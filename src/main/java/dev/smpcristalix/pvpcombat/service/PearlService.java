package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Три (по умолчанию) PvPCombat-заряда жемчуга поверх ванильного cooldown. */
public final class PearlService {
    private final Map<UUID, PearlState> states = new HashMap<>();
    private PvPCombatSettings settings;

    public PearlService(PvPCombatSettings settings) {
        this.settings = settings;
    }

    /** Reload сбрасывает runtime-счётчики, чтобы смена charges не создала вечный cooldown. */
    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
        states.clear();
    }

    public boolean canThrow(Player player) {
        PearlState state = state(player);
        normalizeCooldown(state);
        return state.cooldownUntilMillis == 0L && state.usedCharges < settings.pearlCharges();
    }

    public void recordThrow(Player player) {
        PearlState state = state(player);
        normalizeCooldown(state);
        state.usedCharges++;
        if (state.usedCharges >= settings.pearlCharges()) {
            state.cooldownUntilMillis = System.currentTimeMillis() + settings.pearlRechargeSeconds() * 1000L;
        }
    }

    public int remaining(Player player) {
        PearlState state = state(player);
        normalizeCooldown(state);
        if (state.cooldownUntilMillis != 0L) return 0;
        return Math.max(0, settings.pearlCharges() - state.usedCharges);
    }

    public long cooldownSeconds(Player player) {
        PearlState state = state(player);
        normalizeCooldown(state);
        if (state.cooldownUntilMillis == 0L) return 0L;
        long remainingMillis = state.cooldownUntilMillis - System.currentTimeMillis();
        return Math.max(0L, (remainingMillis + 999L) / 1000L);
    }

    /** Новый отдельный Combat начинается с полного набора зарядов. */
    public void reset(Player player) {
        states.remove(player.getUniqueId());
    }

    private void normalizeCooldown(PearlState state) {
        if (state.cooldownUntilMillis == 0L) return;
        if (state.cooldownUntilMillis > System.currentTimeMillis()) return;
        state.cooldownUntilMillis = 0L;
        state.usedCharges = 0;
    }

    private PearlState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new PearlState());
    }

    private static final class PearlState {
        private int usedCharges;
        private long cooldownUntilMillis;
    }
}
