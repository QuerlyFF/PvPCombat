package dev.smpcristalix.pvpcombat.listener;

import dev.smpcristalix.pvpcombat.data.YamlDataStore;
import dev.smpcristalix.pvpcombat.service.CombatService;
import dev.smpcristalix.pvpcombat.service.NoticeService;
import dev.smpcristalix.pvpcombat.service.PearlService;
import dev.smpcristalix.pvpcombat.service.ScoreboardService;
import dev.smpcristalix.pvpcombat.service.ShardService;
import dev.smpcristalix.pvpcombat.service.StatsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Вход применяет статы/отложенные награды; Combat logout превращается в смерть. */
public final class PlayerLifecycleListener implements Listener {
    private final StatsService stats;
    private final CombatService combat;
    private final PearlService pearls;
    private final ScoreboardService scoreboard;
    private final ShardService shards;
    private final YamlDataStore store;
    private final NoticeService notices;

    public PlayerLifecycleListener(StatsService stats, CombatService combat, PearlService pearls,
                                   ScoreboardService scoreboard, ShardService shards,
                                   YamlDataStore store, NoticeService notices) {
        this.stats = stats;
        this.combat = combat;
        this.pearls = pearls;
        this.scoreboard = scoreboard;
        this.shards = shards;
        this.store = store;
        this.notices = notices;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        stats.apply(player);
        int pending = store.removePendingShards(player.getUniqueId());
        if (pending > 0) {
            shards.give(player, pending);
            store.saveAsync();
            player.sendMessage("§dПолучены отложенные PvP-награды: " + pending + " Осколок(ов).");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        boolean combatLogout = combat.inCombat(player) && !player.isDead();

        if (combatLogout) {
            combat.markCombatLogout(player);
            player.setHealth(0.0);
        } else {
            combat.clear(player);
        }

        scoreboard.forget(player);
        pearls.reset(player);
        notices.clear(player);
    }
}
