package dev.smpcristalix.pvpcombat.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Единая типизированная точка доступа ко всем настройкам PvPCombat.
 *
 * <p>Игровые сервисы не читают YAML напрямую. Это убирает магические строки из
 * бизнес-логики и позволяет безопасно менять структуру config.yml.</p>
 */
public final class PvPCombatSettings {
    private final FileConfiguration config;

    public PvPCombatSettings(FileConfiguration config) {
        this.config = config;
    }

    public int combatDurationSeconds() { return Math.max(1, config.getInt("combat.duration-seconds", 30)); }
    public int shardRewardCooldownMinutes() { return Math.max(0, config.getInt("combat.shard-reward-cooldown-minutes", 30)); }
    public int statLossCooldownMinutes() { return Math.max(0, config.getInt("combat.stat-loss-cooldown-minutes", 30)); }
    public int pearlCharges() { return Math.max(1, config.getInt("pearls.charges", 3)); }
    public int pearlRechargeSeconds() { return Math.max(1, config.getInt("pearls.recharge-seconds", 10)); }
    public boolean allowElytraInCombat() { return config.getBoolean("movement.allow-elytra-in-combat", false); }
    public boolean allowFireworksInCombat() { return config.getBoolean("movement.allow-fireworks-in-combat", false); }
    public int maxTotemsCarried() { return Math.max(0, config.getInt("totems.max-carried", 2)); }
    public boolean allowExternalTotemSourcesInCombat() { return config.getBoolean("totems.allow-external-sources-in-combat", false); }
    public int totemLootMinFightSeconds() { return Math.max(0, config.getInt("totems.loot-min-fight-seconds", 10)); }
    public long totemNoticeCooldownMillis() { return Math.max(0L, config.getLong("totems.notice-cooldown-ms", 1500L)); }
    public double endCrystalDamageMultiplier() { return Math.max(0.0, config.getDouble("end-crystal.damage-percent", 85.0)) / 100.0; }
    public double maxDamagePercent() { return Math.max(0.0, config.getDouble("stats.damage.max-percent", 25.0)); }
    public int damageLevels() { return Math.max(1, config.getInt("stats.damage.levels", 7)); }
    public boolean damagePvpOnly() { return config.getBoolean("stats.damage.pvp-only", true); }
    public double minHealthHearts() { return config.getDouble("stats.health.min-hearts", 7.0); }
    public double baseHealthHearts() { return config.getDouble("stats.health.base-hearts", 10.0); }
    public double maxHealthHearts() { return config.getDouble("stats.health.max-hearts", 13.0); }
    public double healthHeartsPerStep() { return Math.max(0.01, config.getDouble("stats.health.hearts-per-step", 0.5)); }
    public double maxSpeedPercent() { return Math.max(0.0, config.getDouble("stats.speed.max-percent", 25.0)); }
    public int speedLevels() { return Math.max(1, config.getInt("stats.speed.levels", 7)); }
    public double maxSatietyReductionPercent() { return Math.max(0.0, Math.min(100.0, config.getDouble("stats.satiety.max-reduction-percent", 70.0))); }
    public int satietyLevels() { return Math.max(1, config.getInt("stats.satiety.levels", 7)); }
    public int maxAbilityLevel() { return Math.max(1, config.getInt("stats.ability.max-level", 9)); }
    public int deathWeight(String stat) { return Math.max(0, config.getInt("death-penalty.weights." + stat, 0)); }
    public double abilityChance(String weapon, int level) { return listDouble("abilities." + weapon + ".chances", level, 0.0); }
    public double abilityDuration(String weapon, String key, int level) { return listDouble("abilities." + weapon + "." + key, level, 0.0); }
    public int abilityInt(String weapon, String key, int level, int fallback) { return (int) Math.round(listDouble("abilities." + weapon + "." + key, level, fallback)); }
    public double swordBleedDamageHp() { return Math.max(0.0, config.getDouble("abilities.sword.damage-per-second-hp", 1.0)); }
    public double axeMinimumCharge() { return Math.max(0.0, Math.min(1.0, config.getDouble("abilities.axe.minimum-attack-charge", 0.75))); }
    public String shardMaterial() { return config.getString("items.shard.material", "AMETHYST_SHARD"); }
    public String shardName() { return config.getString("items.shard.name", "&d&lОсколок"); }
    public List<String> shardLore() { return config.getStringList("items.shard.lore"); }
    public boolean scoreboardEnabled() { return config.getBoolean("scoreboard.enabled", true); }
    public boolean scoreboardActionBarFallback() { return config.getBoolean("scoreboard.actionbar-fallback-when-sidebar-busy", true); }
    public String scoreboardTitle() { return config.getString("scoreboard.title", "&c&lPvPCombat"); }
    public String scoreboardCombatLine() { return config.getString("scoreboard.combat-line", "&f⚔ PvP: &c{seconds} сек."); }
    public String scoreboardPearlReadyLine() { return config.getString("scoreboard.pearl-ready-line", "&f◉ Жемчуг: &b{remaining}/{max}"); }
    public String scoreboardPearlCooldownLine() { return config.getString("scoreboard.pearl-cooldown-line", "&f◉ Жемчуг: &eКД {seconds} сек."); }

    private double listDouble(String path, int oneBasedLevel, double fallback) {
        var values = config.getDoubleList(path);
        int index = oneBasedLevel - 1;
        return index >= 0 && index < values.size() ? values.get(index) : fallback;
    }
}
