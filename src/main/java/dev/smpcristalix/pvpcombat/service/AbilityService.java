package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Реализует weapon abilities из характеристики «Умения».
 *
 * <p>Listener отвечает только за определение типа попадания. Вся математика шансов,
 * стана, кровотечения, knockback и усиленного износа брони находится здесь.</p>
 */
public final class AbilityService {
    private static final long ARMOR_MULTIPLIER_WINDOW_MS = 250L;

    private final PvPCombatPlugin plugin;
    private final StatsService stats;
    private final Map<UUID, Long> stunnedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> bleeds = new ConcurrentHashMap<>();
    private final Map<UUID, PendingArmorMultiplier> pendingArmorMultipliers = new ConcurrentHashMap<>();
    private PvPCombatSettings settings;

    public AbilityService(PvPCombatPlugin plugin, StatsService stats, PvPCombatSettings settings) {
        this.plugin = plugin;
        this.stats = stats;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public boolean isStunned(Player player) {
        return stunnedUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public void trySword(Player attacker, Player victim) {
        int level = stats.profile(attacker.getUniqueId()).abilityLevel();
        if (level <= 0 || !roll(settings.abilityChance("sword", level))) return;
        int seconds = Math.max(1, (int) Math.round(settings.abilityDuration("sword", "duration-seconds", level)));
        startBleed(attacker, victim, seconds);
    }

    public void tryAxe(Player attacker, Player victim) {
        int level = stats.profile(attacker.getUniqueId()).abilityLevel();
        if (level <= 0) return;
        if (attacker.getAttackCooldown() < settings.axeMinimumCharge()) return;
        if (!roll(settings.abilityChance("axe", level))) return;

        stun(victim, settings.abilityDuration("axe", "stun-seconds", level));
        prepareArmorMultiplier(victim, settings.abilityInt("axe", "armor-durability-multiplier", level, 1));
    }

    public void tryBow(Player attacker, Player victim) {
        int level = stats.profile(attacker.getUniqueId()).abilityLevel();
        if (level <= 0 || !roll(settings.abilityChance("bow", level))) return;

        int knockbackBonus = settings.abilityInt("bow", "knockback-bonus-percent", level, 0);
        int slownessTicks = Math.max(1,
                (int) Math.round(settings.abilityDuration("bow", "slowness-seconds", level) * 20.0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, 0, false, true, true));

        // Vanilla knockback окончательно выставляет velocity после damage-event.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline() || victim.isDead()) return;
            victim.setVelocity(victim.getVelocity().multiply(1.0 + knockbackBonus / 100.0));
        });
    }

    public void tryCrossbow(Player attacker, Player victim) {
        int level = stats.profile(attacker.getUniqueId()).abilityLevel();
        if (level <= 0 || !roll(settings.abilityChance("crossbow", level))) return;

        stun(victim, settings.abilityDuration("crossbow", "stun-seconds", level));
        prepareArmorMultiplier(victim, settings.abilityInt("crossbow", "armor-durability-multiplier", level, 1));
    }

    /**
     * Умножает уже рассчитанный vanilla-износ брони. Так ×3/×5/×7 действительно
     * являются множителями базового damage, а не просто +2/+4/+6 durability.
     */
    public int multiplyArmorDurability(Player player, int vanillaDamage) {
        PendingArmorMultiplier pending = pendingArmorMultipliers.get(player.getUniqueId());
        if (pending == null) return vanillaDamage;
        if (pending.expiresAtMillis() < System.currentTimeMillis()) {
            pendingArmorMultipliers.remove(player.getUniqueId(), pending);
            return vanillaDamage;
        }
        return Math.max(0, vanillaDamage * pending.multiplier());
    }

    private void startBleed(Player attacker, Player victim, int seconds) {
        BukkitRunnable previous = bleeds.remove(victim.getUniqueId());
        if (previous != null) previous.cancel();

        BukkitRunnable task = new BukkitRunnable() {
            private int ticksLeft = seconds;

            @Override
            public void run() {
                if (!victim.isOnline() || victim.isDead() || ticksLeft-- <= 0) {
                    cancelBleed(victim, this);
                    return;
                }

                double damage = settings.swordBleedDamageHp();
                if (damage <= 0.0) return;

                // Прямое уменьшение HP игнорирует броню. На смертельном тике
                // damage(source) сохраняет корректную атрибуцию убийцы.
                if (victim.getHealth() <= damage && attacker.isOnline()) {
                    victim.damage(Math.max(1000.0, victim.getMaxHealth() * 100.0), attacker);
                } else {
                    victim.setHealth(Math.max(0.0, victim.getHealth() - damage));
                }
            }
        };

        bleeds.put(victim.getUniqueId(), task);
        task.runTaskTimer(plugin, 20L, 20L);
    }

    private void cancelBleed(Player victim, BukkitRunnable task) {
        task.cancel();
        bleeds.remove(victim.getUniqueId(), task);
    }

    private void stun(Player victim, double seconds) {
        long until = System.currentTimeMillis() + Math.max(0L, (long) (seconds * 1000.0));
        stunnedUntil.merge(victim.getUniqueId(), until, Math::max);
        victim.setVelocity(victim.getVelocity().zero());
    }

    private void prepareArmorMultiplier(Player victim, int multiplier) {
        if (multiplier <= 1) return;
        long expiry = System.currentTimeMillis() + ARMOR_MULTIPLIER_WINDOW_MS;
        pendingArmorMultipliers.merge(
                victim.getUniqueId(),
                new PendingArmorMultiplier(multiplier, expiry),
                (oldValue, newValue) -> new PendingArmorMultiplier(
                        Math.max(oldValue.multiplier(), newValue.multiplier()),
                        Math.max(oldValue.expiresAtMillis(), newValue.expiresAtMillis())
                )
        );
    }

    private boolean roll(double percent) {
        return ThreadLocalRandom.current().nextDouble(100.0) < percent;
    }

    private record PendingArmorMultiplier(int multiplier, long expiresAtMillis) {}
}
