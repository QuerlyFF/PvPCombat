package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.data.YamlDataStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Anti-farm хранится для направленной пары killer -> victim. */
public final class RewardService {
    private static final String KEY_SEPARATOR = "__";

    private final YamlDataStore store;
    private final ShardService shards;
    private PvPCombatSettings settings;

    public RewardService(YamlDataStore store, ShardService shards, PvPCombatSettings settings) {
        this.store = store;
        this.shards = shards;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public boolean reward(Player killer, Player victim) {
        return reward(killer.getUniqueId(), victim);
    }

    public boolean reward(UUID killerId, Player victim) {
        String key = migrationKey(killerId.toString(), victim.getUniqueId().toString());
        long now = System.currentTimeMillis();
        long cooldown = settings.shardRewardCooldownMinutes() * 60_000L;
        if (now - store.rewardCooldown(key) < cooldown) return false;

        store.rewardCooldown(key, now);
        Player onlineKiller = Bukkit.getPlayer(killerId);
        if (onlineKiller != null) shards.give(onlineKiller, 1);
        else store.addPendingShards(killerId, 1);
        return true;
    }

    public void cleanupExpired() {
        long cooldown = settings.shardRewardCooldownMinutes() * 60_000L;
        store.purgeRewardCooldownsBefore(System.currentTimeMillis() - cooldown);
    }

    public static String migrationKey(String killerUuid, String victimUuid) {
        return killerUuid + KEY_SEPARATOR + victimUuid;
    }
}
