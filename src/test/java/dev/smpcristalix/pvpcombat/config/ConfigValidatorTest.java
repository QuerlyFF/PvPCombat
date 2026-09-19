package dev.smpcristalix.pvpcombat.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigValidatorTest {
    @Test
    void acceptsCurrentProjectBalance() {
        assertDoesNotThrow(() -> ConfigValidator.validate(validConfig()));
    }

    @Test
    void rejectsInvertedHealthRange() {
        YamlConfiguration config = validConfig();
        config.set("stats.health.min-hearts", 14.0);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    @Test
    void rejectsHealthRangeThatDoesNotAlignToStep() {
        YamlConfiguration config = validConfig();
        config.set("stats.health.hearts-per-step", 0.7);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    @Test
    void rejectsAbilityTablesShorterThanConfiguredLevelCount() {
        YamlConfiguration config = validConfig();
        config.set("abilities.crossbow.chances", List.of(10, 20));
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    @Test
    void rejectsChanceAboveOneHundredPercent() {
        YamlConfiguration config = validConfig();
        config.set("abilities.sword.chances", List.of(3, 5, 7, 9, 12, 15, 18, 20, 101));
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    @Test
    void rejectsInvalidShardMaterial() {
        YamlConfiguration config = validConfig();
        config.set("items.shard.material", "THIS_ITEM_DOES_NOT_EXIST");
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    @Test
    void rejectsNegativeCooldownsAndGraceWindows() {
        YamlConfiguration config = validConfig();
        config.set("combat.shard-reward-cooldown-minutes", -1);
        config.set("integrations.grim.legal-movement-grace-ms", -10L);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    @Test
    void rejectsNegativeDeathPenaltyWeight() {
        YamlConfiguration config = validConfig();
        config.set("death-penalty.weights.health", -1);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validate(config));
    }

    private YamlConfiguration validConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat.duration-seconds", 30);
        config.set("combat.shard-reward-cooldown-minutes", 30);
        config.set("combat.stat-loss-cooldown-minutes", 30);
        config.set("pearls.charges", 3);
        config.set("pearls.recharge-seconds", 10);
        config.set("end-crystal.damage-percent", 85.0);
        config.set("stats.damage.max-percent", 25.0);
        config.set("stats.damage.levels", 7);
        config.set("stats.speed.max-percent", 25.0);
        config.set("stats.speed.levels", 7);
        config.set("stats.satiety.max-reduction-percent", 70.0);
        config.set("stats.satiety.levels", 7);
        config.set("stats.ability.max-level", 9);
        config.set("stats.health.min-hearts", 7.0);
        config.set("stats.health.base-hearts", 10.0);
        config.set("stats.health.max-hearts", 13.0);
        config.set("stats.health.hearts-per-step", 0.5);
        config.set("totems.max-carried", 2);
        config.set("totems.loot-min-fight-seconds", 10);
        config.set("messages.notice-cooldown-ms", 1500L);
        config.set("messages.notice-priority-ms", 1800L);
        config.set("integrations.grim.legal-movement-grace-ms", 750L);
        config.set("items.shard.material", "AMETHYST_SHARD");
        config.set("abilities.axe.minimum-attack-charge", 0.75);
        config.set("abilities.sword.damage-per-second-hp", 1.0);
        config.set("death-penalty.weights.ability", 40);
        config.set("death-penalty.weights.damage", 30);
        config.set("death-penalty.weights.health", 15);
        config.set("death-penalty.weights.speed", 10);
        config.set("death-penalty.weights.satiety", 5);

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
