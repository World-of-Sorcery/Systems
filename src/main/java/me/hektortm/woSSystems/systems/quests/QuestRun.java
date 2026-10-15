package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One player's progress in one quest or sequence, on one published version.
 * Saved as the {@code run} document of {@code game.player_quest}; everything
 * here survives a logout.
 */
public final class QuestRun {

    public static final String ACTIVE = "active";

    private final String questId;
    private final int version;
    private final String type;
    private String status = ACTIVE;
    /** Nodes that wait (wait_for, click, wait, join). */
    private final Set<String> active = new LinkedHashSet<>();
    /** Per node: events / clicks so far, a join's arrivals. */
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    /** Per node: milliseconds spent waiting there while online (time limits, Wait). */
    private final Map<String, Long> elapsed = new LinkedHashMap<>();
    /** Per click node: wrong clicks so far. */
    private final Map<String, Integer> wrong = new LinkedHashMap<>();
    private int score;
    /** The interaction key the sequence started on ("block:world:x:y:z", "npc:7"). */
    @Nullable private String anchor;
    /** Custom item id → amount the sequence gave / took (undone on cancel / fail). */
    private final Map<String, Integer> given = new LinkedHashMap<>();
    private final Map<String, Integer> taken = new LinkedHashMap<>();
    /** Click nodes whose state display stands from their step on. */
    private final Set<String> displays = new LinkedHashSet<>();
    /** A sequence started from a quest: the quest and its Run sequence node. */
    @Nullable private String parentQuest;
    @Nullable private String parentNode;
    /** A finished click node with "one click too many fails": its id, while the next step runs. */
    @Nullable private String overshoot;
    /** The last Checkpoint passed, and per checkpoint node how often the run went back to it. */
    @Nullable private QuestCheckpoint checkpoint;
    private final Map<String, Integer> rewinds = new LinkedHashMap<>();

    public QuestRun(String questId, int version, String type) {
        this.questId = questId;
        this.version = version;
        this.type = type;
    }

    public String questId() { return questId; }
    public int version() { return version; }
    public String type() { return type; }
    public boolean sequence() { return "sequence".equals(type); }
    public String status() { return status; }
    public void status(String s) { status = s; }
    public boolean running() { return ACTIVE.equals(status); }
    public Set<String> active() { return active; }
    public int score() { return score; }
    public void addScore(int n) { score += n; }
    @Nullable public String anchor() { return anchor; }
    public void anchor(@Nullable String key) { anchor = key; }
    public Map<String, Integer> given() { return given; }
    public Map<String, Integer> taken() { return taken; }
    public Set<String> displays() { return displays; }
    @Nullable public String parentQuest() { return parentQuest; }
    @Nullable public String parentNode() { return parentNode; }
    @Nullable public String overshoot() { return overshoot; }
    public void overshoot(@Nullable String node) { overshoot = node; }

    public void parent(String quest, String node) {
        parentQuest = quest;
        parentNode = node;
    }

    public int count(String node) { return counts.getOrDefault(node, 0); }
    public int addCount(String node, int n) { return counts.merge(node, n, Integer::sum); }
    public long elapsed(String node) { return elapsed.getOrDefault(node, 0L); }
    public long addElapsed(String node, long ms) { return elapsed.merge(node, ms, Long::sum); }
    public int wrongClicks(String node) { return wrong.getOrDefault(node, 0); }
    public int addWrong(String node) { return wrong.merge(node, 1, Integer::sum); }

    @Nullable public QuestCheckpoint checkpoint() { return checkpoint; }
    public int rewindsTo(String node) { return rewinds.getOrDefault(node, 0); }

    /** A Checkpoint node was passed: a later failure comes back to the run as it is now. */
    public void passCheckpoint(String node) {
        checkpoint = new QuestCheckpoint(node, score, anchor, given, taken, displays);
    }

    /**
     * A failure: back to the last checkpoint, if one was passed and it has
     * tries left ({@code maxTries} 0 = no limit). Score, anchor, given and taken
     * items and displays become what they were there; every waiting node is
     * dropped. Returns what the engine must undo in game, or null (the failure
     * goes through).
     */
    @Nullable
    public QuestCheckpoint.Rewind rewind(int maxTries) {
        QuestCheckpoint c = checkpoint;
        if (c == null || (maxTries > 0 && rewindsTo(c.node) >= maxTries)) return null;
        QuestCheckpoint.Rewind r = new QuestCheckpoint.Rewind(c.node,
                QuestCheckpoint.since(c.given, given), QuestCheckpoint.since(c.taken, taken), score - c.score);
        score = c.score;
        anchor = c.anchor;
        given.clear();
        given.putAll(c.given);
        taken.clear();
        taken.putAll(c.taken);
        displays.clear();
        displays.addAll(c.displays);
        active.clear();
        counts.clear();
        elapsed.clear();
        wrong.clear();
        overshoot = null;
        rewinds.merge(c.node, 1, Integer::sum);
        return r;
    }

    /** Forgets what a node counted (entered again, or left). */
    public void reset(String node) {
        counts.remove(node);
        elapsed.remove(node);
        wrong.remove(node);
    }

    // ── JSON ──

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("quest_id", questId);
        o.addProperty("version", version);
        o.addProperty("type", type);
        o.addProperty("status", status);
        o.add("active", strings(active));
        o.add("counts", ints(counts));
        o.add("elapsed", longs(elapsed));
        o.add("wrong", ints(wrong));
        o.addProperty("score", score);
        if (anchor != null) o.addProperty("anchor", anchor);
        o.add("given", ints(given));
        o.add("taken", ints(taken));
        o.add("displays", strings(displays));
        if (parentQuest != null) o.addProperty("parent_quest", parentQuest);
        if (parentNode != null) o.addProperty("parent_node", parentNode);
        if (overshoot != null) o.addProperty("overshoot", overshoot);
        if (checkpoint != null) o.add("checkpoint", checkpoint.toJson());
        if (!rewinds.isEmpty()) o.add("rewinds", ints(rewinds));
        return o;
    }

    /** A saved run; null when the document is not one (an empty or old row). */
    @Nullable
    public static QuestRun fromJson(JsonObject o) {
        String id = Json.str(o, "quest_id", "");
        if (id.isEmpty()) return null;
        QuestRun r = new QuestRun(id, Json.integer(o, "version", 0), Json.str(o, "type", "quest"));
        r.status = Json.str(o, "status", ACTIVE);
        r.active.addAll(Json.strings(o, "active"));
        readInts(Json.object(o, "counts"), r.counts);
        for (Map.Entry<String, JsonElement> e : Json.object(o, "elapsed").entrySet()) r.elapsed.put(e.getKey(), e.getValue().getAsLong());
        readInts(Json.object(o, "wrong"), r.wrong);
        r.score = Json.integer(o, "score", 0);
        r.anchor = Json.str(o, "anchor");
        readInts(Json.object(o, "given"), r.given);
        readInts(Json.object(o, "taken"), r.taken);
        r.displays.addAll(Json.strings(o, "displays"));
        r.parentQuest = Json.str(o, "parent_quest");
        r.parentNode = Json.str(o, "parent_node");
        r.overshoot = Json.str(o, "overshoot");
        r.checkpoint = QuestCheckpoint.fromJson(Json.object(o, "checkpoint"));
        readInts(Json.object(o, "rewinds"), r.rewinds);
        return r;
    }

    private static JsonArray strings(Iterable<String> list) {
        JsonArray a = new JsonArray();
        for (String s : list) a.add(s);
        return a;
    }

    private static JsonObject ints(Map<String, Integer> m) {
        JsonObject o = new JsonObject();
        m.forEach(o::addProperty);
        return o;
    }

    private static JsonObject longs(Map<String, Long> m) {
        JsonObject o = new JsonObject();
        m.forEach(o::addProperty);
        return o;
    }

    private static void readInts(JsonObject o, Map<String, Integer> into) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) into.put(e.getKey(), e.getValue().getAsInt());
    }
}
