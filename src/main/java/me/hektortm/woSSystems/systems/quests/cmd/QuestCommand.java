package me.hektortm.woSSystems.systems.quests.cmd;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.quests.QuestManager;
import me.hektortm.woSSystems.systems.quests.cmd.sub.Help;
import me.hektortm.woSSystems.systems.quests.cmd.sub.Start;
import me.hektortm.woSSystems.systems.quests.cmd.sub.Status;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class QuestCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public QuestCommand(QuestManager questManager, DAOHub hub) {
        register(new Start(questManager));
        register(new Status(questManager, hub));
        register(new Help());
    }

    private void register(SubCommand cmd) {
        subCommands.put(cmd.getName(), cmd);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String subName = args.length == 0 ? "help" : args[0].toLowerCase();
        SubCommand sub = subCommands.getOrDefault(subName, subCommands.get("help"));

        if (sub.requiresPermission() && !PermissionUtil.hasPermission(sender, sub.getPermission())) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        sub.execute(sender, args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
