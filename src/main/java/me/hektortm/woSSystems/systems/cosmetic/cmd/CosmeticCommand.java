package me.hektortm.woSSystems.systems.cosmetic.cmd;

import me.hektortm.woSSystems.systems.cosmetic.CosmeticManager;

import me.hektortm.woSSystems.systems.cosmetic.cmd.sub.Give;

import me.hektortm.woSSystems.systems.cosmetic.cmd.sub.Help;
import me.hektortm.woSSystems.systems.cosmetic.cmd.sub.Set;
import me.hektortm.woSSystems.systems.cosmetic.cmd.sub.Take;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class CosmeticCommand implements CommandExecutor, TabCompleter {
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final CosmeticManager manager;
    private final DAOHub hub;

    public CosmeticCommand(CosmeticManager manager, DAOHub hub) {
        this.manager = manager;
        this.hub = hub;

        subCommands.put("give", new Give(hub));
        subCommands.put("take", new Take(hub));
        subCommands.put("help", new Help(this));
        subCommands.put("set", new Set(hub));


    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {

        String subCommandName;
        SubCommand subCommand;
        if (!(sender instanceof Player)) {
            subCommandName = args[0].toLowerCase();
            subCommand = subCommands.get(subCommandName);
            subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {

            if (args.length == 0) {
                manager.open((Player) sender);
                return true;
            }
            subCommandName = args[0].toLowerCase();
            subCommand = subCommands.get(subCommandName);

            if (subCommand != null) {

                if(subCommand.getName() != "help" && !(PermissionUtil.hasPermissionNoMsg(sender, subCommand.getPermission()))) {
                    manager.open((Player) sender);
                    return true;
                }
                subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            } else {
                manager.open((Player) sender);
            }
        }


        return true;
    }


    public void cosmeticHelp(CommandSender s) {
        if (PermissionUtil.hasAnyPermission(s, Permissions.COSMETIC_GIVE, Permissions.COSMETIC_TAKE,
                Permissions.COSMETIC_SET)) {
            Utils.info(s, "cosmetics", "help.header");

            if (PermissionUtil.hasPermissionNoMsg(s, Permissions.COSMETIC_GIVE))
                Utils.noPrefix(s, "cosmetics", "help.give");

            if(PermissionUtil.hasPermissionNoMsg(s, Permissions.COSMETIC_TAKE))
                Utils.noPrefix(s, "cosmetics", "help.take");

            if(PermissionUtil.hasPermissionNoMsg(s, Permissions.COSMETIC_SET))
                Utils.noPrefix(s, "cosmetics", "help.set");

            Utils.noPrefix(s, "cosmetics", "help.help");
        } else {
            Utils.error(s, "general", "error.perms");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
