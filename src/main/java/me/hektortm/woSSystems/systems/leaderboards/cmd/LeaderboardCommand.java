package me.hektortm.woSSystems.systems.leaderboards.cmd;

import me.hektortm.woSSystems.database.dao.LeaderboardDAO;
import me.hektortm.woSSystems.systems.leaderboards.LeaderboardActions;
import me.hektortm.woSSystems.systems.leaderboards.LeaderboardLines;
import me.hektortm.woSSystems.utils.HelpSubCommand;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.woSSystems.utils.TabCompletion;
import me.hektortm.woSSystems.utils.model.Leaderboard;
import me.hektortm.wosCore.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /leaderboard lock|unlock|reset|end|show|help <id>}: the realm's leaderboards (built in the portal). */
public class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private static final String LANG = "leaderboards";

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public LeaderboardCommand(LeaderboardDAO boards, LeaderboardActions actions) {
        subCommands.put("lock", new Change(actions, "lock", Permissions.LEADERBOARD_LOCK));
        subCommands.put("unlock", new Change(actions, "unlock", Permissions.LEADERBOARD_UNLOCK));
        subCommands.put("reset", new Change(actions, "reset", Permissions.LEADERBOARD_RESET));
        subCommands.put("end", new Change(actions, "end", Permissions.LEADERBOARD_END));
        subCommands.put("show", new Show(boards));
        subCommands.put("help", new HelpSubCommand(subCommands.values(), LANG));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        SubCommand subCommand = args.length == 0 ? null : subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            Utils.info(sender, LANG, "error.usage.general");
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

    private static final class Change extends SubCommand {
        private final LeaderboardActions actions;
        private final String verb;
        private final Permissions permission;

        Change(LeaderboardActions actions, String verb, Permissions permission) {
            this.actions = actions;
            this.verb = verb;
            this.permission = permission;
        }

        @Override public String getName() { return verb; }
        @Override public Permissions getPermission() { return permission; }
        @Override public List<TabArg> arguments() { return List.of(TabArg.content("leaderboards")); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                Utils.info(sender, LANG, "error.usage." + verb);
                return;
            }
            if (!actions.run(verb, args[0])) {
                Utils.error(sender, LANG, "error.unknown", "%id%", args[0]);
                return;
            }
            // Content runs /leaderboard as the console: no chat line then.
            if (sender instanceof Player) Utils.success(sender, LANG, "done." + verb, "%id%", args[0]);
        }
    }

    private static final class Show extends SubCommand {
        private final LeaderboardDAO boards;

        Show(LeaderboardDAO boards) { this.boards = boards; }

        @Override public String getName() { return "show"; }
        @Override public Permissions getPermission() { return Permissions.LEADERBOARD_SHOW; }
        @Override public List<TabArg> arguments() { return List.of(TabArg.content("leaderboards")); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 1) {
                Utils.info(sender, LANG, "error.usage.show");
                return;
            }
            Leaderboard def = boards.get(args[0]);
            if (def == null) {
                Utils.error(sender, LANG, "error.unknown", "%id%", args[0]);
                return;
            }
            boolean locked = boards.board(def.getId()).locked();
            Utils.info(sender, LANG, locked ? "show.locked" : "show.live", "%id%", def.getId());
            for (String line : LeaderboardLines.lines(def, boards.board(def.getId())).split("\n")) {
                sender.sendMessage(me.hektortm.woSSystems.utils.ColorText.component(line));
            }
        }
    }
}
