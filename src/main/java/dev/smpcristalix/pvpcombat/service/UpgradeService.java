package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.data.PlayerProfile;
import org.bukkit.entity.Player;

/** Правила прокачки характеристик, полностью независимые от GUI. */
public final class UpgradeService {
    private final StatsService stats;
    private final ShardService shards;
    private PvPCombatSettings settings;

    public UpgradeService(StatsService stats, ShardService shards, PvPCombatSettings settings) {
        this.stats = stats;
        this.shards = shards;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public Result upgrade(Player player, Stat stat) {
        PlayerProfile profile = stats.profile(player.getUniqueId());
        stats.clamp(profile);

        if (!canUpgrade(profile, stat)) {
            return new Result(false, "Характеристика уже на максимуме.");
        }
        if (!shards.consumeOne(player)) {
            return new Result(false, "У тебя нет Осколка.");
        }

        switch (stat) {
            case DAMAGE -> profile.damageLevel(profile.damageLevel() + 1);
            case HEALTH -> profile.healthStep(profile.healthStep() + 1);
            case SPEED -> profile.speedLevel(profile.speedLevel() + 1);
            case SATIETY -> profile.satietyLevel(profile.satietyLevel() + 1);
            case ABILITY -> profile.abilityLevel(profile.abilityLevel() + 1);
        }

        stats.apply(player);
        return new Result(true, "Характеристика улучшена.");
    }

    private boolean canUpgrade(PlayerProfile profile, Stat stat) {
        return switch (stat) {
            case DAMAGE -> profile.damageLevel() < settings.damageLevels();
            case HEALTH -> profile.healthStep() < stats.maxHealthStep();
            case SPEED -> profile.speedLevel() < settings.speedLevels();
            case SATIETY -> profile.satietyLevel() < settings.satietyLevels();
            case ABILITY -> profile.abilityLevel() < settings.maxAbilityLevel();
        };
    }

    public enum Stat {
        DAMAGE,
        HEALTH,
        SPEED,
        SATIETY,
        ABILITY
    }

    public record Result(boolean success, String message) {}
}
