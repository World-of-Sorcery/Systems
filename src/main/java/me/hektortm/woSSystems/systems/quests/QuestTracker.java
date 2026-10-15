package me.hektortm.woSSystems.systems.quests;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.systems.quests.model.QuestNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The on-screen tracker (spec §4.8): for now a sidebar, one per player, with
 * each running quest / sequence and what it waits for. Kept behind this class
 * so the final style (boss bar, action bar …) can replace it without touching
 * the engine. Without anything to show, the player gets the main scoreboard back.
 */
final class QuestTracker {

    private static final int MAX_LINES = 15;
    private static final int MAX_WIDTH = 40;

    private final QuestEngine e;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    QuestTracker(QuestEngine engine) {
        this.e = engine;
    }

    void update(Player p, Collection<QuestRun> runs) {
        List<String> lines = lines(runs);
        if (lines.isEmpty()) {
            clear(p);
            return;
        }
        Scoreboard sb = boards.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective old = sb.getObjective("wosquests");
        if (old != null) old.unregister();
        Objective o = sb.registerNewObjective("wosquests", Criteria.DUMMY, Component.text("Quests", NamedTextColor.GOLD));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank());
        int score = lines.size();
        for (int i = 0; i < lines.size() && i < MAX_LINES; i++) {
            // A hidden colour code per line keeps two equal lines apart.
            String entry = shorten(ChatColor.translateAlternateColorCodes('&', lines.get(i))) + ChatColor.values()[i % 16] + ChatColor.RESET;
            o.getScore(entry).setScore(score--);
        }
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
    }

    private List<String> lines(Collection<QuestRun> runs) {
        List<QuestRun> ordered = new ArrayList<>(runs);
        ordered.sort((a, b) -> Boolean.compare(!a.sequence(), !b.sequence()));
        List<String> out = new ArrayList<>();
        for (QuestRun run : ordered) {
            QuestDef def = e.defOf(run);
            if (def == null || !run.running()) continue;
            out.add("&6" + e.title(run.questId()) + (def.sequence() && def.scoreOn() ? " &7(" + run.score() + ")" : ""));
            for (String id : run.active()) {
                QuestNode n = def.node(id);
                String line = n == null ? null : QuestScene.trackerLine(e, run, n);
                if (line != null) out.add("&f• " + line);
            }
        }
        return out;
    }

    private static String shorten(String s) {
        return s.length() <= MAX_WIDTH ? s : s.substring(0, MAX_WIDTH - 1) + "…";
    }

    void clear(Player p) {
        Scoreboard sb = boards.remove(p.getUniqueId());
        if (sb != null && p.getScoreboard() == sb) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
