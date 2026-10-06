package me.hektortm.woSSystems.systems.interactions.cmd;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.interactions.cmd.sub.*;
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

public class InteractionCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final DAOHub hub;

    public InteractionCommand(DAOHub hub) {
        this.hub = hub;
        subCommands.put("trigger", new Trigger(hub));
        subCommands.put("bind", new Bind(hub));
        subCommands.put("unbind", new Unbind(hub));
        subCommands.put("npcbind", new NPCBind(hub));
        subCommands.put("npcunbind", new NPCUnbind(hub));
        subCommands.put("info", new Info(hub));
        subCommands.put("help", new HelpSubCommand(subCommands.values(), "interactions"));

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            Utils.info(sender, "interactions", "info.usage.interaction");
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand != null) {
            if(PermissionUtil.hasPermission(sender, subCommand.getPermission())) {
                subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            } else {
                return true;
            }
        } else {
            Utils.info(sender, "interactions", "info.usage.interaction");
        }


        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
