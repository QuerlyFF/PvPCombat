package dev.smpcristalix.pvpcombat.listener;

import dev.smpcristalix.pvpcombat.service.AbilityService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

/** Применяет множитель способности к уже рассчитанному vanilla-износу брони. */
public final class ArmorDurabilityListener implements Listener {
    private final AbilityService abilities;

    public ArmorDurabilityListener(AbilityService abilities) {
        this.abilities = abilities;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Material material = event.getItem().getType();
        if (!isArmor(material)) return;
        event.setDamage(abilities.multiplyArmorDurability(
                event.getPlayer(),
                material,
                event.getDamage()
        ));
    }

    private boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }
}
