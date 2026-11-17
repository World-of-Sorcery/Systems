package me.hektortm.woSSystems.systems.timers.cmd;

import me.hektortm.woSSystems.database.dao.TimerDAO;
import me.hektortm.woSSystems.systems.timers.TimerActions;
import me.hektortm.woSSystems.utils.HelpSubCommand;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.woSSystems.utils.TabCompletion;
import me.hektortm.woSSystems.utils.TimeFormat;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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

/** {@code /timer start|stop|cancel|resetbest|view|help <player> <id>}: timers (built in the portal) for players. */
public class TimerCommand implements CommandExecutor, TabCompleter {
    private static final String LANG = "timers";

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public TimerCommand(TimerDAO timers) {
        subCommands.put("start", new Run(timers, "start", Permissions.TIMERS_START));
        subCommands.put("stop", new Run(timers, "stop", Permissions.TIMERS_STOP));
        subCommands.put("cancel", new Run(timers, "cancel", Permissions.TIMERS_CANCEL));
        subCommands.put("resetbest", new ResetBest(timers));
        subCommands.put("view", new View(timers));
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

    /** start / stop / cancel for an online player, as the {@code timer} action does it. */
    private static final class Run extends SubCommand {
        private final TimerDAO timers;
        private final String verb;
        private final Permissions permission;

        Run(TimerDAO timers, String verb, Permissions permission) {
            this.timers = timers;
            this.verb = verb;
            this.permission = permission;
        }

        @Override public String getName() { return verb; }
        @Override public Permissions getPermission() { return permission; }
        @Override public List<TabArg> arguments() { return List.of(TabArg.PLAYER, TabArg.content("timers")); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                Utils.info(sender, LANG, "error.usage." + verb);
                return;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                Utils.error(sender, LANG, "error.offline", "%player%", args[0]);
                return;
            }
            if (timers.getTimer(args[1]) == null) {
                Utils.error(sender, LANG, "error.unknown", "%id%", args[1]);
                return;
            }
            TimerDAO.StopResult result = TimerActions.run(timers, target, verb, args[1], null);
            // Content runs /timer as the console: no chat line for every start.
            if (!(sender instanceof Player)) return;
            if (verb.equals("stop") && result == null) {
                Utils.error(sender, LANG, "error.not-running", "%player%", target.getName(), "%id%", args[1]);
            } else if (verb.equals("stop")) {
                Utils.success(sender, LANG, "stopped", "%player%", target.getName(), "%id%", args[1], "%time%", TimeFormat.millis(result.millis()));
            } else {
                Utils.success(sender, LANG, verb.equals("start") ? "started" : "cancelled", "%player%", target.getName(), "%id%", args[1]);
            }
        }
    }

    private static final class ResetBest extends SubCommand {
        private final TimerDAO timers;

        ResetBest(TimerDAO timers) { this.timers = timers; }

        @Override public String getName() { return "resetbest"; }
        @Override public Permissions getPermission() { return Permissions.TIMERS_RESETBEST; }
        @Override public List<TabArg> arguments() { return List.of(TabArg.PLAYER, TabArg.content("timers")); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                Utils.info(sender, LANG, "error.usage.resetbest");
                return;
            }
            @SuppressWarnings("deprecation") // by name, like the other staff commands
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            timers.resetBest(target, args[1]);
            Utils.success(sender, LANG, "best-reset", "%player%", args[0], "%id%", args[1]);
        }
    }

    private static final class View extends SubCommand {
        private final TimerDAO timers;

        View(TimerDAO timers) { this.timers = timers; }

        @Override public String getName() { return "view"; }
        @Override public Permissions getPermission() { return Permissions.TIMERS_VIEW; }
        @Override public List<TabArg> arguments() { return List.of(TabArg.PLAYER, TabArg.content("timers")); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                Utils.info(sender, LANG, "error.usage.view");
                return;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                Utils.error(sender, LANG, "error.offline", "%player%", args[0]);
                return;
            }
            Long running = timers.runningMillis(target, args[1]);
            Long best = timers.best(target, args[1]);
            Utils.info(sender, LANG, "view", "%player%", target.getName(), "%id%", args[1],
                    "%running%", running == null ? "-" : TimeFormat.millis(running),
                    "%best%", best == null ? "-" : TimeFormat.millis(best));
        }
    }
}
