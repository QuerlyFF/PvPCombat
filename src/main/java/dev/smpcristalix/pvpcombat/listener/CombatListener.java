package dev.smpcristalix.pvpcombat.listener;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.service.AbilityService;
import dev.smpcristalix.pvpcombat.service.CombatService;
import dev.smpcristalix.pvpcombat.service.NoticeService;
import dev.smpcristalix.pvpcombat.service.PearlService;
import dev.smpcristalix.pvpcombat.service.StatsService;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;

/** Главная точка обработки PvP-попаданий и расхода жемчуга. */
public final class CombatListener implements Listener {
    private static final byte PROJECTILE_BOW = 1;
    private static final byte PROJECTILE_CROSSBOW = 2;

    private final CombatService combat;
    private final PearlService pearls;
    private final StatsService stats;
    private final AbilityService abilities;
    private final NoticeService notices;
    private PvPCombatSettings settings;

    public CombatListener(CombatService combat, PearlService pearls, StatsService stats,
                          AbilityService abilities, NoticeService notices, PvPCombatSettings settings) {
        this.combat = combat;
        this.pearls = pearls;
        this.stats = stats;
        this.abilities = abilities;
        this.notices = notices;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    /** На HIGH меняем только численное значение урона. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageModify(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof EnderCrystal) {
            event.setDamage(event.getDamage() * settings.endCrystalDamageMultiplier());
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(event.getEntity())) return;

        if (!(event.getEntity() instanceof Player victim)) {
            if (!settings.damagePvpOnly()) applyDamageBonus(event, attacker);
            return;
        }
        if (abilities.isInternalDamage(victim)) return;
        applyDamageBonus(event, attacker);
    }

    /**
     * На MONITOR уже известно, что попадание не отменено и действительно нанесло урон.
     * Только после этого создаём Combat-state и запускаем способность.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageResolved(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() <= 0.0 || abilities.isInternalDamage(victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        boolean attackerWasInCombat = combat.inCombat(attacker);
        boolean victimWasInCombat = combat.inCombat(victim);
        combat.tag(attacker, victim);

        if (!attackerWasInCombat) pearls.reset(attacker);
        if (!victimWasInCombat) pearls.reset(victim);
        triggerWeaponAbility(event, attacker, victim);
    }

    /** Запрещаем только бросок, который превысил наш дополнительный Combat-лимит. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunchGate(ProjectileLaunchEvent event) {
        Player player = pearlShooter(event);
        if (player == null || !combat.inCombat(player)) return;
        if (pearls.canThrow(player)) return;

        event.setCancelled(true);
        notices.warn(player, "pearl-cooldown", "Жемчуг на дополнительном КД PvPCombat.");
    }

    /** Заряд тратится только если после всех listener'ов projectile остался разрешён. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunchCommitted(ProjectileLaunchEvent event) {
        Player player = pearlShooter(event);
        if (player == null || !combat.inCombat(player)) return;
        pearls.recordThrow(player);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double reduction = stats.satietyReductionPercent(stats.profile(player.getUniqueId())) / 100.0;
        if (reduction <= 0.0) return;
        event.setExhaustion((float) Math.max(0.0, event.getExhaustion() * (1.0 - reduction)));
    }

    private Player pearlShooter(ProjectileLaunchEvent event) {
        if (event.getEntity().getType() != org.bukkit.entity.EntityType.ENDER_PEARL) return null;
        return event.getEntity().getShooter() instanceof Player player ? player : null;
    }

    private void applyDamageBonus(EntityDamageByEntityEvent event, Player attacker) {
        double bonus = stats.damageBonusPercent(stats.profile(attacker.getUniqueId()));
        event.setDamage(event.getDamage() * (1.0 + bonus / 100.0));
    }

    private void triggerWeaponAbility(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (event.getDamager() instanceof Projectile projectile) {
            Byte weapon = projectile.getPersistentDataContainer().get(
                    PvPCombatPlugin.PROJECTILE_WEAPON_KEY,
                    PersistentDataType.BYTE
            );
            if (weapon == null) return;
            if (weapon == PROJECTILE_CROSSBOW) abilities.tryCrossbow(attacker, victim);
            else if (weapon == PROJECTILE_BOW) abilities.tryBow(attacker, victim);
            return;
        }

        Material weapon = attacker.getInventory().getItemInMainHand().getType();
        if (weapon.name().endsWith("_SWORD")) abilities.trySword(attacker, victim);
        else if (weapon.name().endsWith("_AXE")) abilities.tryAxe(attacker, victim);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
