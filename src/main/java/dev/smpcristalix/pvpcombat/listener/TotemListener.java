package dev.smpcristalix.pvpcombat.listener;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.service.CombatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Лимит тотемов, Combat-ограничения источников и защита от спама уведомлениями. */
public final class TotemListener implements Listener {
    private static final int DENIED_PICKUP_RETRY_TICKS = 40;

    private final CombatService combat;
    private final Map<UUID, Long> lastNoticeAt = new HashMap<>();
    private PvPCombatSettings settings;

    public TotemListener(CombatService combat, PvPCombatSettings settings) {
        this.combat = combat;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Item entityItem = event.getItem();
        ItemStack stack = entityItem.getItemStack();
        if (stack.getType() != Material.TOTEM_OF_UNDYING) return;

        if (countTotems(player) + stack.getAmount() > settings.maxTotemsCarried()) {
            denyPickup(event, entityItem);
            notifyLimited(player, "Лимит тотемов: " + settings.maxTotemsCarried());
            return;
        }

        if (!combat.inCombat(player) || settings.allowExternalTotemSourcesInCombat()) return;

        String trophyOwner = stack.hasItemMeta()
                ? stack.getItemMeta().getPersistentDataContainer().get(
                        PvPCombatPlugin.TROPHY_TOTEM_KEY,
                        PersistentDataType.STRING
                )
                : null;
        if (!player.getUniqueId().toString().equals(trophyOwner)) {
            denyPickup(event, entityItem);
            notifyLimited(player, "Во время Combat нельзя пополнять тотемы из внешних источников.");
            return;
        }

        // Трофейная метка одноразовая: после разрешённого подбора это обычный тотем.
        var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().remove(PvPCombatPlugin.TROPHY_TOTEM_KEY);
        stack.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (combat.inCombat(player)
                && !settings.allowExternalTotemSourcesInCombat()
                && event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && isTotem(event.getCursor())) {
            event.setCancelled(true);
            notifyLimited(player, "Во время Combat нельзя собирать тотемы из контейнеров.");
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (!isTotem(current)) return;
        boolean external = event.getClickedInventory() != null
                && event.getClickedInventory() != player.getInventory();
        if (!external) return;

        if (combat.inCombat(player) && !settings.allowExternalTotemSourcesInCombat()) {
            event.setCancelled(true);
            notifyLimited(player, "Во время Combat нельзя брать тотемы из контейнеров.");
            return;
        }

        if (countTotems(player) + current.getAmount() > settings.maxTotemsCarried()) {
            event.setCancelled(true);
            notifyLimited(player, "Лимит тотемов: " + settings.maxTotemsCarried());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!combat.inCombat(player) || settings.allowExternalTotemSourcesInCombat()) return;
        if (!isTotem(event.getOldCursor())) return;
        event.setCancelled(true);
        notifyLimited(player, "Во время Combat нельзя пополнять тотемы из внешних источников.");
    }

    /**
     * Страховка от прямой выдачи тотемов другими плагинами/командами.
     * Лишние предметы не удаляются — они выбрасываются рядом с игроком.
     */
    public void enforceLimit(Player player) {
        int maximum = settings.maxTotemsCarried();
        int seen = 0;
        int overflow = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isTotem(item)) continue;

            int canKeep = Math.max(0, maximum - seen);
            int keep = Math.min(canKeep, item.getAmount());
            int remove = item.getAmount() - keep;
            seen += keep;
            overflow += remove;

            if (remove <= 0) continue;
            if (keep == 0) player.getInventory().setItem(slot, null);
            else item.setAmount(keep);
        }

        for (int i = 0; i < overflow; i++) {
            Item dropped = player.getWorld().dropItemNaturally(
                    player.getLocation().add(0.0, 0.25, 0.0),
                    new ItemStack(Material.TOTEM_OF_UNDYING)
            );
            dropped.setPickupDelay(DENIED_PICKUP_RETRY_TICKS);
            dropped.setVelocity(player.getLocation().getDirection().multiply(0.22).setY(0.18));
        }

        if (overflow > 0) {
            notifyLimited(player, "Лимит тотемов: " + maximum + ". Лишние тотемы выброшены рядом.");
        }
    }

    public int countTotems(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTotem(item)) count += item.getAmount();
        }
        return count;
    }

    private void denyPickup(EntityPickupItemEvent event, Item item) {
        event.setCancelled(true);
        // Главное исправление спама со скриншота: пока предмет лежит под ногами,
        // Paper больше не пытается вызвать pickup-event каждый тик.
        item.setPickupDelay(DENIED_PICKUP_RETRY_TICKS);
    }

    private boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING;
    }

    private void notifyLimited(Player player, String message) {
        long now = System.currentTimeMillis();
        long previous = lastNoticeAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < settings.totemNoticeCooldownMillis()) return;
        lastNoticeAt.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }
}
