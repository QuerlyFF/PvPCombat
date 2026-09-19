package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.data.PlayerProfile;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Снимает ровно одну доступную ступень и соблюдает cooldown штрафа смерти. */
public final class DeathPenaltyService {
    private final StatsService stats;
    private PvPCombatSettings settings;

    public DeathPenaltyService(StatsService stats, PvPCombatSettings settings) {
        this.stats = stats;
        this.settings = settings;
    }

    public void reload(PvPCombatSettings settings) {
        this.settings = settings;
    }

    public String apply(Player player) {
        PlayerProfile profile = stats.profile(player.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldownMillis = settings.statLossCooldownMinutes() * 60_000L;
        if (now - profile.lastStatLossAt() < cooldownMillis) return null;

        List<Candidate> candidates = new ArrayList<>();
        addCandidate(candidates, profile.abilityLevel() > 0, "Умения", "ability",
                () -> profile.abilityLevel(profile.abilityLevel() - 1));
        addCandidate(candidates, profile.damageLevel() > 0, "Урон", "damage",
                () -> profile.damageLevel(profile.damageLevel() - 1));
        addCandidate(candidates, profile.healthStep() > stats.minHealthStep(), "Здоровье", "health",
                () -> profile.healthStep(profile.healthStep() - 1));
        addCandidate(candidates, profile.speedLevel() > 0, "Скорость", "speed",
                () -> profile.speedLevel(profile.speedLevel() - 1));
        addCandidate(candidates, profile.satietyLevel() > 0, "Сытость", "satiety",
                () -> profile.satietyLevel(profile.satietyLevel() - 1));

        int totalWeight = candidates.stream().mapToInt(Candidate::weight).sum();
        if (totalWeight <= 0) return null;

        // Недоступные характеристики вообще не участвуют в totalWeight, поэтому
        // оставшиеся веса автоматически нормализуются без отдельной математики.
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (Candidate candidate : candidates) {
            roll -= candidate.weight();
            if (roll >= 0) continue;

            candidate.action().run();
            profile.lastStatLossAt(now);
            stats.apply(player);
            return candidate.name();
        }
        return null;
    }

    private void addCandidate(List<Candidate> candidates, boolean eligible, String name,
                              String configKey, Runnable action) {
        if (!eligible) return;
        int weight = settings.deathWeight(configKey);
        if (weight <= 0) return;
        candidates.add(new Candidate(name, weight, action));
    }

    private record Candidate(String name, int weight, Runnable action) {}
}
