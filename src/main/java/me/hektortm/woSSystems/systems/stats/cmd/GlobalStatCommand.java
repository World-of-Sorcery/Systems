package me.hektortm.woSSystems.systems.stats.cmd;

import me.hektortm.woSSystems.systems.stats.StatsManager;
import me.hektortm.woSSystems.systems.stats.cmd.sub.globalstats.*;
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

public class GlobalStatCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();
    public final Map<Permissions, String> permCmds = new HashMap<>();
    private final StatsManager manager;

    public GlobalStatCommand(StatsManager manager) {
        this.manager = manager;

        subCommands.put("give", new GlobalGiveCommand(manager));
        subCommands.put("take", new GlobalTakeCommand(manager));
        subCommands.put("set", new GlobalSetCommand(manager));
        subCommands.put("reset", new GlobalResetCommand(manager));
        subCommands.put("view", new GlobalViewCommand(manager));
        subCommands.put("help", new GlobalHelpCommand(this));

        for (SubCommand subCommand : subCommands.values()) {
            permCmds.put(subCommand.getPermission(), subCommand.getName());
        }
    }
    // TODO: GlobalStats Messages
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            Utils.info(sender, "global_stats", "error.usage.general");
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);



        if (subCommand != null) {
            if(!(PermissionUtil.hasPermission(sender, subCommand.getPermission()))) return true;
            subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            Utils.info(sender, "global_stats", "error.usage.general");
        }

        return true;
    }

    public void globalStatsHelp(CommandSender sender) {
        if (PermissionUtil.hasAnyPermission(sender, Permissions.STATS_GLOBAL_GIVE, Permissions.STATS_GLOBAL_TAKE, Permissions.STATS_GLOBAL_SET,
                Permissions.STATS_GLOBAL_RESET, Permissions.STATS_GLOBAL_VIEW)) {
            Utils.info(sender, "global_stats", "help.header");

            if (PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_GLOBAL_GIVE))
                Utils.noPrefix(sender, "global_stats", "help.give");

            if(PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_GLOBAL_TAKE))
                Utils.noPrefix(sender, "global_stats", "help.take");

            if(PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_GLOBAL_SET))
                Utils.noPrefix(sender, "global_stats", "help.set");

            if(PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_GLOBAL_RESET))
                Utils.noPrefix(sender, "global_stats", "help.reset");

            if(PermissionUtil.hasPermissionNoMsg(sender, Permissions.STATS_GLOBAL_VIEW))
                Utils.noPrefix(sender, "global_stats", "help.view");

            Utils.noPrefix(sender, "global_stats", "help.help");
        } else {
            Utils.error(sender, "general", "error.perms");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
