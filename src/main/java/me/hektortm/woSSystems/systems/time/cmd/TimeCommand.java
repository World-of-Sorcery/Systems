package me.hektortm.woSSystems.systems.time.cmd;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.time.TimeManager;
import me.hektortm.woSSystems.systems.time.cmd.sub.DateCommand;
import me.hektortm.woSSystems.systems.time.cmd.sub.FreezeCommand;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.LangManager;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class TimeCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final TimeManager manager;
    private final WoSSystems plugin;
    private final LangManager lang;

    public TimeCommand(TimeManager manager, WoSSystems plugin, LangManager lang) {
        this.manager = manager;
        this.plugin = plugin;
        this.lang = lang;

        subCommands.put("date", new DateCommand(manager));
        subCommands.put("freeze", new FreezeCommand(manager));

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        // Check if there are any arguments
        if (args.length == 0) {
            // Handle the case where no arguments are provided (just "/time")
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            showInGameTimeAndActivity(player);
            return true;
        }

        // Handle subcommands
        String subCommandName = args[0];
        SubCommand subCommand = subCommands.get(subCommandName.toLowerCase());

        if (subCommand != null) {
            subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        } else {
            sender.sendMessage("Unknown subcommand. Use '/time' or valid subcommands like '/time date'.");
            return true;
        }
    }

    private void showInGameTimeAndActivity(Player player) {
        int inGameMinutes = manager.getInGameTimeMinutes();

        // Convert in-game minutes to hours and minutes
        int hours = inGameMinutes / 60;
        int minutes = inGameMinutes % 60;
        String time = String.format("%02d:%02d", hours, minutes);

        // Get the current date in the game
        String date = String.format("%s %02d", manager.getMonthName(manager.getInGameMonth()), manager.getInGameDayOfMonth());

        // Send the in-game time, date, and activity to the player
        player.sendMessage("§eIn-Game Time: §f" + time);
        player.sendMessage("§eIn-Game Date: §f" + date);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
