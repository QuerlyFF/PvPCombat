package dev.smpcristalix.pvpcombat.command;

import dev.smpcristalix.pvpcombat.PvPCombatPlugin;
import dev.smpcristalix.pvpcombat.api.event.PlayerStatChangeEvent;
import dev.smpcristalix.pvpcombat.data.PlayerProfile;
import dev.smpcristalix.pvpcombat.data.YamlDataStore;
import dev.smpcristalix.pvpcombat.service.GuiService;
import dev.smpcristalix.pvpcombat.service.ShardService;
import dev.smpcristalix.pvpcombat.service.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Админские команды остаются тонкими: игровая логика живёт в сервисах. */
public final class PvPCombatCommand implements CommandExecutor {
    private final PvPCombatPlugin plugin;
    private final GuiService gui;
    private final ShardService shards;
    private final StatsService stats;
    private final YamlDataStore store;

    public PvPCombatCommand(PvPCombatPlugin plugin, GuiService gui, ShardService shards,
                            StatsService stats, YamlDataStore store) {
        this.plugin = plugin;
        this.gui = gui;
        this.shards = shards;
        this.stats = stats;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) gui.open(player);
            else sender.sendMessage("Использование: /pvpcombat stats <игрок>");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats" -> showStats(sender, args);
            case "give" -> giveShards(sender, args);
            case "set" -> setStat(sender, args);
            case "reload" -> reload(sender);
            default -> false;
        };
    }

    private boolean showStats(CommandSender sender, String[] args) {
        Player target = args.length >= 2
                ? Bukkit.getPlayerExact(args[1])
                : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage("§cИгрок не найден.");
            return true;
        }

        PlayerProfile profile = stats.profile(target.getUniqueId());
        sender.sendMessage("§dPvPCombat §7— §f" + target.getName());
        sender.sendMessage("§7Урон: §f" + profile.damageLevel()
                + " §7| Здоровье: §f"
                + String.format(Locale.ROOT, "%.1f", stats.healthHearts(profile)) + "❤");
        sender.sendMessage("§7Скорость: §f" + profile.speedLevel()
                + " §7| Сытость: §f" + profile.satietyLevel()
                + " §7| Умения: §f" + profile.abilityLevel());
        return true;
    }

    private boolean giveShards(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage("§c/pvpcombat give <игрок> <количество>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден.");
            return true;
        }

        Integer amount = parseInteger(args[2]);
        if (amount == null || amount <= 0) {
            sender.sendMessage("§cКоличество должно быть целым числом больше 0.");
            return true;
        }

        shards.give(target, amount);
        sender.sendMessage("§aВыдано " + amount + " Осколков игроку " + target.getName() + ".");
        return true;
    }

    private boolean setStat(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 4) {
            sender.sendMessage("§c/pvpcombat set <игрок> <damage|health|speed|satiety|ability> <ступень>");
            sender.sendMessage("§7Для health: 0 = базовые "
                    + formatHearts(plugin.getSettings().baseHealthHearts())
                    + " сердец, отрицательные значения — штрафная зона.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден.");
            return true;
        }

        Integer value = parseInteger(args[3]);
        if (value == null) {
            sender.sendMessage("§cСтупень должна быть целым числом.");
            return true;
        }

        PlayerProfile profile = stats.profile(target.getUniqueId());
        PlayerStatChangeEvent.Stat stat;
        int oldValue;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "damage" -> {
                stat = PlayerStatChangeEvent.Stat.DAMAGE;
                oldValue = profile.damageLevel();
                profile.damageLevel(value);
            }
            case "health" -> {
                stat = PlayerStatChangeEvent.Stat.HEALTH;
                oldValue = profile.healthStep();
                profile.healthStep(value);
            }
            case "speed" -> {
                stat = PlayerStatChangeEvent.Stat.SPEED;
                oldValue = profile.speedLevel();
                profile.speedLevel(value);
            }
            case "satiety" -> {
                stat = PlayerStatChangeEvent.Stat.SATIETY;
                oldValue = profile.satietyLevel();
                profile.satietyLevel(value);
            }
            case "ability" -> {
                stat = PlayerStatChangeEvent.Stat.ABILITY;
                oldValue = profile.abilityLevel();
                profile.abilityLevel(value);
            }
            default -> {
                sender.sendMessage("§cНеизвестная характеристика.");
                return true;
            }
        }

        stats.clamp(profile);
        stats.apply(target);
        int newValue = switch (stat) {
            case DAMAGE -> profile.damageLevel();
            case HEALTH -> profile.healthStep();
            case SPEED -> profile.speedLevel();
            case SATIETY -> profile.satietyLevel();
            case ABILITY -> profile.abilityLevel();
        };
        if (oldValue != newValue) {
            store.saveAsync();
            Bukkit.getPluginManager().callEvent(new PlayerStatChangeEvent(
                    target,
                    stat,
                    oldValue,
                    newValue,
                    PlayerStatChangeEvent.Reason.ADMIN
            ));
        }
        sender.sendMessage("§aХарактеристика изменена.");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!requireAdmin(sender)) return true;
        String error = plugin.reloadPluginConfiguration();
        if (error == null) sender.sendMessage("§aPvPCombat config.yml перезагружен.");
        else sender.sendMessage("§cКонфиг не применён: " + error);
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("pvpcombat.admin")) return true;
        sender.sendMessage("§cНет права pvpcombat.admin.");
        return false;
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatHearts(double hearts) {
        return hearts == Math.rint(hearts)
                ? Integer.toString((int) hearts)
                : String.format(Locale.ROOT, "%.1f", hearts);
    }
}
