package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.api.event.PlayerStatChangeEvent;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.data.PlayerProfile;
import org.bukkit.Bukkit;
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

        int oldValue = value(profile, stat);
        int newValue = oldValue + 1;
        setValue(profile, stat, newValue);
        stats.apply(player);
        Bukkit.getPluginManager().callEvent(new PlayerStatChangeEvent(
                player,
                apiStat(stat),
                oldValue,
                newValue,
                PlayerStatChangeEvent.Reason.UPGRADE
        ));
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

    private int value(PlayerProfile profile, Stat stat) {
        return switch (stat) {
            case DAMAGE -> profile.damageLevel();
            case HEALTH -> profile.healthStep();
            case SPEED -> profile.speedLevel();
            case SATIETY -> profile.satietyLevel();
            case ABILITY -> profile.abilityLevel();
        };
    }

    private void setValue(PlayerProfile profile, Stat stat, int value) {
        switch (stat) {
            case DAMAGE -> profile.damageLevel(value);
            case HEALTH -> profile.healthStep(value);
            case SPEED -> profile.speedLevel(value);
            case SATIETY -> profile.satietyLevel(value);
            case ABILITY -> profile.abilityLevel(value);
        }
    }

    private PlayerStatChangeEvent.Stat apiStat(Stat stat) {
        return PlayerStatChangeEvent.Stat.valueOf(stat.name());
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
