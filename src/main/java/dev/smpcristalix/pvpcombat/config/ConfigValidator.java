package dev.smpcristalix.pvpcombat.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Проверяет взаимосвязанные значения config.yml до применения их к игрокам. */
public final class ConfigValidator {
    private static final double EPSILON = 1.0E-9;

    private ConfigValidator() {}

    public static void validate(FileConfiguration config) {
        List<String> errors = new ArrayList<>();

        positive(errors, config, "combat.duration-seconds");
        nonNegative(errors, config, "combat.shard-reward-cooldown-minutes");
        nonNegative(errors, config, "combat.stat-loss-cooldown-minutes");
        positive(errors, config, "pearls.charges");
        positive(errors, config, "pearls.recharge-seconds");
        positive(errors, config, "stats.damage.levels");
        positive(errors, config, "stats.speed.levels");
        positive(errors, config, "stats.satiety.levels");
        positive(errors, config, "stats.ability.max-level");

        finiteNonNegative(errors, config, "end-crystal.damage-percent");
        finiteNonNegative(errors, config, "stats.damage.max-percent");
        finiteNonNegative(errors, config, "stats.speed.max-percent");
        finiteRange(errors, config, "stats.satiety.max-reduction-percent", 0.0, 100.0);
        finiteRange(errors, config, "abilities.axe.minimum-attack-charge", 0.0, 1.0);
        finiteNonNegative(errors, config, "abilities.sword.damage-per-second-hp");

        validateHealth(errors, config);
        validateAbilityTables(errors, config);
        validateItem(errors, config);

        for (String stat : List.of("ability", "damage", "health", "speed", "satiety")) {
            nonNegative(errors, config, "death-penalty.weights." + stat);
        }
        if (config.getInt("totems.max-carried", 2) < 0) {
            errors.add("totems.max-carried не может быть отрицательным");
        }
        if (config.getInt("totems.loot-min-fight-seconds", 10) < 0) {
            errors.add("totems.loot-min-fight-seconds не может быть отрицательным");
        }
        if (config.getLong("messages.notice-cooldown-ms", 1500L) < 0L) {
            errors.add("messages.notice-cooldown-ms не может быть отрицательным");
        }
        if (config.getLong("messages.notice-priority-ms", 1800L) < 0L) {
            errors.add("messages.notice-priority-ms не может быть отрицательным");
        }
        if (config.getLong("integrations.grim.legal-movement-grace-ms", 750L) < 0L) {
            errors.add("integrations.grim.legal-movement-grace-ms не может быть отрицательным");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Ошибки config.yml:\n - " + String.join("\n - ", errors));
        }
    }

    private static void validateHealth(List<String> errors, FileConfiguration config) {
        double min = config.getDouble("stats.health.min-hearts", 7.0);
        double base = config.getDouble("stats.health.base-hearts", 10.0);
        double max = config.getDouble("stats.health.max-hearts", 13.0);
        double step = config.getDouble("stats.health.hearts-per-step", 0.5);

        if (!finitePositive(min) || !finitePositive(base) || !finitePositive(max)) {
            errors.add("stats.health min/base/max должны быть конечными числами > 0");
            return;
        }
        if (!(min <= base && base <= max)) {
            errors.add("stats.health должно удовлетворять min-hearts <= base-hearts <= max-hearts");
        }
        if (!finitePositive(step)) {
            errors.add("stats.health.hearts-per-step должно быть конечным числом > 0");
            return;
        }

        if (!alignedToStep(base - min, step) || !alignedToStep(max - base, step)) {
            errors.add("stats.health диапазон должен точно делиться на hearts-per-step от base-hearts");
        }
    }

    private static void validateAbilityTables(List<String> errors, FileConfiguration config) {
        int levels = config.getInt("stats.ability.max-level", 9);
        validateList(errors, config, "abilities.sword.chances", levels, 0.0, 100.0, false);
        validateList(errors, config, "abilities.sword.duration-seconds", levels, 0.0, Double.MAX_VALUE, false);
        validateList(errors, config, "abilities.axe.chances", levels, 0.0, 100.0, false);
        validateList(errors, config, "abilities.axe.stun-seconds", levels, 0.0, Double.MAX_VALUE, false);
        validateList(errors, config, "abilities.axe.armor-durability-multiplier", levels, 1.0, Integer.MAX_VALUE, true);
        validateList(errors, config, "abilities.bow.chances", levels, 0.0, 100.0, false);
        validateList(errors, config, "abilities.bow.knockback-bonus-percent", levels, 0.0, Double.MAX_VALUE, false);
        validateList(errors, config, "abilities.bow.slowness-seconds", levels, 0.0, Double.MAX_VALUE, false);
        validateList(errors, config, "abilities.crossbow.chances", levels, 0.0, 100.0, false);
        validateList(errors, config, "abilities.crossbow.armor-durability-multiplier", levels, 1.0, Integer.MAX_VALUE, true);
        validateList(errors, config, "abilities.crossbow.stun-seconds", levels, 0.0, Double.MAX_VALUE, false);
    }

    private static void validateList(List<String> errors, FileConfiguration config, String path,
                                     int minimumSize, double min, double max, boolean wholeNumber) {
        List<?> values = config.getList(path, List.of());
        if (values.size() < minimumSize) {
            errors.add(path + " должен содержать минимум " + minimumSize + " значений");
            return;
        }

        for (int i = 0; i < minimumSize; i++) {
            Object raw = values.get(i);
            if (!(raw instanceof Number number)) {
                errors.add(path + "[" + i + "] должен быть числом");
                continue;
            }
            double value = number.doubleValue();
            if (!Double.isFinite(value) || value < min || value > max) {
                errors.add(path + "[" + i + "] вне допустимого диапазона " + min + ".." + max);
                continue;
            }
            if (wholeNumber && Math.rint(value) != value) {
                errors.add(path + "[" + i + "] должен быть целым множителем");
            }
        }
    }

    private static void validateItem(List<String> errors, FileConfiguration config) {
        String materialName = config.getString("items.shard.material", "AMETHYST_SHARD");
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            errors.add("items.shard.material должен быть существующим предметом Bukkit Material");
        }
    }

    private static void positive(List<String> errors, FileConfiguration config, String path) {
        if (config.getInt(path, 0) <= 0) errors.add(path + " должно быть > 0");
    }

    private static void nonNegative(List<String> errors, FileConfiguration config, String path) {
        if (config.getInt(path, 0) < 0) errors.add(path + " не может быть отрицательным");
    }

    private static void finiteNonNegative(List<String> errors, FileConfiguration config, String path) {
        double value = config.getDouble(path);
        if (!Double.isFinite(value) || value < 0.0) {
            errors.add(path + " должно быть конечным числом >= 0");
        }
    }

    private static void finiteRange(List<String> errors, FileConfiguration config, String path,
                                    double min, double max) {
        double value = config.getDouble(path);
        if (!Double.isFinite(value) || value < min || value > max) {
            errors.add(path + " должно быть в диапазоне " + min + ".." + max);
        }
    }

    private static boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static boolean alignedToStep(double distance, double step) {
        double steps = distance / step;
        return Math.abs(steps - Math.rint(steps)) < EPSILON;
    }
}
