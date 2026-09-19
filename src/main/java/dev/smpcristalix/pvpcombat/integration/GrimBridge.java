package dev.smpcristalix.pvpcombat.integration;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.event.ListenerPriority;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.plugin.GrimPlugin;
import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.service.AbilityService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Необязательная typed-интеграция с актуальным GrimAPI. */
public final class GrimBridge implements IntegrationBridge {
    private final PvPCombatPlugin plugin;
    private final AbilityService abilities;
    private EventBus eventBus;
    private GrimPlugin grimPlugin;
    private boolean registered;

    public GrimBridge(PvPCombatPlugin plugin, AbilityService abilities) {
        this.plugin = plugin;
        this.abilities = abilities;
    }

    @Override
    public boolean registerIfAvailable() {
        unregister();
        if (!plugin.getSettings().grimIntegrationEnabled()) return false;

        Plugin grim = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (grim == null || !grim.isEnabled()) return false;

        try {
            GrimAbstractAPI api = GrimAPIProvider.get();
            eventBus = api.getEventBus();
            grimPlugin = api.getGrimPlugin(plugin);
            eventBus.get(FlagEvent.class).onFlagSupplier(
                    grimPlugin,
                    (user, check, verbose, currentlyCancelled) ->
                            currentlyCancelled || abilities.shouldSuppressGrimFlag(
                                    user.getUniqueId(),
                                    check.getCheckName()
                            ),
                    ListenerPriority.LOWEST,
                    true
            );
            registered = true;
            plugin.getLogger().info(
                    "GrimAC bridge enabled through typed GrimAPI: only legal PvPCombat movement gets exemptions."
            );
            return true;
        } catch (IllegalStateException | IllegalArgumentException | LinkageError ex) {
            eventBus = null;
            grimPlugin = null;
            plugin.getLogger().warning("GrimAC найден, но typed bridge не подключён: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public void unregister() {
        if (!registered || eventBus == null || grimPlugin == null) return;
        try {
            eventBus.unregisterAllListeners(grimPlugin);
        } catch (RuntimeException ignored) {
            // Grim может уже находиться в процессе выключения.
        } finally {
            registered = false;
            eventBus = null;
            grimPlugin = null;
        }
    }
}
