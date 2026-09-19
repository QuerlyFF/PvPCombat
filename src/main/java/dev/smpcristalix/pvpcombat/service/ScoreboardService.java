package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Combat sidebar с безопасным fallback, если SIDEBAR уже занят или scoreboard разделяется игроками. */
public final class ScoreboardService {
    private static final String OBJECTIVE_NAME = "pvpcombat_core";
    private static final String COMBAT_SUFFIX = "§0§r";
    private static final String PEARL_SUFFIX = "§1§r";

    private final CombatService combat;
    private final PearlService pearls;
    private final NoticeService notices;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, PlayerBoardState> states = new HashMap<>();
    private PvPCombatSettings settings;

    public ScoreboardService(CombatService combat, PearlService pearls, NoticeService notices,
                             PvPCombatSettings settings) {
        this.combat = combat;
        this.pearls = pearls;
        this.notices = notices;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
        states.values().forEach(state -> state.objective.displayName(scoreboardTitle()));
    }

    /**
     * Один проход считает, какие Scoreboard разделяются игроками, второй обновляет UI.
     * Это O(n) вместо прежней проверки каждого игрока против всех остальных O(n²).
     */
    public void updateAll(Collection<? extends Player> players) {
        Map<Scoreboard, Integer> boardUsers = new IdentityHashMap<>();
        for (Player player : players) {
            boardUsers.merge(player.getScoreboard(), 1, Integer::sum);
        }

        for (Player player : players) {
            update(player, boardUsers.getOrDefault(player.getScoreboard(), 0) > 1);
        }
    }

    public void update(Player player) {
        update(player, false);
    }

    private void update(Player player, boolean sharedBoard) {
        if (!settings.scoreboardEnabled() || !combat.inCombat(player)) {
            clear(player);
            return;
        }

        Scoreboard currentBoard = player.getScoreboard();
        if (sharedBoard) {
            releaseOurSidebar(player, currentBoard);
            showFallback(player);
            return;
        }

        PlayerBoardState state = stateFor(player);
        Objective currentSidebar = state.board.getObjective(DisplaySlot.SIDEBAR);
        if (currentSidebar != null && currentSidebar != state.objective) {
            showFallback(player);
            return;
        }

        if (currentSidebar != state.objective) state.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateLine(state, combatLine(player) + COMBAT_SUFFIX, 2, true);
        updateLine(state, pearlLine(player) + PEARL_SUFFIX, 1, false);
    }

    public void clear(Player player) {
        PlayerBoardState state = states.get(player.getUniqueId());
        if (state == null) return;
        if (player.getScoreboard() != state.board) {
            states.remove(player.getUniqueId());
            return;
        }

        if (state.board.getObjective(DisplaySlot.SIDEBAR) == state.objective) {
            state.objective.setDisplaySlot(null);
        }
        clearLines(state);
    }

    public void forget(Player player) {
        PlayerBoardState state = states.remove(player.getUniqueId());
        if (state == null) return;
        if (state.board.getObjective(DisplaySlot.SIDEBAR) == state.objective) {
            state.objective.setDisplaySlot(null);
        }
        clearLines(state);
    }

    private void releaseOurSidebar(Player player, Scoreboard board) {
        PlayerBoardState existing = states.get(player.getUniqueId());
        if (existing == null || existing.board != board) return;
        if (board.getObjective(DisplaySlot.SIDEBAR) == existing.objective) {
            existing.objective.setDisplaySlot(null);
        }
        clearLines(existing);
    }

    private void showFallback(Player player) {
        if (!settings.scoreboardActionBarFallback() || notices.hasPriorityNotice(player)) return;
        player.sendActionBar(Component.text(
                ChatColor.stripColor(combatLine(player))
                        + " | "
                        + ChatColor.stripColor(pearlLine(player))
        ));
    }

    private PlayerBoardState stateFor(Player player) {
        Scoreboard board = player.getScoreboard();
        PlayerBoardState old = states.get(player.getUniqueId());
        if (old != null && old.board == board) return old;

        if (old != null) {
            if (old.board.getObjective(DisplaySlot.SIDEBAR) == old.objective) {
                old.objective.setDisplaySlot(null);
            }
            clearLines(old);
        }

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(
                    OBJECTIVE_NAME,
                    Criteria.DUMMY,
                    scoreboardTitle()
            );
        }

        PlayerBoardState created = new PlayerBoardState(board, objective);
        states.put(player.getUniqueId(), created);
        return created;
    }

    private void updateLine(PlayerBoardState state, String line, int score, boolean first) {
        String previous = first ? state.combatLine : state.pearlLine;
        if (line.equals(previous)) return;
        if (previous != null) state.objective.getScore(previous).resetScore();
        state.objective.getScore(line).setScore(score);
        if (first) state.combatLine = line;
        else state.pearlLine = line;
    }

    private void clearLines(PlayerBoardState state) {
        if (state.combatLine != null) {
            state.objective.getScore(state.combatLine).resetScore();
            state.combatLine = null;
        }
        if (state.pearlLine != null) {
            state.objective.getScore(state.pearlLine).resetScore();
            state.pearlLine = null;
        }
    }

    private String combatLine(Player player) {
        long seconds = (combat.remainingMillis(player) + 999L) / 1000L;
        return color(settings.scoreboardCombatLine().replace("{seconds}", Long.toString(seconds)));
    }

    private String pearlLine(Player player) {
        long cooldown = pearls.cooldownSeconds(player);
        if (cooldown > 0) {
            return color(settings.scoreboardPearlCooldownLine().replace("{seconds}", Long.toString(cooldown)));
        }
        return color(settings.scoreboardPearlReadyLine()
                .replace("{remaining}", Integer.toString(pearls.remaining(player)))
                .replace("{max}", Integer.toString(settings.pearlCharges())));
    }

    private Component scoreboardTitle() {
        String value = settings.scoreboardTitle();
        return legacy.deserialize(value == null ? "" : value);
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private static final class PlayerBoardState {
        private final Scoreboard board;
        private final Objective objective;
        private String combatLine;
        private String pearlLine;

        private PlayerBoardState(Scoreboard board, Objective objective) {
            this.board = board;
            this.objective = objective;
        }
    }
}
