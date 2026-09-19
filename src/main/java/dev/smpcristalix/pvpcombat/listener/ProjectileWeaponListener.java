package dev.smpcristalix.pvpcombat.listener;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Помечает только реальные снаряды лука/арбалета.
 *
 * <p>EntityShootBowEvent даёт именно тот предмет, из которого был произведён выстрел,
 * поэтому трезубцы и другие projectile больше не могут ошибочно вызвать bow/crossbow proc.</p>
 */
public final class ProjectileWeaponListener implements Listener {
    public static final byte BOW = 1;
    public static final byte CROSSBOW = 2;

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getBow() == null) return;

        Material weapon = event.getBow().getType();
        byte marker;
        if (weapon == Material.CROSSBOW) marker = CROSSBOW;
        else if (weapon == Material.BOW) marker = BOW;
        else return;

        event.getProjectile().getPersistentDataContainer().set(
                PvPCombatPlugin.PROJECTILE_WEAPON_KEY,
                PersistentDataType.BYTE,
                marker
        );
    }
}
