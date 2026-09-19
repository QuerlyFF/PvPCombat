package dev.smpcristalix.pvpcombat.service;

import dev.smpcristalix.pvpcombat.api.event.PlayerStatChangeEvent;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.data.PlayerProfile;
import org.bukkit.Bukkit;
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
                PlayerStatChangeEvent.Stat.ABILITY,
                profile::abilityLevel,
                value -> profile.abilityLevel(value));
        addCandidate(candidates, profile.damageLevel() > 0, "Урон", "damage",
                PlayerStatChangeEvent.Stat.DAMAGE,
                profile::damageLevel,
                value -> profile.damageLevel(value));
        addCandidate(candidates, profile.healthStep() > stats.minHealthStep(), "Здоровье", "health",
                PlayerStatChangeEvent.Stat.HEALTH,
                profile::healthStep,
                value -> profile.healthStep(value));
        addCandidate(candidates, profile.speedLevel() > 0, "Скорость", "speed",
                PlayerStatChangeEvent.Stat.SPEED,
                profile::speedLevel,
                value -> profile.speedLevel(value));
        addCandidate(candidates, profile.satietyLevel() > 0, "Сытость", "satiety",
                PlayerStatChangeEvent.Stat.SATIETY,
                profile::satietyLevel,
                value -> profile.satietyLevel(value));

        int totalWeight = candidates.stream().mapToInt(Candidate::weight).sum();
        if (totalWeight <= 0) return null;

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (Candidate candidate : candidates) {
            roll -= candidate.weight();
            if (roll >= 0) continue;

            int oldValue = candidate.getter().get();
            int newValue = oldValue - 1;
            candidate.setter().set(newValue);
            profile.lastStatLossAt(now);
            stats.apply(player);
            Bukkit.getPluginManager().callEvent(new PlayerStatChangeEvent(
                    player,
                    candidate.stat(),
                    oldValue,
                    newValue,
                    PlayerStatChangeEvent.Reason.DEATH_PENALTY
            ));
            return candidate.name();
        }
        return null;
    }

    private void addCandidate(List<Candidate> candidates, boolean eligible, String name,
                              String configKey, PlayerStatChangeEvent.Stat stat,
                              IntGetter getter, IntSetter setter) {
        if (!eligible) return;
        int weight = settings.deathWeight(configKey);
        if (weight <= 0) return;
        candidates.add(new Candidate(name, weight, stat, getter, setter));
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }

    private record Candidate(
            String name,
            int weight,
            PlayerStatChangeEvent.Stat stat,
            IntGetter getter,
            IntSetter setter
    ) {}
}
