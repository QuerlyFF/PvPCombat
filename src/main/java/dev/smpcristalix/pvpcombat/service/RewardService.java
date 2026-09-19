package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.data.YamlDataStore;
import org.bukkit.entity.Player;

/** Антифарм хранится для направленной пары killer -> victim. */
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
        String key = key(killer, victim);
        long now = System.currentTimeMillis();
        long cooldown = settings.shardRewardCooldownMinutes() * 60_000L;
        if (now - store.rewardCooldown(key) < cooldown) return false;

        store.rewardCooldown(key, now);
        shards.give(killer, 1);
        return true;
    }

    public static String migrationKey(String killerUuid, String victimUuid) {
        return killerUuid + KEY_SEPARATOR + victimUuid;
    }

    private String key(Player killer, Player victim) {
        return migrationKey(killer.getUniqueId().toString(), victim.getUniqueId().toString());
    }
}
