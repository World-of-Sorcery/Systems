package me.hektortm.woSSystems.systems.cooldowns.cmd;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.cooldowns.cmd.sub.Give;
import me.hektortm.woSSystems.systems.cooldowns.cmd.sub.Help;
import me.hektortm.woSSystems.systems.cooldowns.cmd.sub.Remove;
import me.hektortm.woSSystems.systems.cooldowns.cmd.sub.View;
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

public class CooldownCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();
    public Map<Permissions, String> permCmds = new HashMap<>();
    private final DAOHub hub;

    public CooldownCommand(DAOHub hub) {
        this.hub = hub;

        subCommands.put("give", new Give(hub));
        subCommands.put("remove", new Remove(hub));
        subCommands.put("view", new View(hub));


        for (SubCommand subCommand : subCommands.values()) {
            permCmds.put(subCommand.getPermission(), subCommand.getName());
        }

        subCommands.put("help", new Help(this));


    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            Utils.info(sender, "cooldowns", "info.usage.general");
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand != null) {
            if(!(PermissionUtil.hasPermission(sender, subCommand.getPermission()))) return true;
            subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            Utils.info(sender, "cooldowns", "info.usage.general");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
