package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Осколок распознаётся по PDC-метке, а не по материалу или названию. */
public final class ShardService {
    private final NamespacedKey key;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private PvPCombatSettings settings;

    public ShardService(PvPCombatPlugin plugin, PvPCombatSettings settings) {
        this.key = new NamespacedKey(plugin, "shard");
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public ItemStack create(int amount) {
        Material material = Material.matchMaterial(settings.shardMaterial());
        if (material == null) material = Material.AMETHYST_SHARD;

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacy.deserialize(settings.shardName()));
        List<String> configuredLore = settings.shardLore();
        if (!configuredLore.isEmpty()) {
            meta.lore(configuredLore.stream().map(legacy::deserialize).toList());
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isShard(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    public void give(Player player, int amount) {
        if (amount <= 0) return;

        Material material = Material.matchMaterial(settings.shardMaterial());
        int maxStack = material == null ? 64 : material.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maxStack);
            player.getInventory().addItem(create(stackAmount)).values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover)
            );
            remaining -= stackAmount;
        }
    }

    public boolean consumeOne(Player player) {
        return consume(player, 1);
    }

    /** Списание транзакционное: если Осколков недостаточно, инвентарь не меняется. */
    public boolean consume(Player player, int amount) {
        if (amount <= 0) return true;
        if (count(player) < amount) return false;

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isShard(item)) continue;
            int take = Math.min(item.getAmount(), remaining);
            int left = item.getAmount() - take;
            remaining -= take;
            if (left == 0) player.getInventory().setItem(slot, null);
            else item.setAmount(left);
        }
        return true;
    }

    public int count(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isShard(item)) count += item.getAmount();
        }
        return count;
    }
}
