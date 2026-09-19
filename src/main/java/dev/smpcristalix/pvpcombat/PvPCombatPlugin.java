package dev.smpcristalix.pvpcombat;

import dev.smpcristalix.pvpcombat.api.PvPCombatApi;
import dev.smpcristalix.pvpcombat.api.PvPCombatApiImpl;
import dev.smpcristalix.pvpcombat.command.PvPCombatCommand;
import dev.smpcristalix.pvpcombat.config.ConfigValidator;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import dev.smpcristalix.pvpcombat.data.YamlDataStore;
import dev.smpcristalix.pvpcombat.integration.GrimBridge;
import dev.smpcristalix.pvpcombat.listener.ArmorDurabilityListener;
import dev.smpcristalix.pvpcombat.listener.CombatListener;
import dev.smpcristalix.pvpcombat.listener.DeathListener;
import dev.smpcristalix.pvpcombat.listener.MaceListener;
import dev.smpcristalix.pvpcombat.listener.MovementListener;
import dev.smpcristalix.pvpcombat.listener.PlayerLifecycleListener;
import dev.smpcristalix.pvpcombat.listener.ProjectileWeaponListener;
import dev.smpcristalix.pvpcombat.listener.ShardGuiListener;
import dev.smpcristalix.pvpcombat.listener.TotemListener;
import dev.smpcristalix.pvpcombat.service.AbilityService;
import dev.smpcristalix.pvpcombat.service.CombatService;
import dev.smpcristalix.pvpcombat.service.DeathPenaltyService;
import dev.smpcristalix.pvpcombat.service.GuiService;
import dev.smpcristalix.pvpcombat.service.NoticeService;
import dev.smpcristalix.pvpcombat.service.PearlService;
import dev.smpcristalix.pvpcombat.service.RewardService;
import dev.smpcristalix.pvpcombat.service.ScoreboardService;
import dev.smpcristalix.pvpcombat.service.ShardService;
import dev.smpcristalix.pvpcombat.service.StatsService;
import dev.smpcristalix.pvpcombat.service.UpgradeService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Composition root PvPCombat: связывает сервисы, listener'ы и жизненный цикл. */
public final class PvPCombatPlugin extends JavaPlugin {
    public static NamespacedKey PROJECTILE_WEAPON_KEY;
    public static NamespacedKey TROPHY_TOTEM_KEY;

    private PvPCombatSettings settings;
    private YamlDataStore store;
    private StatsService stats;
    private ShardService shards;
    private CombatService combat;
    private PearlService pearls;
    private AbilityService abilities;
    private DeathPenaltyService penalties;
    private RewardService rewards;
    private UpgradeService upgrades;
    private GuiService gui;
    private ScoreboardService scoreboard;
    private NoticeService notices;

    private CombatListener combatListener;
    private MovementListener movementListener;
    private TotemListener totemListener;
    private DeathListener deathListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            ConfigValidator.validate(getConfig());
        } catch (IllegalArgumentException ex) {
            getLogger().severe(ex.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        PROJECTILE_WEAPON_KEY = new NamespacedKey(this, "projectile_weapon");
        TROPHY_TOTEM_KEY = new NamespacedKey(this, "trophy_totem_owner");

        settings = new PvPCombatSettings(getConfig());
        store = new YamlDataStore(this);
        store.load();

        stats = new StatsService(this, store, settings);
        shards = new ShardService(this, settings);
        combat = new CombatService(settings);
        pearls = new PearlService(settings);
        notices = new NoticeService(settings);
        abilities = new AbilityService(this, stats, combat, settings);
        penalties = new DeathPenaltyService(stats, settings);
        rewards = new RewardService(store, shards, settings);
        upgrades = new UpgradeService(stats, shards, settings);
        gui = new GuiService(stats, shards);
        scoreboard = new ScoreboardService(combat, pearls, notices, settings);

        registerListeners();
        registerCommand();
        registerApi();
        new GrimBridge(this, abilities).registerIfAvailable();
        Bukkit.getOnlinePlayers().forEach(stats::apply);
        startSchedulers();

        getLogger().info("PvPCombat 1.0.0 enabled for Paper 1.21.1");
    }

    private void registerListeners() {
        combatListener = new CombatListener(combat, pearls, stats, abilities, notices, settings);
        movementListener = new MovementListener(combat, abilities, notices, settings);
        totemListener = new TotemListener(combat, notices, settings);
        deathListener = new DeathListener(combat, pearls, penalties, rewards, settings);

        var pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(combatListener, this);
        pluginManager.registerEvents(new ProjectileWeaponListener(), this);
        pluginManager.registerEvents(new ArmorDurabilityListener(abilities), this);
        pluginManager.registerEvents(movementListener, this);
        pluginManager.registerEvents(new ShardGuiListener(shards, gui, upgrades), this);
        pluginManager.registerEvents(totemListener, this);
        pluginManager.registerEvents(new MaceListener(), this);
        pluginManager.registerEvents(deathListener, this);
        pluginManager.registerEvents(new PlayerLifecycleListener(
                stats,
                combat,
                pearls,
                scoreboard,
                shards,
                store,
                notices
        ), this);
    }

    private void registerCommand() {
        var command = getCommand("pvpcombat");
        if (command != null) command.setExecutor(new PvPCombatCommand(this, gui, shards, stats));
    }

    private void registerApi() {
        Bukkit.getServicesManager().register(
                PvPCombatApi.class,
                new PvPCombatApiImpl(shards, combat, stats),
                this,
                ServicePriority.Normal
        );
    }

    private void startSchedulers() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            combat.clearExpired();
            for (var player : Bukkit.getOnlinePlayers()) {
                if (combat.inCombat(player)) scoreboard.update(player);
                else scoreboard.clear(player);
            }
        }, 10L, 10L);

        Bukkit.getScheduler().runTaskTimer(this, () ->
                Bukkit.getOnlinePlayers().forEach(totemListener::enforceLimit), 20L, 20L);

        // Snapshot делается на main thread, физическая запись YAML — async и атомарно.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            rewards.cleanupExpired();
            store.saveAsync();
        }, 1200L, 1200L);
    }

    public String reloadPluginConfiguration() {
        reloadConfig();
        try {
            ConfigValidator.validate(getConfig());
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }

        settings = new PvPCombatSettings(getConfig());
        stats.reload(settings);
        shards.reload(settings);
        combat.reload(settings);
        pearls.reload(settings);
        abilities.reload(settings);
        penalties.reload(settings);
        rewards.reload(settings);
        upgrades.reload(settings);
        notices.reload(settings);
        scoreboard.reload(settings);
        combatListener.reload(settings);
        movementListener.reload(settings);
        totemListener.reload(settings);
        deathListener.reload(settings);
        Bukkit.getOnlinePlayers().forEach(stats::apply);
        return null;
    }

    public PvPCombatSettings getSettings() {
        return settings;
    }

    @Override
    public void onDisable() {
        if (store != null) {
            if (rewards != null) rewards.cleanupExpired();
            store.save();
        }
        Bukkit.getServicesManager().unregisterAll(this);
    }
}
