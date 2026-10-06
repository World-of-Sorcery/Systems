package me.hektortm.woSSystems.systems.unlockables.cmd;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.unlockables.cmd.sub.*;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class UnlockableCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public UnlockableCommand(DAOHub hub) {
        register(new GiveCommand(hub));
        register(new TakeCommand(hub));
        register(new HelpCommand());
    }

    private void register(SubCommand cmd) {
        subCommands.put(cmd.getName(), cmd);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        String subCommandName = args.length == 0 ? "help" : args[0].toLowerCase();
        SubCommand subCommand = subCommands.getOrDefault(subCommandName, subCommands.get("help"));

        if (subCommand.requiresPermission() && !PermissionUtil.hasPermission(sender, subCommand.getPermission())) return true;
        subCommand.execute(sender, args.length == 0 ? new String[0] : java.util.Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
