package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.systems.quests.model.QuestNode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * What the player's runs show in the world right now: over each waiting click
 * step a hologram and a pointer, the displays a sequence left standing, and a
 * waypoint towards a Wait for node's place. Drawn once a second and after
 * every change; whatever is no longer wanted goes.
 */
final class QuestScene {
    private QuestScene() {}

    static void draw(QuestEngine e, Player p, Collection<QuestRun> runs) {
        Set<String> keep = new HashSet<>();
        for (QuestRun run : runs) {
            QuestDef def = e.defOf(run);
            if (def == null || !run.running()) continue;
            for (String id : run.active()) {
                QuestNode n = def.node(id);
                if (n == null) continue;
                if (n.is("click")) click(e, p, run, n, keep);
                if (n.is("wait_for")) waypoint(e, p, n);
            }
            displays(e, p, run, def, keep);
        }
        e.visuals().retain(p, keep);
    }

    private static void click(QuestEngine e, Player p, QuestRun run, QuestNode n, Set<String> keep) {
        boolean atAnchor = n.flag("anchor", true) && run.anchor() != null;
        Location target = atAnchor ? QuestPlaces.ofKey(run.anchor()) : e.places().nearest(p, n.str("target"));
        if (target == null) return;
        boolean npc = atAnchor && run.anchor().startsWith("npc:");
        String key = run.questId() + ":" + n.id() + ":holo";
        Location over = npc ? target.clone().add(0, 2.4, 0) : target.clone().add(0.5, 1.7, 0.5);
        e.visuals().hologram(p, key, over, "&e" + QuestRules.hologram(n, run.count(n.id())));
        keep.add(key);
        if (n.flag("pointer", true)) e.visuals().pointer(p, npc ? target.clone().add(-0.5, 0.8, -0.5) : target);
    }

    private static void waypoint(QuestEngine e, Player p, QuestNode n) {
        String interaction = Json.str(n.object("waypoint"), "interaction", "");
        if (interaction.isEmpty()) return;
        Location to = e.places().nearest(p, interaction);
        if (to != null) e.visuals().waypoint(p, to);
    }

    private static void displays(QuestEngine e, Player p, QuestRun run, QuestDef def, Set<String> keep) {
        Location anchor = QuestPlaces.ofKey(run.anchor());
        if (anchor == null) return;
        for (String id : run.displays()) {
            QuestNode n = def.node(id);
            JsonObject settings = n == null ? null : n.object("display");
            if (settings == null || settings.entrySet().isEmpty()) continue;
            String key = run.questId() + ":" + id + ":display";
            e.visuals().display(p, key, anchor, settings);
            keep.add(key);
        }
    }

    /** The line a waiting node puts in the tracker; null for nodes that show none. */
    @Nullable
    static String trackerLine(QuestEngine e, QuestRun run, QuestNode n) {
        switch (n.kind()) {
            case "wait_for": {
                JsonObject ev = n.object("event");
                long target = QuestEvents.target(Json.str(ev, "source", ""), Json.str(ev, "entry", ""),
                        QuestEngine.params(ev), n.integer("count", 1));
                int done = (int) Math.min(run.count(n.id()), target);
                String own = QuestRules.objective(n, done, (int) target);
                if (!own.isEmpty()) return own;
                return QuestRules.pretty(Json.str(ev, "source", "")) + " " + QuestRules.pretty(Json.str(ev, "entry", ""))
                        + (target > 1 ? " (" + done + "/" + target + ")" : "");
            }
            case "click":
                return QuestRules.hologram(n, run.count(n.id()));
            case "wait":
                return "Wait…";
            case "run_sequence":
                return "Do: " + e.title(n.str("sequence"));
            default:
                return null;
        }
    }
}
