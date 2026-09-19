package dev.smpcristalix.pvpcombat.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Публичное событие входа игрока в PvP Combat и выхода из него. */
public final class CombatStateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID opponentId;
    private final boolean inCombat;

    public CombatStateChangeEvent(Player player, @Nullable UUID opponentId, boolean inCombat) {
        this.player = player;
        this.opponentId = opponentId;
        this.inCombat = inCombat;
    }

    public Player getPlayer() {
        return player;
    }

    public @Nullable UUID getOpponentId() {
        return opponentId;
    }

    public boolean isInCombat() {
        return inCombat;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
