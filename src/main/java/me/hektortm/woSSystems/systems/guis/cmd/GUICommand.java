package me.hektortm.woSSystems.systems.guis.cmd;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.guis.cmd.sub.Open;
import me.hektortm.woSSystems.systems.guis.cmd.sub.PlayerView;
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

public class GUICommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private final DAOHub hub;

    public GUICommand(DAOHub hub) {
        this.hub = hub;

        subCommands.put("open", new Open(hub));
        SubCommand playerView = new PlayerView(hub);
        subCommands.put("playerview", playerView);
        subCommands.put("pv", playerView);
        subCommands.put("help", new HelpSubCommand(subCommands.values(), "guis"));
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            Utils.info(sender, "guis", "error.usage.general");
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);



        if (subCommand != null) {
            if(!(PermissionUtil.hasPermission(sender, subCommand.getPermission()))) return true;
            subCommand.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        } else {
            Utils.info(sender, "guis", "error.usage.general");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
