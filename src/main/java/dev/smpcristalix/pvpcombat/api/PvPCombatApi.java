package dev.smpcristalix.pvpcombat.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Публичный API для квестов, наград и других серверных плагинов. */
public interface PvPCombatApi {
    void giveShards(Player player, int amount);
    int countShards(Player player);
    boolean consumeShards(Player player, int amount);
    ItemStack createShard(int amount);
    boolean isShard(ItemStack item);
    boolean isInCombat(Player player);
    PlayerStatsSnapshot getStats(UUID playerId);
}
