package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.data.PlayerProfile;
import dev.smpcristalix.pvpcombat.data.YamlDataStore;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Преобразует дискретные ступени игрока в реальные игровые характеристики.
 *
 * <p>Здоровье и скорость применяются собственными keyed-модификаторами. PvPCombat
 * не перезаписывает base value атрибута, поэтому корректно сосуществует с другими
 * плагинами, предметами и эффектами, которые тоже модифицируют эти характеристики.</p>
 */
public final class StatsService {
    private static final double VANILLA_HEALTH_HP = 20.0;

    private final YamlDataStore store;
    private final NamespacedKey healthModifierKey;
    private final NamespacedKey speedModifierKey;
    private PvPCombatSettings settings;

    public StatsService(JavaPlugin plugin, YamlDataStore store, PvPCombatSettings settings) {
        this.store = store;
        this.settings = settings;
        this.healthModifierKey = new NamespacedKey(plugin, "health_progression");
        this.speedModifierKey = new NamespacedKey(plugin, "speed_progression");
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public PlayerProfile profile(UUID uuid) {
        return store.profile(uuid);
    }

    public void clamp(PlayerProfile profile) {
        profile.damageLevel(clamp(profile.damageLevel(), 0, settings.damageLevels()));
        profile.speedLevel(clamp(profile.speedLevel(), 0, settings.speedLevels()));
        profile.satietyLevel(clamp(profile.satietyLevel(), 0, settings.satietyLevels()));
        profile.abilityLevel(clamp(profile.abilityLevel(), 0, settings.maxAbilityLevel()));
        profile.healthStep(clamp(profile.healthStep(), minHealthStep(), maxHealthStep()));
    }

    public void apply(Player player) {
        PlayerProfile profile = profile(player.getUniqueId());
        clamp(profile);
        applyHealth(player, profile);
        applySpeed(player, profile);
    }

    private void applyHealth(Player player, PlayerProfile profile) {
        AttributeInstance health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health == null) return;

        removeOurModifier(health, healthModifierKey);
        double desiredHp = healthHearts(profile) * 2.0;
        double deltaFromVanilla = desiredHp - VANILLA_HEALTH_HP;
        if (Math.abs(deltaFromVanilla) > 1.0E-9) {
            health.addModifier(new AttributeModifier(
                    healthModifierKey,
                    deltaFromVanilla,
                    AttributeModifier.Operation.ADD_NUMBER
            ));
        }

        if (player.getHealth() > health.getValue()) {
            player.setHealth(Math.max(0.0, health.getValue()));
        }
    }

    private void applySpeed(Player player, PlayerProfile profile) {
        AttributeInstance speed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed == null) return;

        removeOurModifier(speed, speedModifierKey);
        double bonus = speedBonusPercent(profile) / 100.0;
        if (Math.abs(bonus) > 1.0E-9) {
            speed.addModifier(new AttributeModifier(
                    speedModifierKey,
                    bonus,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
            ));
        }
    }

    private void removeOurModifier(AttributeInstance attribute, NamespacedKey key) {
        attribute.getModifiers().stream()
                .filter(modifier -> modifier.getKey().equals(key))
                .findFirst()
                .ifPresent(attribute::removeModifier);
    }

    public double damageBonusPercent(PlayerProfile profile) {
        return settings.maxDamagePercent() * profile.damageLevel() / settings.damageLevels();
    }

    public double speedBonusPercent(PlayerProfile profile) {
        return settings.maxSpeedPercent() * profile.speedLevel() / settings.speedLevels();
    }

    public double satietyReductionPercent(PlayerProfile profile) {
        return settings.maxSatietyReductionPercent() * profile.satietyLevel() / settings.satietyLevels();
    }

    public double healthHearts(PlayerProfile profile) {
        return settings.baseHealthHearts() + profile.healthStep() * settings.healthHeartsPerStep();
    }

    public int minHealthStep() {
        return (int) Math.round(
                (settings.minHealthHearts() - settings.baseHealthHearts()) / settings.healthHeartsPerStep()
        );
    }

    public int maxHealthStep() {
        return (int) Math.round(
                (settings.maxHealthHearts() - settings.baseHealthHearts()) / settings.healthHeartsPerStep()
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
