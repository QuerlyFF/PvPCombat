package dev.smpcristalix.pvpcombat.integration;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.service.AbilityService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/**
 * Необязательный bridge с GrimAC без жёсткой compile-time зависимости.
 *
 * <p>Мы не выключаем проверки Grim глобально. На короткое время подавляются только
 * Simulation/AntiKB-флаги, которые совпали с заведомо легальным custom movement
 * PvPCombat: станом или нашим серверным velocity.</p>
 */
public final class GrimBridge {
    private static final String FLAG_EVENT_CLASS = "ac.grim.grimac.api.events.FlagEvent";

    private final PvPCombatPlugin plugin;
    private final AbilityService abilities;
    private Listener listener;

    public GrimBridge(PvPCombatPlugin plugin, AbilityService abilities) {
        this.plugin = plugin;
        this.abilities = abilities;
    }

    public boolean registerIfAvailable() {
        if (!plugin.getSettings().grimIntegrationEnabled()) return false;

        Plugin grim = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (grim == null || !grim.isEnabled()) return false;

        try {
            Class<?> rawClass = Class.forName(
                    FLAG_EVENT_CLASS,
                    false,
                    grim.getClass().getClassLoader()
            );
            if (!Event.class.isAssignableFrom(rawClass)) return false;

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            listener = new Listener() {};
            EventExecutor executor = (ignored, event) -> handleFlag(event);
            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    EventPriority.LOWEST,
                    executor,
                    plugin,
                    true
            );
            plugin.getLogger().info("GrimAC bridge enabled: legal PvPCombat movement has targeted exemptions.");
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("GrimAC найден, но bridge не подключён: " + ex.getMessage());
            return false;
        }
    }

    private void handleFlag(Event event) {
        if (!(event instanceof Cancellable cancellable)) return;

        Object user = invoke(event, "getUser");
        Object check = invoke(event, "getCheck");
        UUID playerId = resolveUuid(user);
        String checkName = resolveCheckName(check);
        if (playerId == null || checkName == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        if (abilities.shouldSuppressGrimFlag(player, checkName)) {
            cancellable.setCancelled(true);
        }
    }

    private UUID resolveUuid(Object user) {
        if (user == null) return null;
        for (String methodName : new String[]{"getUniqueId", "getUuid", "getUUID"}) {
            Object value = invoke(user, methodName);
            if (value instanceof UUID uuid) return uuid;
        }

        for (String fieldName : new String[]{"uuid", "uniqueId"}) {
            try {
                Field field = user.getClass().getField(fieldName);
                Object value = field.get(user);
                if (value instanceof UUID uuid) return uuid;
            } catch (ReflectiveOperationException ignored) {
                // Пробуем следующий совместимый вариант API.
            }
        }
        return null;
    }

    private String resolveCheckName(Object check) {
        if (check == null) return null;
        for (String methodName : new String[]{"getCheckName", "getConfigName", "getName"}) {
            Object value = invoke(check, methodName);
            if (value instanceof String text && !text.isBlank()) {
                return text.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
