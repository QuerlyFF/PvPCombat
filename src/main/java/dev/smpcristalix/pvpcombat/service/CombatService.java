package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.api.event.CombatStateChangeEvent;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Хранит Combat-state игрока, длительность конкретных PvP-пар и forced killer. */
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
        boolean firstEntered = putPlayerState(first, second.getUniqueId(), now, expiry);
        boolean secondEntered = putPlayerState(second, first.getUniqueId(), now, expiry);

        PlayerPair pair = PlayerPair.of(first.getUniqueId(), second.getUniqueId());
        pairStates.compute(pair, (ignored, old) -> updatePairState(old, now, expiry));

        if (firstEntered) fireStateChange(first, second.getUniqueId(), true);
        if (secondEntered) fireStateChange(second, first.getUniqueId(), true);
    }

    private boolean putPlayerState(Player player, UUID opponent, long now, long expiry) {
        UUID id = player.getUniqueId();
        CombatState old = states.get(id);
        if (old == null || old.expiresAt <= now) {
            states.put(id, new CombatState(opponent, expiry));
            return true;
        }
        old.opponent = opponent;
        old.expiresAt = expiry;
        return false;
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
        return fightDurationMillis(first.getUniqueId(), second.getUniqueId());
    }

    public long fightDurationMillis(UUID first, UUID second) {
        PairState state = pairStates.get(PlayerPair.of(first, second));
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
        if (opponent != null) forceKiller(player.getUniqueId(), opponent);
    }

    public void forceKiller(UUID victimId, UUID killerId) {
        if (victimId == null || killerId == null || victimId.equals(killerId)) return;
        forcedKillers.put(victimId, killerId);
    }

    public void clearForcedKiller(UUID victimId) {
        forcedKillers.remove(victimId);
    }

    public UUID consumeForcedKiller(Player victim) {
        return forcedKillers.remove(victim.getUniqueId());
    }

    public void clear(Player player) {
        UUID id = player.getUniqueId();
        CombatState old = states.remove(id);
        forcedKillers.remove(id);
        pairStates.keySet().removeIf(pair -> pair.contains(id));
        if (old != null) fireStateChange(player, old.opponent, false);
    }

    public void clearExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, CombatState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CombatState> entry = iterator.next();
            CombatState state = entry.getValue();
            if (state.expiresAt > now) continue;

            iterator.remove();
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null) fireStateChange(online, state.opponent, false);
        }
        pairStates.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private void fireStateChange(Player player, UUID opponentId, boolean inCombat) {
        Bukkit.getPluginManager().callEvent(new CombatStateChangeEvent(player, opponentId, inCombat));
    }

    private static final class CombatState {
        private UUID opponent;
        private long expiresAt;

        private CombatState(UUID opponent, long expiresAt) {
            this.opponent = opponent;
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
