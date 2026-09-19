package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Реализует weapon abilities и их краткоживущий runtime-state. */
public final class AbilityService {
    private final PvPCombatPlugin plugin;
    private final StatsService stats;
    private final CombatService combat;
    private final Map<UUID, Long> stunnedUntil = new HashMap<>();
    private final Map<UUID, BukkitRunnable> bleeds = new HashMap<>();
    private final Map<UUID, PendingArmorMultiplier> pendingArmorMultipliers = new HashMap<>();
    private final Map<UUID, Long> grimGraceUntil = new HashMap<>();
    private final Set<UUID> internalDamageVictims = new HashSet<>();
    private PvPCombatSettings settings;

    public AbilityService(PvPCombatPlugin plugin, StatsService stats, CombatService combat,
                          PvPCombatSettings settings) {
        this.plugin = plugin;
        this.stats = stats;
        this.combat = combat;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public boolean isStunned(Player player) {
        UUID id = player.getUniqueId();
        long until = stunnedUntil.getOrDefault(id, 0L);
        if (until > System.currentTimeMillis()) return true;
        stunnedUntil.remove(id);
        return false;
    }

    public boolean isInternalDamage(Player victim) {
        return internalDamageVictims.contains(victim.getUniqueId());
    }

    public boolean shouldSuppressGrimFlag(Player player, String rawCheckName) {
        UUID id = player.getUniqueId();
        long until = grimGraceUntil.getOrDefault(id, 0L);
        if (until <= System.currentTimeMillis()) {
            grimGraceUntil.remove(id);
            return false;
        }

        String check = rawCheckName.toLowerCase(Locale.ROOT).replace("_", "");
        return check.equals("simulation")
                || check.equals("antikb")
                || check.equals("antiknockback")
                || check.equals("knockback");
    }

    public void trySword(Player attacker, Player victim) {
        int level = stats.profile(attacker.getUniqueId()).abilityLevel();
        if (level <= 0 || !roll(settings.abilityChance("sword", level))) return;
        int seconds = Math.max(
                1,
                (int) Math.round(settings.abilityDuration("sword", "duration-seconds", level))
        );
        startBleed(attacker, victim, seconds);
    }

    public void tryAxe(Player attacker, Player victim) {
        int level = stats.profile(attacker.getUniqueId()).abilityLevel();
        if (level <= 0) return;
        if (attacker.getAttackCooldown() < settings.axeMinimumCharge()) return;
        if (!roll(settings.abilityChance("axe", level))) return;

        stun(victim, settings.abilityDuration("axe", "stun-seconds", level));
        prepareArmorMultiplier(
                victim,
                settings.abilityInt("axe", "armor-durability-multiplier", level, 1)
        );
    }

    public void tryBow(Player attacker, Player victim) {
        int level = stats.profile(attacker.getUniqueId()).abilityLevel();
        if (level <= 0 || !roll(settings.abilityChance("bow", level))) return;

        int knockbackBonus = settings.abilityInt("bow", "knockback-bonus-percent", level, 0);
        int slownessTicks = Math.max(
                1,
                (int) Math.round(settings.abilityDuration("bow", "slowness-seconds", level) * 20.0)
        );
        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                slownessTicks,
                0,
                false,
                true,
                true
        ));

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline() || victim.isDead()) return;
            victim.setVelocity(victim.getVelocity().multiply(1.0 + knockbackBonus / 100.0));
            grantGrimGrace(victim, settings.grimMovementGraceMillis());
        });
    }

    public void tryCrossbow(Player attacker, Player victim) {
        int level = stats.profile(attacker.getUniqueId()).abilityLevel();
        if (level <= 0 || !roll(settings.abilityChance("crossbow", level))) return;

        stun(victim, settings.abilityDuration("crossbow", "stun-seconds", level));
        prepareArmorMultiplier(
                victim,
                settings.abilityInt("crossbow", "armor-durability-multiplier", level, 1)
        );
    }

    public int multiplyArmorDurability(Player player, int vanillaDamage) {
        PendingArmorMultiplier pending = pendingArmorMultipliers.get(player.getUniqueId());
        if (pending == null) return vanillaDamage;
        if (pending.expiresAtTick() < Bukkit.getCurrentTick()) {
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

                if (victim.getHealth() <= damage) {
                    dealLethalBleed(attacker, victim);
                    cancelBleed(victim, this);
                    return;
                }
                victim.setHealth(Math.max(0.0, victim.getHealth() - damage));
            }
        };

        bleeds.put(victim.getUniqueId(), task);
        task.runTaskTimer(plugin, 20L, 20L);
    }

    private void dealLethalBleed(Player attacker, Player victim) {
        UUID victimId = victim.getUniqueId();
        combat.forceKiller(victimId, attacker.getUniqueId());
        internalDamageVictims.add(victimId);
        try {
            if (attacker.isOnline()) {
                victim.damage(Math.max(1000.0, victim.getMaxHealth() * 100.0), attacker);
            } else {
                victim.setHealth(0.0);
            }
        } finally {
            internalDamageVictims.remove(victimId);
        }
    }

    private void cancelBleed(Player victim, BukkitRunnable task) {
        task.cancel();
        bleeds.remove(victim.getUniqueId(), task);
    }

    private void stun(Player victim, double seconds) {
        UUID id = victim.getUniqueId();
        long now = System.currentTimeMillis();
        long until = now + Math.max(0L, (long) (seconds * 1000.0));
        stunnedUntil.merge(id, until, Math::max);
        victim.setVelocity(victim.getVelocity().zero());
        grantGrimGrace(victim, Math.max(settings.grimMovementGraceMillis(), until - now + 250L));

        long delayTicks = Math.max(1L, (long) Math.ceil(seconds * 20.0) + 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long storedUntil = stunnedUntil.getOrDefault(id, 0L);
            if (storedUntil <= System.currentTimeMillis()) stunnedUntil.remove(id);
        }, delayTicks);
    }

    private void prepareArmorMultiplier(Player victim, int multiplier) {
        if (multiplier <= 1) return;
        UUID id = victim.getUniqueId();
        long expiresAtTick = Bukkit.getCurrentTick() + 1L;
        PendingArmorMultiplier pending = new PendingArmorMultiplier(
                UUID.randomUUID(),
                multiplier,
                expiresAtTick
        );
        pendingArmorMultipliers.merge(id, pending, (oldValue, newValue) ->
                new PendingArmorMultiplier(
                        newValue.token(),
                        Math.max(oldValue.multiplier(), newValue.multiplier()),
                        Math.max(oldValue.expiresAtTick(), newValue.expiresAtTick())
                )
        );
        PendingArmorMultiplier stored = pendingArmorMultipliers.get(id);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                pendingArmorMultipliers.computeIfPresent(id, (ignored, current) ->
                        current.token().equals(stored.token()) ? null : current
                ), 2L);
    }

    private void grantGrimGrace(Player player, long durationMillis) {
        if (durationMillis <= 0L) return;
        UUID id = player.getUniqueId();
        long until = System.currentTimeMillis() + durationMillis;
        grimGraceUntil.merge(id, until, Math::max);
        long delayTicks = Math.max(1L, (durationMillis + 49L) / 50L + 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long storedUntil = grimGraceUntil.getOrDefault(id, 0L);
            if (storedUntil <= System.currentTimeMillis()) grimGraceUntil.remove(id);
        }, delayTicks);
    }

    private boolean roll(double percent) {
        return ThreadLocalRandom.current().nextDouble(100.0) < percent;
    }

    private record PendingArmorMultiplier(UUID token, int multiplier, long expiresAtTick) {}
}
