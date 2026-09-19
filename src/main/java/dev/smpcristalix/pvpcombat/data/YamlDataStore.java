package dev.smpcristalix.pvpcombat.data;

import dev.smpcristalix.pvpcombat.service.RewardService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** UUID-хранилище прогресса и антифарм-cooldown'ов. */
public final class YamlDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
    private final Map<String, Long> rewardCooldowns = new HashMap<>();

    public YamlDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        profiles.clear();
        rewardCooldowns.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadProfiles(yaml);
        loadRewardCooldowns(yaml);
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

            // Миграция с первой версии: killerUUID.victimUUID превращался YAML'ом
            // в два вложенных уровня и раньше после рестарта не загружался.
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

    public PlayerProfile profile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, ignored -> new PlayerProfile());
    }

    public long rewardCooldown(String key) {
        return rewardCooldowns.getOrDefault(key, 0L);
    }

    public void rewardCooldown(String key, long value) {
        rewardCooldowns.put(key, value);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        profiles.forEach((uuid, profile) -> {
            String base = "players." + uuid + ".";
            yaml.set(base + "damage", profile.damageLevel());
            yaml.set(base + "health-step", profile.healthStep());
            yaml.set(base + "speed", profile.speedLevel());
            yaml.set(base + "satiety", profile.satietyLevel());
            yaml.set(base + "ability", profile.abilityLevel());
            yaml.set(base + "last-stat-loss", profile.lastStatLossAt());
        });

        // Новый separator не содержит '.', поэтому Bukkit YAML не разбивает ключ пары на секции.
        rewardCooldowns.forEach((key, value) -> yaml.set("reward-cooldowns." + key, value));

        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить data.yml: " + ex.getMessage());
        }
    }
}
