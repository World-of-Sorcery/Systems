package me.hektortm.woSSystems.systems.loottables.cmd;


import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.loottables.LoottableManager;
import me.hektortm.woSSystems.systems.loottables.cmd.sub.Chest;
import me.hektortm.woSSystems.systems.loottables.cmd.sub.Trigger;
import me.hektortm.woSSystems.utils.HelpSubCommand;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public class LoottableCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final DAOHub hub;
    private final LoottableManager loottableManager;

    public LoottableCommand(DAOHub hub, LoottableManager loottableManager) {
        this.hub = hub;
        this.loottableManager = loottableManager;

        subCommands.put("trigger", new Trigger(loottableManager));
        subCommands.put("chest", new Chest(hub));
        subCommands.put("help", new HelpSubCommand(subCommands.values(), "loottables"));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            Utils.info(sender, "loottables", "error.usage.loottable");
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand != null) {
            if(!(PermissionUtil.hasPermission(sender, subCommand.getPermission()))) return true;
            subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            Utils.info(sender, "loottables", "error.usage.loottable");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
