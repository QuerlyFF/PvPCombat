package dev.smpcristalix.pvpcombat.data;

import dev.smpcristalix.pvpcombat.service.RewardService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** UUID-хранилище прогресса, anti-farm cooldown и отложенных наград. */
public final class YamlDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Object ioLock = new Object();
    private final AtomicBoolean asyncSaveRunning = new AtomicBoolean(false);
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
    private final Map<String, Long> rewardCooldowns = new HashMap<>();
    private final Map<UUID, Integer> pendingShards = new HashMap<>();

    public YamlDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        profiles.clear();
        rewardCooldowns.clear();
        pendingShards.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadProfiles(yaml);
        loadRewardCooldowns(yaml);
        loadPendingShards(yaml);
    }

    private void loadProfiles(YamlConfiguration yaml) {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;

        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerProfile profile = new PlayerProfile();
                String base = "players." + key + ".";
                profile.damageLevel(yaml.getInt(base + "damage"));
                profile.healthStep(yaml.getInt(base + "health-step"));
                profile.speedLevel(yaml.getInt(base + "speed"));
                profile.satietyLevel(yaml.getInt(base + "satiety"));
                profile.abilityLevel(yaml.getInt(base + "ability"));
                profile.lastStatLossAt(yaml.getLong(base + "last-stat-loss"));
                profiles.put(uuid, profile);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Пропущен повреждённый UUID в data.yml: " + key);
            }
        }
    }

    private void loadRewardCooldowns(YamlConfiguration yaml) {
        ConfigurationSection rewards = yaml.getConfigurationSection("reward-cooldowns");
        if (rewards == null) return;

        for (String key : rewards.getKeys(false)) {
            Object value = rewards.get(key);
            if (value instanceof Number number) {
                rewardCooldowns.put(key, number.longValue());
                continue;
            }

            ConfigurationSection killerSection = rewards.getConfigurationSection(key);
            if (killerSection == null) continue;
            for (String victim : killerSection.getKeys(false)) {
                rewardCooldowns.put(
                        RewardService.migrationKey(key, victim),
                        killerSection.getLong(victim)
                );
            }
        }
    }

    private void loadPendingShards(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("pending-shards");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int amount = Math.max(0, section.getInt(key));
                if (amount > 0) pendingShards.put(uuid, amount);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Пропущен повреждённый pending-shards UUID: " + key);
            }
        }
    }

    public PlayerProfile profile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, ignored -> new PlayerProfile());
    }

    public long rewardCooldown(String key) {
        return rewardCooldowns.getOrDefault(key, 0L);
    }

    public void rewardCooldown(String key, long value) {
        rewardCooldowns.put(key, value);
    }

    public void purgeRewardCooldownsBefore(long cutoff) {
        rewardCooldowns.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    public void addPendingShards(UUID playerId, int amount) {
        if (amount <= 0) return;
        pendingShards.merge(playerId, amount, Integer::sum);
    }

    public int takePendingShards(UUID playerId) {
        return pendingShards.remove(playerId) == null
                ? 0
                : Math.max(0, pendingShards.getOrDefault(playerId, 0));
    }

    public int removePendingShards(UUID playerId) {
        Integer amount = pendingShards.remove(playerId);
        return amount == null ? 0 : Math.max(0, amount);
    }

    public void saveAsync() {
        SaveSnapshot snapshot = snapshot();
        if (!asyncSaveRunning.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                writeSnapshot(snapshot);
            } finally {
                asyncSaveRunning.set(false);
            }
        });
    }

    public void save() {
        writeSnapshot(snapshot());
    }

    private SaveSnapshot snapshot() {
        Map<UUID, ProfileSnapshot> profileCopy = new HashMap<>();
        profiles.forEach((uuid, profile) -> profileCopy.put(uuid, new ProfileSnapshot(
                profile.damageLevel(),
                profile.healthStep(),
                profile.speedLevel(),
                profile.satietyLevel(),
                profile.abilityLevel(),
                profile.lastStatLossAt()
        )));
        return new SaveSnapshot(
                profileCopy,
                new HashMap<>(rewardCooldowns),
                new HashMap<>(pendingShards)
        );
    }

    private void writeSnapshot(SaveSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.profiles().forEach((uuid, profile) -> {
            String base = "players." + uuid + ".";
            yaml.set(base + "damage", profile.damageLevel());
            yaml.set(base + "health-step", profile.healthStep());
            yaml.set(base + "speed", profile.speedLevel());
            yaml.set(base + "satiety", profile.satietyLevel());
            yaml.set(base + "ability", profile.abilityLevel());
            yaml.set(base + "last-stat-loss", profile.lastStatLossAt());
        });
        snapshot.rewardCooldowns().forEach((key, value) ->
                yaml.set("reward-cooldowns." + key, value)
        );
        snapshot.pendingShards().forEach((uuid, amount) ->
                yaml.set("pending-shards." + uuid, amount)
        );

        synchronized (ioLock) {
            try {
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                File temp = new File(plugin.getDataFolder(), "data.yml.tmp");
                yaml.save(temp);
                moveAtomically(temp, file);
            } catch (IOException ex) {
                plugin.getLogger().severe("Не удалось сохранить data.yml: " + ex.getMessage());
            }
        }
    }

    private void moveAtomically(File source, File target) throws IOException {
        try {
            Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record ProfileSnapshot(
            int damageLevel,
            int healthStep,
            int speedLevel,
            int satietyLevel,
            int abilityLevel,
            long lastStatLossAt
    ) {}

    private record SaveSnapshot(
            Map<UUID, ProfileSnapshot> profiles,
            Map<String, Long> rewardCooldowns,
            Map<UUID, Integer> pendingShards
    ) {}
}
