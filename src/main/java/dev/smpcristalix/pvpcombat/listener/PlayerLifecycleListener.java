package dev.smpcristalix.pvpcombat.listener;

import dev.smpcristalix.pvpcombat.service.CombatService;
import dev.smpcristalix.pvpcombat.service.PearlService;
import dev.smpcristalix.pvpcombat.service.ScoreboardService;
import dev.smpcristalix.pvpcombat.service.StatsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Вход применяет статы; combat-logout превращается в смерть с сохранением PvP-атрибуции. */
public final class PlayerLifecycleListener implements Listener {
    private final StatsService stats;
    private final CombatService combat;
    private final PearlService pearls;
    private final ScoreboardService scoreboard;

    public PlayerLifecycleListener(StatsService stats, CombatService combat, PearlService pearls,
                                   ScoreboardService scoreboard) {
        this.stats = stats;
        this.combat = combat;
        this.pearls = pearls;
        this.scoreboard = scoreboard;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        stats.apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (combat.inCombat(player) && !player.isDead()) {
            combat.markCombatLogout(player);
            player.setHealth(0.0);
        }
        scoreboard.forget(player);
        combat.clear(player);
        pearls.reset(player);
    }
}
