package dev.smpcristalix.pvpcombat.listener;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.service.AbilityService;
import dev.smpcristalix.pvpcombat.service.CombatService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/** Ограничения элитр/фейерверков и блокировка перемещения во время полного стана. */
public final class MovementListener implements Listener {
    private final CombatService combat;
    private final AbilityService abilities;
    private PvPCombatSettings settings;

    public MovementListener(CombatService combat, AbilityService abilities, PvPCombatSettings settings) {
        this.combat = combat;
        this.abilities = abilities;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return;
        if (combat.inCombat(player) && !settings.allowElytraInCombat()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        Player player = event.getPlayer();
        if (combat.inCombat(player) && !settings.allowFireworksInCombat()) {
            event.setCancelled(true);
            player.sendMessage("§cУскорение фейерверком во время PvP Combat запрещено.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!abilities.isStunned(event.getPlayer()) || event.getTo() == null) return;
        boolean changedPosition = event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();
        if (!changedPosition) return;

        // Положение блокируем полностью, но оставляем возможность вращать камерой.
        var locked = event.getFrom().clone();
        locked.setYaw(event.getTo().getYaw());
        locked.setPitch(event.getTo().getPitch());
        event.setTo(locked);
    }
}
