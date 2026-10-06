package me.hektortm.woSSystems.systems.stats.cmd;

import me.hektortm.woSSystems.systems.stats.StatsManager;
import me.hektortm.woSSystems.systems.stats.cmd.sub.stats.*;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final StatsManager manager;

    public StatsCommand(StatsManager manager) {
        this.manager = manager;

        subCommands.put("give", new GiveCommand(manager));
        subCommands.put("take", new TakeCommand(manager));
        subCommands.put("set", new SetCommand(manager));
        subCommands.put("reset", new ResetCommand(manager));
        subCommands.put("view", new ViewCommand(manager));
        subCommands.put("help", new HelpCommand(this));

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            Utils.info(sender, "stats", "error.usage.general", "%type%", "stats");
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand != null) {
            if(!(PermissionUtil.hasPermission(sender, subCommand.getPermission()))) return true;
            subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            Utils.info(sender, "stats", "error.usage.general", "%type%", "stats");
        }


        return true;
    }


    public void statsHelp(CommandSender sender) {
        if (PermissionUtil.hasAnyPermission(sender, Permissions.STATS_GIVE, Permissions.STATS_TAKE, Permissions.STATS_SET, Permissions.STATS_RESET, Permissions.STATS_VIEW)) {
            Utils.info(sender, "stats", "help.header", "%type%", "Stats");

            if (PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_GIVE))
                Utils.noPrefix(sender, "stats", "help.give", "%type%", "stats");

            if(PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_TAKE))
                Utils.noPrefix(sender, "stats", "help.take", "%type%", "stats");

            if(PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_SET))
                Utils.noPrefix(sender, "stats", "help.set", "%type%", "stats");

            if(PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_RESET))
                Utils.noPrefix(sender, "stats", "help.reset", "%type%", "stats");

            if(PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_VIEW))
                Utils.noPrefix(sender, "stats", "help.view", "%type%", "stats");

            Utils.noPrefix(sender, "stats", "help.help", "%type%", "stats");
        } else {
            Utils.error(sender, "general", "error.perms");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
