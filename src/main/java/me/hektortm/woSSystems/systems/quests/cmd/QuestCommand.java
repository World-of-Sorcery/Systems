package me.hektortm.woSSystems.systems.quests.cmd;

import me.hektortm.woSSystems.systems.quests.QuestEngine;
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

/** {@code /quest start|cancel|abandon|status|reset|help}: quests and sequences in game. */
public class QuestCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public QuestCommand(QuestEngine engine) {
        subCommands.put("status", new QuestSubs.Status(engine));
        subCommands.put("cancel", new QuestSubs.Cancel(engine));
        subCommands.put("abandon", new QuestSubs.Abandon(engine));
        subCommands.put("start", new QuestSubs.Start(engine));
        subCommands.put("reset", new QuestSubs.Reset(engine));
        subCommands.put("help", new HelpSubCommand(subCommands.values(), "quests"));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        SubCommand sub = args.length == 0 ? null : subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            Utils.info(sender, "quests", "error.usage.general");
            return true;
        }
        if (!PermissionUtil.hasPermission(sender, sub.getPermission())) return true;
        sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(subCommands, sender, args);
    }
}
