package me.hektortm.woSSystems.systems.cscreens.cmd;

import me.hektortm.woSSystems.systems.cscreens.CscreenManager;
import me.hektortm.woSSystems.systems.cscreens.cmd.sub.Close;
import me.hektortm.woSSystems.systems.cscreens.cmd.sub.Open;
import me.hektortm.woSSystems.utils.HelpSubCommand;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabCompletion;
import me.hektortm.wosCore.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /cscreen open|close|help}: custom screens (built in the portal) for players. */
public class CscreenCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public CscreenCommand(CscreenManager manager) {
        subCommands.put("open", new Open(manager));
        subCommands.put("close", new Close(manager));
        subCommands.put("help", new HelpSubCommand(subCommands.values(), "cscreens"));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        SubCommand subCommand = args.length == 0 ? null : subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            Utils.info(sender, "cscreens", "error.usage.general");
            return true;
        }
        if (!PermissionUtil.hasPermission(sender, subCommand.getPermission())) return true;
        subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
