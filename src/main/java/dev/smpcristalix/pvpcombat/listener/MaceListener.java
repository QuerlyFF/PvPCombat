package dev.smpcristalix.pvpcombat.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Валидирует ограничения зачарований булавы во всех обычных точках получения и использования. */
public final class MaceListener implements Listener {
    private static final Set<String> ALLOWED = Set.of("wind_burst", "density", "breach");

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (event.getItem().getType() != Material.MACE) return;

        Map<Enchantment, Integer> combined = new HashMap<>(event.getItem().getEnchantments());
        combined.putAll(event.getEnchantsToAdd());
        if (isAllowed(combined)) return;

        event.setCancelled(true);
        notifyPlayer(event.getEnchanter());
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType() != Material.MACE) return;
        if (!isAllowed(result.getEnchantments())) event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResultClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.MACE) return;
        if (isAllowed(item.getEnchantments())) return;

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) notifyPlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvalidMaceAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack mace = player.getInventory().getItemInMainHand();
        if (mace.getType() != Material.MACE || isAllowed(mace.getEnchantments())) return;

        // Даже если некорректная булава появилась через команду/другой плагин,
        // использовать её в бою до исправления зачарований нельзя.
        event.setCancelled(true);
        notifyPlayer(player);
    }

    private boolean isAllowed(Map<Enchantment, Integer> enchantments) {
        for (var entry : enchantments.entrySet()) {
            String key = entry.getKey().getKey().getKey();
            if (!ALLOWED.contains(key) || entry.getValue() != 1) return false;
        }

        List<Enchantment> list = enchantments.keySet().stream().toList();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Enchantment first = list.get(i);
                Enchantment second = list.get(j);
                if (first.conflictsWith(second) || second.conflictsWith(first)) return false;
            }
        }
        return true;
    }

    private void notifyPlayer(Player player) {
        player.sendActionBar(Component.text(
                "Для булавы разрешены только Порыв ветра I, Плотность I и Пробитие I с vanilla-совместимостью.",
                NamedTextColor.RED
        ));
    }
}
