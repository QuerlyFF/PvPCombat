package dev.smpcristalix.pvpcombat.api;

import dev.smpcristalix.pvpcombat.service.CombatService;
import dev.smpcristalix.pvpcombat.service.ShardService;
import dev.smpcristalix.pvpcombat.service.StatsService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Внутренняя реализация публичного API; наружу сервисы плагина не раскрываются. */
public final class PvPCombatApiImpl implements PvPCombatApi {
    private final ShardService shards;
    private final CombatService combat;
    private final StatsService stats;

    public PvPCombatApiImpl(ShardService shards, CombatService combat, StatsService stats) {
        this.shards = shards;
        this.combat = combat;
        this.stats = stats;
    }

    @Override
    public void giveShards(Player player, int amount) {
        shards.give(player, amount);
    }

    @Override
    public int countShards(Player player) {
        return shards.count(player);
    }

    @Override
    public boolean consumeShards(Player player, int amount) {
        return shards.consume(player, amount);
    }

    @Override
    public ItemStack createShard(int amount) {
        return shards.create(amount);
    }

    @Override
    public boolean isShard(ItemStack item) {
        return shards.isShard(item);
    }

    @Override
    public boolean isInCombat(Player player) {
        return combat.inCombat(player);
    }

    @Override
    public PlayerStatsSnapshot getStats(UUID playerId) {
        var profile = stats.profile(playerId);
        return new PlayerStatsSnapshot(
                profile.damageLevel(),
                stats.healthHearts(profile),
                profile.speedLevel(),
                profile.satietyLevel(),
                profile.abilityLevel()
        );
    }
}
