package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.systems.quests.model.QuestNode;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.woSSystems.utils.Operations;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.hektortm.woSSystems.player.ApiWriter.body;
import static me.hektortm.woSSystems.player.ApiWriter.seg;

/**
 * Runs quests and sequences (spec 2026-10-15-quest-builder-sequences-design §4).
 * A run walks its published version's graph: nodes that don't wait run at
 * once, nodes that wait (Wait for, Click, Wait, Join, Run sequence) stay
 * active until an event, a click or the clock moves them on. Runs are saved
 * per player through the API and resumed after a logout.
 */
public final class QuestEngine {

    /** Why a start was refused (or that it worked). */
    public enum Start { STARTED, UNKNOWN, ACTIVE, DONE, COOLDOWN, CONDITIONS, BUSY }

    static final long TICK_MS = 250;

    private final WoSSystems plugin;
    private final DAOHub hub;
    private final QuestStore store;
    private final ApiWriter writer;
    private final QuestPlaces places;
    private final QuestVisuals visuals;
    private final QuestClicks clicks;
    private final QuestTracker tracker;
    /** Player → quest / sequence id → run. */
    private final Map<UUID, Map<String, QuestRun>> runs = new ConcurrentHashMap<>();
    /** Runs stepping on from a checkpoint right now: failing again before the player does anything fails for real (no endless loop). */
    private final Set<QuestRun> rewinding = Collections.newSetFromMap(new IdentityHashMap<>());

    public QuestEngine(WoSSystems plugin, DAOHub hub) {
        this.plugin = plugin;
        this.hub = hub;
        this.store = hub.getQuestStore();
        this.writer = store.writer();
        this.places = new QuestPlaces(hub);
        this.visuals = new QuestVisuals(plugin, hub);
        this.clicks = new QuestClicks(this);
        this.tracker = new QuestTracker(this);
        QuestHooks.install(plugin, this);
    }

    WoSSystems plugin() { return plugin; }
    DAOHub hub() { return hub; }
    QuestStore store() { return store; }
    QuestPlaces places() { return places; }
    QuestVisuals visuals() { return visuals; }
    public QuestClicks clicks() { return clicks; }

    /** Starts the clock (waits, time limits) and the visuals' refresh. */
    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, TICK_MS / 50);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) tracker.clear(p);
        visuals.shutdown();
        QuestHooks.install(plugin, null);
    }

    // ── runs ──

    public Map<String, QuestRun> runsOf(Player p) {
        return runs.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
    }

    @Nullable
    public QuestRun runningSequence(Player p) {
        for (QuestRun r : runsOf(p).values()) if (r.sequence() && r.running()) return r;
        return null;
    }

    @Nullable
    QuestDef defOf(QuestRun r) {
        return store.version(r.questId(), r.version());
    }

    public String title(String id) {
        return store.title(id);
    }

    /** The player's saved runs, after login: older versions are fetched off the main thread. */
    public void load(Player p) {
        PlayerSession s = hub.getSessions().get(p.getUniqueId());
        if (s == null) return;
        List<QuestRun> saved = new ArrayList<>();
        for (JsonObject row : s.quests.values()) {
            QuestRun r = QuestRun.fromJson(Json.object(row, "run"));
            if (r != null && r.running()) saved.add(r);
        }
        if (saved.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (QuestRun r : saved) store.fetch(r.questId(), r.version());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                Map<String, QuestRun> mine = runsOf(p);
                for (QuestRun r : saved) {
                    if (defOf(r) != null) mine.put(r.questId(), r);
                    else plugin.getLogger().warning("[Quests] " + p.getName() + ": " + r.questId() + " v" + r.version() + " is gone, its run is dropped");
                }
                refresh(p);
            });
        });
    }

    /** Logout: the runs are saved as they are (waiting paused), visuals go. */
    public void unload(Player p) {
        Map<String, QuestRun> mine = runs.remove(p.getUniqueId());
        if (mine != null) mine.values().forEach(r -> save(p.getUniqueId(), r));
        visuals.clearAll(p);
        tracker.clear(p);
    }

    // ── starting ──

    public Start start(Player p, String id, @Nullable String anchor) {
        QuestDef def = store.latest(id);
        if (def == null || def.start() == null) return Start.UNKNOWN;
        QuestRun existing = runsOf(p).get(id);
        if (existing != null && existing.running()) return Start.ACTIVE;
        if (def.sequence() && runningSequence(p) != null) return Start.BUSY;
        if (!def.sequence() && !def.repeatable() && completed(p, id)) return Start.DONE;
        if (!def.cooldown().isEmpty() && plugin.getConditionHandler().isCooldownActive(p, def.cooldown())) return Start.COOLDOWN;
        if (!startConditionsPass(p, def)) return Start.CONDITIONS;
        QuestRun run = new QuestRun(id, def.version(), def.type());
        run.anchor(anchor);
        runsOf(p).put(id, run);
        if (!def.sequence()) Utils.info(p, "quests", "started", "%quest%", title(id));
        debug(p, "start " + id + " v" + def.version());
        enter(p, run, def, def.start().id());
        return Start.STARTED;
    }

    public boolean startConditionsPass(Player p, QuestDef def) {
        List<Condition> conds = def.startConditions();
        if (conds.isEmpty()) return true;
        if (def.startMatchOne()) return conds.stream().anyMatch(c -> plugin.getConditionHandler().evaluate(p, c, null));
        return plugin.getConditionHandler().checkConditions(p, conds, null);
    }

    private boolean completed(Player p, String id) {
        PlayerSession s = hub.getSessions().get(p.getUniqueId());
        return s != null && s.completedQuests.contains(id);
    }

    // ── stepping ──

    /** Runs nodes from {@code nodeId} until every active node waits (or the run ends). */
    void enter(Player p, QuestRun run, QuestDef def, String nodeId) {
        Deque<String> todo = new ArrayDeque<>();
        todo.add(nodeId);
        int steps = 0;
        while (!todo.isEmpty() && run.running()) {
            if (++steps > QuestRules.STEP_GUARD) {
                plugin.getLogger().warning("[Quests] " + def.id() + ": more than " + QuestRules.STEP_GUARD + " steps at once, stopped at " + todo.peek());
                break;
            }
            String id = todo.poll();
            QuestNode n = def.node(id);
            if (n == null) {
                finish(p, run, def, null);
                return;
            }
            debug(p, "→ " + n.kind() + " " + id);
            if (!step(p, run, def, n, todo)) return;
        }
        save(p.getUniqueId(), run);
        refresh(p);
    }

    /** One node; false when the run ended here. */
    private boolean step(Player p, QuestRun run, QuestDef def, QuestNode n, Deque<String> todo) {
        switch (n.kind()) {
            case "start", "do", "score" -> {
                if (n.is("do")) runCommands(p, run, def, n.strings("commands"), n.id(), true);
                if (n.is("score")) score(p, run, def, n.integer("amount", 0));
                return follow(p, run, def, n.id(), "next", todo);
            }
            case "checkpoint" -> {
                run.passCheckpoint(n.id());
                debug(p, "checkpoint " + n.id());
                return follow(p, run, def, n.id(), "next", todo);
            }
            case "check" -> {
                return follow(p, run, def, n.id(), check(p, n) ? "yes" : "no", todo);
            }
            case "split" -> {
                for (QuestDef.Edge e : def.exitsOf(n.id())) todo.add(e.target());
                return true;
            }
            case "join" -> {
                int arrived = run.addCount(n.id(), 1);
                if (!QuestRules.joinReady(n.str("mode"), arrived, def.inbound(n.id()))) return true;
                if ("any".equals(n.str("mode")) && arrived > 1) return true;
                return follow(p, run, def, n.id(), "next", todo);
            }
            case "wait", "wait_for", "click" -> {
                run.active().add(n.id());
                run.reset(n.id());
                if (n.is("wait_for") && waitForAlreadyMet(p, n)) {
                    run.active().remove(n.id());
                    return follow(p, run, def, n.id(), "next", todo);
                }
                return true;
            }
            case "run_sequence" -> {
                return runSequence(p, run, def, n, todo);
            }
            case "end" -> {
                finish(p, run, def, n);
                return false;
            }
            default -> {
                plugin.getLogger().warning("[Quests] " + def.id() + ": unknown node kind '" + n.kind() + "'");
                return follow(p, run, def, n.id(), "next", todo);
            }
        }
    }

    /** Queues where an exit leads; an exit that leads nowhere ends the run as failed. */
    private boolean follow(Player p, QuestRun run, QuestDef def, String node, String handle, Deque<String> todo) {
        String next = def.next(node, handle);
        if (next == null) {
            finish(p, run, def, null);
            return false;
        }
        todo.add(next);
        return true;
    }

    /** Leaves a waiting node through an exit (an event, a click, the clock). */
    void leave(Player p, QuestRun run, String node, String handle) {
        QuestDef def = defOf(run);
        if (def == null || !run.running()) return;
        run.active().remove(node);
        run.reset(node);
        String next = def.next(node, handle);
        if (next == null) finish(p, run, def, null);
        else enter(p, run, def, next);
    }

    private boolean check(Player p, QuestNode n) {
        List<Condition> conds = QuestDef.conditions(Json.array(n.data(), "conditions"));
        if ("one".equals(n.str("match"))) return conds.stream().anyMatch(c -> plugin.getConditionHandler().evaluate(p, c, null));
        return plugin.getConditionHandler().checkConditions(p, conds, null);
    }

    private void score(Player p, QuestRun run, QuestDef def, int amount) {
        run.addScore(amount);
        if ("stat".equals(def.scoreMode()) && !def.scoreStat().isEmpty() && amount != 0) {
            plugin.getStatsManager().modifyStat(p.getUniqueId(), def.scoreStat(), Math.abs(amount), amount > 0 ? Operations.GIVE : Operations.TAKE);
        }
    }

    /** Runs commands as the quest; in a sequence, custom items given by them are remembered (taken back on cancel / fail). */
    void runCommands(Player p, QuestRun run, QuestDef def, List<String> commands, String detail, boolean remember) {
        if (commands.isEmpty()) return;
        if (remember && run.sequence()) for (String c : commands) recordGiven(run, c);
        plugin.getActionHandler().executeActions(p, commands, ActionHandler.SourceType.QUEST, def.id(), anchorKey(run), detail);
    }

    /** "cgive @p oil 2" / "citem give @p oil 2" → oil × 2. */
    static void recordGiven(QuestRun run, String command) {
        String[] w = command.trim().replaceFirst("^/", "").split("\\s+");
        String id = null;
        String amount = "1";
        if (w.length >= 3 && w[0].equalsIgnoreCase("cgive")) {
            id = w[2];
            if (w.length >= 4) amount = w[3];
        } else if (w.length >= 4 && w[0].equalsIgnoreCase("citem") && w[1].equalsIgnoreCase("give")) {
            id = w[3];
            if (w.length >= 5) amount = w[4];
        }
        if (id == null || id.contains("{")) return;
        try {
            run.given().merge(id, Math.max(1, Integer.parseInt(amount)), Integer::sum);
        } catch (NumberFormatException e) {
            run.given().merge(id, 1, Integer::sum);
        }
    }

    @Nullable
    static InteractionKey anchorKey(QuestRun run) {
        return run.anchor() == null ? null : new InteractionKey(run.anchor());
    }

    /** "Reaches" events may already be true when the node is entered. */
    private boolean waitForAlreadyMet(Player p, QuestNode n) {
        JsonObject ev = n.object("event");
        String source = Json.str(ev, "source", "");
        String entry = Json.str(ev, "entry", "");
        Map<String, String> params = params(ev);
        if ("stat.reached".equals(source + "." + entry)) {
            long v = plugin.getStatsManager().getPlayerStat(p.getUniqueId(), params.getOrDefault("stat", ""));
            return QuestEvents.progress(source, entry, params, source, entry, Map.of("stat", params.getOrDefault("stat", ""), "value", String.valueOf(v))) > 0;
        }
        if ("currency.balance_reached".equals(source + "." + entry)) {
            String cur = params.getOrDefault("currency", "");
            long v = plugin.getEcoManager().getCurrencyBalance(p.getUniqueId(), cur);
            return QuestEvents.progress(source, entry, params, source, entry, Map.of("currency", cur, "amount", String.valueOf(v))) > 0;
        }
        return false;
    }

    static Map<String, String> params(JsonObject event) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : Json.object(event, "params").entrySet()) {
            out.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : "");
        }
        return out;
    }

    // ── sequences inside quests ──

    private boolean runSequence(Player p, QuestRun run, QuestDef def, QuestNode n, Deque<String> todo) {
        String seq = n.str("sequence");
        QuestDef child = store.latest(seq);
        if (child == null || !child.sequence() || child.start() == null || runningSequence(p) != null) {
            Utils.error(p, "quests", child == null ? "error.unknown" : "error.busy", "%quest%", seq);
            return follow(p, run, def, n.id(), "fail", todo);
        }
        run.active().add(n.id());
        QuestRun childRun = new QuestRun(seq, child.version(), child.type());
        childRun.parent(def.id(), n.id());
        runsOf(p).put(seq, childRun);
        save(p.getUniqueId(), run);
        enter(p, childRun, child, child.start().id());
        return true;
    }

    // ── ending ──

    /** Ends a run at an End node (null: ended as failed, an exit led nowhere). */
    void finish(Player p, QuestRun run, QuestDef def, @Nullable QuestNode end) {
        if (!run.running()) return;
        boolean success = end != null && !"fail".equals(end.str("result"));
        if (!success && backToCheckpoint(p, run, def)) return;
        String outcome = end != null && def.sequence() && def.scoreOn() ? QuestRules.outcome(end, run.score()) : null;
        run.active().clear();
        run.status(success ? "completed" : "failed");
        // First take back what a failed sequence handed out, then the End's own commands (a reward
        // or a consolation, never taken back).
        if (!success && run.sequence()) undoGiven(p, run);
        if (end != null) {
            runCommands(p, run, def, end.strings("commands"), end.id(), false);
            runCommands(p, run, def, QuestRules.outcomeCommands(end, outcome), end.id() + ":" + outcome, false);
        }
        if (!def.cooldown().isEmpty()) {
            plugin.getActionHandler().executeActions(p, List.of("cooldown give @p " + def.cooldown()), ActionHandler.SourceType.QUEST, def.id(), null);
        }
        save(p.getUniqueId(), run);
        if (!run.sequence()) recordCompletion(p, def, success, outcome);
        String key = run.sequence() ? (success ? "sequence.done" : "sequence.failed") : (success ? "completed" : "failed");
        Utils.info(p, "quests", key, "%quest%", title(def.id()), "%outcome%", outcome == null ? "" : outcome);
        debug(p, "end " + def.id() + (success ? " success" : " fail") + (outcome == null ? "" : " (" + outcome + ")"));
        runsOf(p).remove(def.id());
        refresh(p);
        report(p, "quest", success ? "completed" : "failed", Map.of("quest", def.id()));
        resumeParent(p, run, success, outcome);
    }

    /**
     * A failure with a checkpoint behind it: undo in game what happened since
     * (items handed out, ingredients taken, score), tell the player and go on
     * from the checkpoint. False when there is none, its tries are used up, or
     * the run failed again before the player did anything after the last rewind.
     */
    private boolean backToCheckpoint(Player p, QuestRun run, QuestDef def) {
        QuestCheckpoint at = run.checkpoint();
        if (at == null || rewinding.contains(run)) return false;
        QuestNode node = def.node(at.node());
        String next = def.next(at.node(), "next");
        if (node == null || next == null) return false;
        QuestCheckpoint.Rewind r = run.rewind(Math.max(0, node.integer("tries", 0)));
        if (r == null) return false;
        r.takeBack().forEach((citem, n) -> plugin.getCitemManager().removeCitem(p, citem, n));
        r.giveBack().forEach((citem, n) -> plugin.getCitemManager().addCitem(p, citem, n));
        if ("stat".equals(def.scoreMode()) && !def.scoreStat().isEmpty() && r.scoreUndone() != 0) {
            int undo = r.scoreUndone();
            plugin.getStatsManager().modifyStat(p.getUniqueId(), def.scoreStat(), Math.abs(undo), undo > 0 ? Operations.TAKE : Operations.GIVE);
        }
        String message = node.str("message");
        if (message.isBlank()) Utils.info(p, "quests", "checkpoint.back", "%quest%", title(def.id()));
        else p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', message)));
        debug(p, "back to checkpoint " + at.node() + " (" + run.rewindsTo(at.node()) + "×)");
        rewinding.add(run);
        try {
            enter(p, run, def, next);
        } finally {
            rewinding.remove(run);
        }
        return true;
    }

    private void recordCompletion(Player p, QuestDef def, boolean success, @Nullable String outcome) {
        PlayerSession s = hub.getSessions().get(p.getUniqueId());
        if (s != null && success) s.completedQuests.add(def.id());
        Map<String, Object> b = new LinkedHashMap<>(body("success", success, "version", def.version()));
        if (outcome != null) b.put("outcome", outcome);
        writer.post(path(p.getUniqueId(), def.id()) + "/completions", b);
    }

    /** A sequence started by a quest ended: the quest goes on through the matching exit. */
    private void resumeParent(Player p, QuestRun child, boolean success, @Nullable String outcome) {
        if (child.parentQuest() == null || child.parentNode() == null) return;
        QuestRun parent = runsOf(p).get(child.parentQuest());
        QuestDef pdef = parent == null ? null : defOf(parent);
        if (parent == null || pdef == null || !parent.running()) return;
        parent.active().remove(child.parentNode());
        String next = QuestRules.afterSequence(pdef, child.parentNode(), success, outcome);
        if (next == null) finish(p, parent, pdef, null);
        else enter(p, parent, pdef, next);
    }

    /** Cancels a run (a player's /quest cancel or abandon, staff reset): a sequence's items are undone. */
    public boolean cancel(Player p, String id) {
        QuestRun run = runsOf(p).get(id);
        if (run == null || !run.running()) return false;
        if (run.sequence()) {
            undoGiven(p, run);
            QuestDef def = defOf(run);
            if (def == null || def.returnOnCancel()) {
                run.taken().forEach((citem, n) -> plugin.getCitemManager().addCitem(p, citem, n));
            }
        }
        run.active().clear();
        run.status("cancelled");
        save(p.getUniqueId(), run);
        runsOf(p).remove(id);
        refresh(p);
        resumeParent(p, run, false, null);
        return true;
    }

    private void undoGiven(Player p, QuestRun run) {
        run.given().forEach((citem, n) -> plugin.getCitemManager().removeCitem(p, citem, n));
        run.given().clear();
    }

    /** Staff: forgets a player's run and completions of a quest (the history in the API stays). */
    public void reset(Player p, String id) {
        cancel(p, id);
        PlayerSession s = hub.getSessions().get(p.getUniqueId());
        if (s != null) s.completedQuests.remove(id);
    }

    // ── events ──

    /** Something happened that Wait for nodes may wait on. */
    public void report(Player p, String source, String entry, Map<String, String> facts) {
        for (QuestRun run : new ArrayList<>(runsOf(p).values())) {
            QuestDef def = defOf(run);
            if (def == null || !run.running()) continue;
            for (String node : new ArrayList<>(run.active())) {
                QuestNode n = def.node(node);
                if (n == null || !n.is("wait_for") || !run.running()) continue;
                JsonObject ev = n.object("event");
                Map<String, String> params = params(ev);
                String s = Json.str(ev, "source", "");
                String e = Json.str(ev, "entry", "");
                long step = QuestEvents.progress(s, e, params, source, entry, facts);
                if (step <= 0) continue;
                long target = QuestEvents.target(s, e, params, n.integer("count", 1));
                int now = run.addCount(node, (int) Math.min(Integer.MAX_VALUE, step));
                debug(p, "event " + source + "." + entry + " → " + node + " " + Math.min(now, target) + "/" + target);
                if (now >= target) leave(p, run, node, "next");
                else {
                    save(p.getUniqueId(), run);
                    refresh(p);
                }
            }
        }
    }

    // ── the clock ──

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (QuestRun run : new ArrayList<>(runsOf(p).values())) tickRun(p, run);
        }
    }

    private void tickRun(Player p, QuestRun run) {
        QuestDef def = defOf(run);
        if (def == null || !run.running()) return;
        for (String node : new ArrayList<>(run.active())) {
            QuestNode n = def.node(node);
            if (n == null || !run.running()) continue;
            if (n.is("wait")) {
                long ticks = QuestRules.durationTicks(n.str("duration"));
                if (run.addElapsed(node, TICK_MS) >= Math.max(1, ticks) * 50) leave(p, run, node, "next");
            } else if (n.is("click") && QuestRules.canFail(n)) {
                if (QuestRules.timedOut(n, run.addElapsed(node, TICK_MS))) clicks.fail(p, run, n, "error.time");
            }
        }
    }

    // ── saving, visuals, debug ──

    void save(UUID uuid, QuestRun run) {
        JsonObject row = new JsonObject();
        row.addProperty("quest_id", run.questId());
        row.addProperty("status", run.status());
        row.addProperty("version", run.version());
        row.add("run", run.toJson());
        PlayerSession s = hub.getSessions().get(uuid);
        if (s != null) s.quests.put(run.questId(), row);
        writer.put(path(uuid, run.questId()), row);
    }

    private static String path(UUID uuid, String id) {
        return "/v1/players/" + uuid + "/quests/" + seg(id);
    }

    private void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    /** Draws the player's holograms, pointers, displays, waypoints and tracker from their runs. */
    void refresh(Player p) {
        QuestScene.draw(this, p, runsOf(p).values());
        tracker.update(p, runsOf(p).values());
    }

    void debug(Player p, String line) {
        if (plugin.getDebugMode().isOn(p)) plugin.getDebugMode().tell(p, "§7[Quest] §f" + line);
    }

    public Collection<QuestDef> published() {
        return store.all();
    }
}
