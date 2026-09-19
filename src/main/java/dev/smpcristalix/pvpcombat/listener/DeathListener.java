package dev.smpcristalix.pvpcombat.listener;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.service.CombatService;
import dev.smpcristalix.pvpcombat.service.DeathPenaltyService;
import dev.smpcristalix.pvpcombat.service.PearlService;
import dev.smpcristalix.pvpcombat.service.RewardService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** Смерть, штраф прогресса, награда и трофейные тотемы обрабатываются единым pipeline. */
public final class DeathListener implements Listener {
    private final CombatService combat;
    private final PearlService pearls;
    private final DeathPenaltyService penalties;
    private final RewardService rewards;
    private PvPCombatSettings settings;

    public DeathListener(CombatService combat, PearlService pearls, DeathPenaltyService penalties,
                         RewardService rewards, PvPCombatSettings settings) {
        this.combat = combat;
        this.pearls = pearls;
        this.penalties = penalties;
        this.rewards = rewards;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        forceFullDrop(event);

        String lost = penalties.apply(victim);
        if (lost != null) victim.sendMessage("§cПосле смерти потеряна ступень: §f" + lost);

        UUID killerId = resolveKillerId(victim);
        if (killerId != null && !killerId.equals(victim.getUniqueId())) {
            boolean rewarded = rewards.reward(killerId, victim);
            Player onlineKiller = Bukkit.getPlayer(killerId);
            if (rewarded && onlineKiller != null) {
                onlineKiller.sendMessage("§dТы получил Осколок за PvP-убийство.");
            }
            markEligibleTotems(event, killerId, victim.getUniqueId());
        }

        combat.clear(victim);
        pearls.reset(victim);
    }

    private UUID resolveKillerId(Player victim) {
        UUID forcedKiller = combat.consumeForcedKiller(victim);
        if (forcedKiller != null) return forcedKiller;
        Player vanillaKiller = victim.getKiller();
        return vanillaKiller == null ? null : vanillaKiller.getUniqueId();
    }

    private void forceFullDrop(PlayerDeathEvent event) {
        if (!event.getKeepInventory()) return;
        event.getDrops().clear();
        for (ItemStack item : event.getEntity().getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) event.getDrops().add(item.clone());
        }
        event.setKeepInventory(false);
        event.getEntity().getInventory().clear();
    }

    private void markEligibleTotems(PlayerDeathEvent event, UUID killerId, UUID victimId) {
        long minimumFightMillis = settings.totemLootMinFightSeconds() * 1000L;
        if (combat.fightDurationMillis(killerId, victimId) < minimumFightMillis) return;

        for (ItemStack drop : event.getDrops()) {
            if (drop.getType() != Material.TOTEM_OF_UNDYING) continue;
            var meta = drop.getItemMeta();
            meta.getPersistentDataContainer().set(
                    PvPCombatPlugin.TROPHY_TOTEM_KEY,
                    PersistentDataType.STRING,
                    killerId.toString()
            );
            drop.setItemMeta(meta);
        }
    }
}
