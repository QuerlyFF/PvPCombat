package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит эфемерное состояние PvP Combat.
 *
 * <p>Кроме персонального таймера отдельно хранится состояние каждой пары игроков.
 * Это важно для правила трофейного тотема: длительность боя считается именно для
 * killer/victim, а не для всего Combat игрока с любыми противниками.</p>
 */
public final class CombatService {
    private final Map<UUID, CombatState> states = new HashMap<>();
    private final Map<PlayerPair, PairState> pairStates = new HashMap<>();
    private final Map<UUID, UUID> forcedKillers = new HashMap<>();
    private PvPCombatSettings settings;

    public CombatService(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public void tag(Player first, Player second) {
        long now = System.currentTimeMillis();
        long expiry = now + settings.combatDurationSeconds() * 1000L;
        states.compute(first.getUniqueId(), (id, old) -> updatePlayerState(old, second.getUniqueId(), now, expiry));
        states.compute(second.getUniqueId(), (id, old) -> updatePlayerState(old, first.getUniqueId(), now, expiry));
        PlayerPair pair = PlayerPair.of(first.getUniqueId(), second.getUniqueId());
        pairStates.compute(pair, (ignored, old) -> updatePairState(old, now, expiry));
    }

    private CombatState updatePlayerState(CombatState old, UUID opponent, long now, long expiry) {
        if (old == null || old.expiresAt <= now) return new CombatState(opponent, now, expiry);
        old.opponent = opponent;
        old.expiresAt = expiry;
        return old;
    }

    private PairState updatePairState(PairState old, long now, long expiry) {
        if (old == null || old.expiresAt <= now) return new PairState(now, expiry);
        old.expiresAt = expiry;
        return old;
    }

    public boolean inCombat(Player player) {
        return remainingMillis(player) > 0L;
    }

    public long remainingMillis(Player player) {
        CombatState state = states.get(player.getUniqueId());
        return state == null ? 0L : Math.max(0L, state.expiresAt - System.currentTimeMillis());
    }

    public long fightDurationMillis(Player first, Player second) {
        PairState state = pairStates.get(PlayerPair.of(first.getUniqueId(), second.getUniqueId()));
        if (state == null || state.expiresAt <= System.currentTimeMillis()) return 0L;
        return Math.max(0L, System.currentTimeMillis() - state.startedAt);
    }

    public UUID opponent(Player player) {
        CombatState state = states.get(player.getUniqueId());
        if (state == null || state.expiresAt <= System.currentTimeMillis()) return null;
        return state.opponent;
    }

    public void markCombatLogout(Player player) {
        UUID opponent = opponent(player);
        if (opponent != null) forcedKillers.put(player.getUniqueId(), opponent);
    }

    public UUID consumeForcedKiller(Player victim) {
        return forcedKillers.remove(victim.getUniqueId());
    }

    public void clear(Player player) {
        UUID id = player.getUniqueId();
        states.remove(id);
        forcedKillers.remove(id);
        pairStates.keySet().removeIf(pair -> pair.contains(id));
    }

    public void clearExpired() {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        pairStates.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private static final class CombatState {
        private UUID opponent;
        private final long startedAt;
        private long expiresAt;

        private CombatState(UUID opponent, long startedAt, long expiresAt) {
            this.opponent = opponent;
            this.startedAt = startedAt;
            this.expiresAt = expiresAt;
        }
    }

    private static final class PairState {
        private final long startedAt;
        private long expiresAt;

        private PairState(long startedAt, long expiresAt) {
            this.startedAt = startedAt;
            this.expiresAt = expiresAt;
        }
    }

    private record PlayerPair(UUID first, UUID second) {
        private static PlayerPair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new PlayerPair(a, b) : new PlayerPair(b, a);
        }

        private boolean contains(UUID id) {
            return first.equals(id) || second.equals(id);
        }
    }
}
