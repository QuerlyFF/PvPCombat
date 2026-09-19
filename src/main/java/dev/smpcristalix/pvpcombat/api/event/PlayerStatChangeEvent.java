package dev.smpcristalix.pvpcombat.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Публичное событие изменения одной ступени прогрессии PvPCombat. */
public final class PlayerStatChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Stat stat;
    private final int oldValue;
    private final int newValue;
    private final Reason reason;

    public PlayerStatChangeEvent(Player player, Stat stat, int oldValue, int newValue, Reason reason) {
        this.player = player;
        this.stat = stat;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public Stat getStat() {
        return stat;
    }

    public int getOldValue() {
        return oldValue;
    }

    public int getNewValue() {
        return newValue;
    }

    public Reason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum Stat {
        DAMAGE,
        HEALTH,
        SPEED,
        SATIETY,
        ABILITY
    }

    public enum Reason {
        UPGRADE,
        DEATH_PENALTY,
        ADMIN
    }
}
