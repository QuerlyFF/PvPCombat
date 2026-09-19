package dev.smpcristalix.pvpcombat.listener;

import dev.smpcristalix.pvpcombat.service.GuiService;
import dev.smpcristalix.pvpcombat.service.ShardService;
import dev.smpcristalix.pvpcombat.service.UpgradeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** ПКМ настоящим Осколком открывает GUI; клик по slot выбирает характеристику. */
public final class ShardGuiListener implements Listener {
    private final ShardService shards;
    private final GuiService gui;
    private final UpgradeService upgrades;

    public ShardGuiListener(ShardService shards, GuiService gui, UpgradeService upgrades) {
        this.shards = shards;
        this.gui = gui;
        this.upgrades = upgrades;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!shards.isShard(event.getItem())) return;

        event.setCancelled(true);
        gui.open(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!gui.isUpgradeMenu(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UpgradeService.Stat stat = statBySlot(event.getRawSlot());
        if (stat == null) return;

        UpgradeService.Result result = upgrades.upgrade(player, stat);
        player.sendMessage((result.success() ? "§a" : "§c") + result.message());
        gui.open(player);
    }

    private UpgradeService.Stat statBySlot(int rawSlot) {
        return switch (rawSlot) {
            case 10 -> UpgradeService.Stat.DAMAGE;
            case 11 -> UpgradeService.Stat.HEALTH;
            case 13 -> UpgradeService.Stat.SPEED;
            case 15 -> UpgradeService.Stat.SATIETY;
            case 16 -> UpgradeService.Stat.ABILITY;
            default -> null;
        };
    }
}
