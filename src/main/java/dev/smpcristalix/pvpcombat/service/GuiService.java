package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.data.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** GUI только отображает данные; правила и стоимость прокачки находятся в UpgradeService. */
public final class GuiService {
    public static final String TITLE = "PvPCombat — Прокачка";

    private final StatsService stats;
    private final ShardService shards;

    public GuiService(StatsService stats, ShardService shards) {
        this.stats = stats;
        this.shards = shards;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(TITLE));
        PlayerProfile profile = stats.profile(player.getUniqueId());

        inventory.setItem(10, statItem(Material.IRON_SWORD, "Урон",
                "Ступень: " + profile.damageLevel()));
        inventory.setItem(11, statItem(Material.RED_DYE, "Здоровье",
                "Сердец: " + String.format(Locale.ROOT, "%.1f", stats.healthHearts(profile))));
        inventory.setItem(13, statItem(Material.SUGAR, "Скорость",
                "Ступень: " + profile.speedLevel()));
        inventory.setItem(15, statItem(Material.COOKED_BEEF, "Сытость",
                "Ступень: " + profile.satietyLevel()));
        inventory.setItem(16, statItem(Material.ENCHANTED_BOOK, "Умения",
                "Уровень: " + profile.abilityLevel()));
        inventory.setItem(22, infoItem(Material.AMETHYST_SHARD, "Осколки",
                "В инвентаре: " + shards.count(player)));

        player.openInventory(inventory);
    }

    private ItemStack statItem(Material material, String name, String value) {
        ItemStack item = infoItem(material, name, value);
        var meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text(value, NamedTextColor.GRAY),
                Component.text("ЛКМ — улучшить за 1 Осколок", NamedTextColor.DARK_GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem(Material material, String name, String value) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(Component.text(value, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }
}
