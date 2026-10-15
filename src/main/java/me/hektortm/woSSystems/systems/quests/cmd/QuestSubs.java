package me.hektortm.woSSystems.systems.quests.cmd;

import me.hektortm.woSSystems.systems.quests.QuestEngine;
import me.hektortm.woSSystems.systems.quests.QuestRun;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** The sub-commands of {@code /quest}. */
final class QuestSubs {
    private QuestSubs() {}

    /** {@code /quest start <player> <id>}: staff and content ({@code quest start @p <id>} runs as the console). */
    static final class Start extends SubCommand {
        private final QuestEngine engine;

        Start(QuestEngine engine) { this.engine = engine; }

        @Override public String getName() { return "start"; }
        @Override public Permissions getPermission() { return Permissions.QUEST_ADMIN_START; }
        @Override public List<TabArg> arguments() { return List.of(TabArg.PLAYER, TabArg.content("quests")); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                Utils.info(sender, "quests", "error.usage.start");
                return;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                Utils.error(sender, "quests", "error.offline", "%player%", args[0]);
                return;
            }
            QuestEngine.Start result = engine.start(target, args[1], null);
            if (result == QuestEngine.Start.STARTED) {
                if (sender instanceof Player) Utils.info(sender, "quests", "started-for", "%quest%", engine.title(args[1]), "%player%", target.getName());
                return;
            }
            // Told to the player too: the console runs this for content (an NPC, a GUI button).
            String key = "error.start." + result.name().toLowerCase(Locale.ROOT);
            Utils.error(target, "quests", key, "%quest%", engine.title(args[1]));
            if (sender != target && sender instanceof Player) Utils.error(sender, "quests", key, "%quest%", engine.title(args[1]));
        }
    }

    /** {@code /quest cancel}: stops the running sequence (its items are given back). */
    static final class Cancel extends SubCommand {
        private final QuestEngine engine;

        Cancel(QuestEngine engine) { this.engine = engine; }

        @Override public String getName() { return "cancel"; }
        @Override public Permissions getPermission() { return null; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof Player p)) {
                Utils.error(sender, "quests", "error.player-only");
                return;
            }
            QuestRun seq = engine.runningSequence(p);
            if (seq == null || !engine.cancel(p, seq.questId())) {
                Utils.error(p, "quests", "error.no-sequence");
                return;
            }
            Utils.info(p, "quests", "sequence.cancelled", "%quest%", engine.title(seq.questId()));
        }
    }

    /** {@code /quest abandon <id>}: gives up a repeatable quest (it can be started again). */
    static final class Abandon extends SubCommand {
        private final QuestEngine engine;

        Abandon(QuestEngine engine) { this.engine = engine; }

        @Override public String getName() { return "abandon"; }
        @Override public Permissions getPermission() { return null; }
        @Override public List<TabArg> arguments() { return List.of(TabArg.content("quests")); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof Player p)) {
                Utils.error(sender, "quests", "error.player-only");
                return;
            }
            if (args.length < 1) {
                Utils.info(p, "quests", "error.usage.abandon");
                return;
            }
            QuestRun run = engine.runsOf(p).get(args[0]);
            var def = engine.published().stream().filter(d -> d.id().equals(args[0])).findFirst().orElse(null);
            if (run == null || !run.running()) {
                Utils.error(p, "quests", "error.not-active", "%quest%", engine.title(args[0]));
                return;
            }
            if (def != null && !def.sequence() && !def.repeatable()) {
                Utils.error(p, "quests", "error.cannot-abandon", "%quest%", engine.title(args[0]));
                return;
            }
            engine.cancel(p, args[0]);
            Utils.info(p, "quests", "abandoned", "%quest%", engine.title(args[0]));
        }
    }

    /** {@code /quest status}: the player's running quests and sequences. */
    static final class Status extends SubCommand {
        private final QuestEngine engine;

        Status(QuestEngine engine) { this.engine = engine; }

        @Override public String getName() { return "status"; }
        @Override public Permissions getPermission() { return null; }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof Player p)) {
                Utils.error(sender, "quests", "error.player-only");
                return;
            }
            var runs = engine.runsOf(p).values().stream().filter(QuestRun::running).toList();
            if (runs.isEmpty()) {
                Utils.info(p, "quests", "status.none");
                return;
            }
            Utils.info(p, "quests", "status.header");
            for (QuestRun r : runs) {
                Utils.info(p, "quests", r.sequence() ? "status.sequence" : "status.quest", "%quest%", engine.title(r.questId()));
            }
        }
    }

    /** {@code /quest reset <player> <id>}: staff — stops the run and lets the quest be done again. */
    static final class Reset extends SubCommand {
        private final QuestEngine engine;

        Reset(QuestEngine engine) { this.engine = engine; }

        @Override public String getName() { return "reset"; }
        @Override public Permissions getPermission() { return Permissions.QUEST_ADMIN_START; }
        @Override public List<TabArg> arguments() { return List.of(TabArg.PLAYER, TabArg.content("quests")); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                Utils.info(sender, "quests", "error.usage.reset");
                return;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                Utils.error(sender, "quests", "error.offline", "%player%", args[0]);
                return;
            }
            engine.reset(target, args[1]);
            Utils.info(sender, "quests", "reset", "%quest%", engine.title(args[1]), "%player%", target.getName());
        }
    }
}
