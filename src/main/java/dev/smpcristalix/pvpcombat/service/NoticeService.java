package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Единая защита от спама короткими системными уведомлениями PvPCombat.
 *
 * <p>Важные сообщения идут через ActionBar и временно имеют приоритет над
 * Combat-fallback, чтобы разные подсистемы не перетирали друг друга каждый тик.</p>
 */
public final class NoticeService {
    private final Map<UUID, Map<String, Long>> lastNoticeAt = new HashMap<>();
    private final Map<UUID, Long> priorityUntil = new HashMap<>();
    private PvPCombatSettings settings;

    public NoticeService(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public void warn(Player player, String channel, String message) {
        long now = System.currentTimeMillis();
        Map<String, Long> channels = lastNoticeAt.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new HashMap<>()
        );
        long previous = channels.getOrDefault(channel, 0L);
        if (now - previous < settings.noticeCooldownMillis()) return;

        channels.put(channel, now);
        priorityUntil.put(
                player.getUniqueId(),
                now + settings.noticePriorityMillis()
        );
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    public boolean hasPriorityNotice(Player player) {
        long until = priorityUntil.getOrDefault(player.getUniqueId(), 0L);
        if (until > System.currentTimeMillis()) return true;
        priorityUntil.remove(player.getUniqueId());
        return false;
    }

    public void clear(Player player) {
        UUID id = player.getUniqueId();
        lastNoticeAt.remove(id);
        priorityUntil.remove(id);
    }
}
