package dev.smpcristalix.pvpcombat.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigValidatorTest {
    @Test
    void acceptsCurrentProjectBalance() {
        YamlConfiguration config = validConfig();
        assertDoesNotThrow(() -> ConfigValidator.validate(config));
    }

    @Test
    void rejectsInvertedHealthRange() {
        YamlConfiguration config = validConfig();
        config.set("stats.health.min-hearts", 14.0);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    @Test
    void rejectsAbilityTablesShorterThanConfiguredLevelCount() {
        YamlConfiguration config = validConfig();
        config.set("abilities.crossbow.chances", List.of(10, 20));
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    private YamlConfiguration validConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat.duration-seconds", 30);
        config.set("pearls.charges", 3);
        config.set("pearls.recharge-seconds", 10);
        config.set("stats.damage.levels", 7);
        config.set("stats.speed.levels", 7);
        config.set("stats.satiety.levels", 7);
        config.set("stats.ability.max-level", 9);
        config.set("stats.health.min-hearts", 7.0);
        config.set("stats.health.base-hearts", 10.0);
        config.set("stats.health.max-hearts", 13.0);
        config.set("stats.health.hearts-per-step", 0.5);
        config.set("totems.max-carried", 2);
        config.set("messages.notice-cooldown-ms", 1500L);

        List<Integer> nine = List.of(1, 1, 1, 1, 1, 1, 1, 1, 1);
        config.set("abilities.sword.chances", nine);
        config.set("abilities.sword.duration-seconds", nine);
        config.set("abilities.axe.chances", nine);
        config.set("abilities.axe.stun-seconds", nine);
        config.set("abilities.axe.armor-durability-multiplier", nine);
        config.set("abilities.bow.chances", nine);
        config.set("abilities.bow.knockback-bonus-percent", nine);
        config.set("abilities.bow.slowness-seconds", nine);
        config.set("abilities.crossbow.chances", nine);
        config.set("abilities.crossbow.armor-durability-multiplier", nine);
        config.set("abilities.crossbow.stun-seconds", nine);
        return config;
    }
}
