package dev.smpcristalix.pvpcombat.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Проверяет взаимосвязанные значения config.yml до применения их к игрокам. */
public final class ConfigValidator {
    private ConfigValidator() {}

    public static void validate(FileConfiguration config) {
        List<String> errors = new ArrayList<>();

        positive(errors, config, "combat.duration-seconds");
        positive(errors, config, "pearls.charges");
        positive(errors, config, "pearls.recharge-seconds");
        positive(errors, config, "stats.damage.levels");
        positive(errors, config, "stats.speed.levels");
        positive(errors, config, "stats.satiety.levels");
        positive(errors, config, "stats.ability.max-level");

        double min = config.getDouble("stats.health.min-hearts", 7.0);
        double base = config.getDouble("stats.health.base-hearts", 10.0);
        double max = config.getDouble("stats.health.max-hearts", 13.0);
        double step = config.getDouble("stats.health.hearts-per-step", 0.5);
        if (!(min <= base && base <= max)) {
            errors.add("stats.health должно удовлетворять min-hearts <= base-hearts <= max-hearts");
        }
        if (step <= 0.0 || !Double.isFinite(step)) {
            errors.add("stats.health.hearts-per-step должно быть конечным числом > 0");
        }

        int abilityLevels = config.getInt("stats.ability.max-level", 9);
        requireList(errors, config, "abilities.sword.chances", abilityLevels);
        requireList(errors, config, "abilities.sword.duration-seconds", abilityLevels);
        requireList(errors, config, "abilities.axe.chances", abilityLevels);
        requireList(errors, config, "abilities.axe.stun-seconds", abilityLevels);
        requireList(errors, config, "abilities.axe.armor-durability-multiplier", abilityLevels);
        requireList(errors, config, "abilities.bow.chances", abilityLevels);
        requireList(errors, config, "abilities.bow.knockback-bonus-percent", abilityLevels);
        requireList(errors, config, "abilities.bow.slowness-seconds", abilityLevels);
        requireList(errors, config, "abilities.crossbow.chances", abilityLevels);
        requireList(errors, config, "abilities.crossbow.armor-durability-multiplier", abilityLevels);
        requireList(errors, config, "abilities.crossbow.stun-seconds", abilityLevels);

        if (config.getInt("totems.max-carried", 2) < 0) {
            errors.add("totems.max-carried не может быть отрицательным");
        }
        if (config.getLong("messages.notice-cooldown-ms", 1500L) < 0L) {
            errors.add("messages.notice-cooldown-ms не может быть отрицательным");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Ошибки config.yml:\n - " + String.join("\n - ", errors));
        }
    }

    private static void positive(List<String> errors, FileConfiguration config, String path) {
        if (config.getInt(path, 0) <= 0) errors.add(path + " должно быть > 0");
    }

    private static void requireList(List<String> errors, FileConfiguration config, String path, int minimumSize) {
        if (config.getList(path, List.of()).size() < minimumSize) {
            errors.add(path + " должен содержать минимум " + minimumSize + " значений");
        }
    }
}
