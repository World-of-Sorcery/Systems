package me.hektortm.woSSystems.systems.quests.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.utils.model.Condition;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One published version of a quest or sequence (wos-api
 * {@code GET /v1/content/quests/published} or {@code …/versions/{n}}):
 * its nodes, the edges between them and its settings.
 */
public final class QuestDef {

    /** One edge: leaves {@code source} through the exit {@code handle}. */
    public record Edge(String source, String handle, String target) {}

    private final String id;
    private final int version;
    private final String type;
    private final JsonObject settings;
    private final Map<String, QuestNode> nodes;
    private final List<Edge> edges;

    public QuestDef(String id, int version, String type, JsonObject settings, Map<String, QuestNode> nodes, List<Edge> edges) {
        this.id = id;
        this.version = version;
        this.type = type;
        this.settings = settings;
        this.nodes = Collections.unmodifiableMap(nodes);
        this.edges = List.copyOf(edges);
    }

    /** Reads a version row: {@code quest_id, version, type, settings, graph}. */
    public static QuestDef parse(JsonObject row) {
        JsonObject graph = obj(row, "graph");
        Map<String, QuestNode> nodes = new LinkedHashMap<>();
        for (JsonElement e : Json.array(graph, "nodes")) {
            if (!e.isJsonObject()) continue;
            JsonObject n = e.getAsJsonObject();
            String id = Json.str(n, "id", "");
            if (!id.isEmpty()) nodes.put(id, new QuestNode(id, Json.str(n, "kind", ""), Json.object(n, "data")));
        }
        List<Edge> edges = new ArrayList<>();
        for (JsonElement e : Json.array(graph, "edges")) {
            if (!e.isJsonObject()) continue;
            JsonObject x = e.getAsJsonObject();
            edges.add(new Edge(Json.str(x, "source", ""), Json.str(x, "sourceHandle", ""), Json.str(x, "target", "")));
        }
        String id = Json.str(row, "quest_id", Json.str(row, "id", ""));
        return new QuestDef(id, Json.integer(row, "version", 0), Json.str(row, "type", "quest"), obj(row, "settings"), nodes, edges);
    }

    /** A JSON object field, also one stored as JSON text. */
    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = Json.field(o, key);
        if (e == null) return new JsonObject();
        if (e.isJsonObject()) return e.getAsJsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(e.getAsString());
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ex) {
            return new JsonObject();
        }
    }

    public String id() { return id; }
    public int version() { return version; }
    public String type() { return type; }
    public boolean sequence() { return "sequence".equals(type); }
    public JsonObject settings() { return settings; }
    public Map<String, QuestNode> nodes() { return nodes; }
    public List<Edge> edges() { return edges; }

    @Nullable
    public QuestNode node(String id) {
        return nodes.get(id);
    }

    @Nullable
    public QuestNode start() {
        for (QuestNode n : nodes.values()) if (n.is("start")) return n;
        return null;
    }

    /** Where the exit {@code handle} of {@code source} leads, or null when it is not connected. */
    @Nullable
    public String next(String source, String handle) {
        for (Edge e : edges) if (e.source().equals(source) && e.handle().equals(handle)) return e.target();
        return null;
    }

    /** Every exit of a node that is connected, with its target (a split's branches). */
    public List<Edge> exitsOf(String source) {
        List<Edge> out = new ArrayList<>();
        for (Edge e : edges) if (e.source().equals(source)) out.add(e);
        return out;
    }

    /** How many edges lead into a node (a join waits for that many with mode all). */
    public int inbound(String target) {
        int n = 0;
        for (Edge e : edges) if (e.target().equals(target)) n++;
        return n;
    }

    /** Every interaction a click node of this graph targets (clicks on them belong to the sequence). */
    public List<String> clickTargets() {
        List<String> out = new ArrayList<>();
        for (QuestNode n : nodes.values()) {
            String t = n.str("target");
            if (n.is("click") && !t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    // ── settings ──

    public String setting(String key) { return Json.str(settings, key, ""); }

    public boolean repeatable() { return "repeatable".equals(setting("repeat")); }

    public String cooldown() { return setting("cooldown"); }

    public String startsAt() { return setting("starts_at"); }

    /** Whether the generated recipe menu lists it ({@code menu: false} turns that off). */
    public boolean inMenu() {
        JsonElement e = Json.field(settings, "menu");
        return e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean() || e.getAsBoolean();
    }

    public String menuIcon() { return Json.str(Json.object(settings, "menu_icon"), "citem", ""); }

    /** {@code off}, {@code local} or {@code stat}. */
    public String scoreMode() { return Json.str(Json.object(settings, "score"), "mode", "off"); }

    public String scoreStat() { return Json.str(Json.object(settings, "score"), "stat", ""); }

    public boolean scoreOn() { return "local".equals(scoreMode()) || "stat".equals(scoreMode()); }

    public String theme(String key) { return Json.str(Json.object(settings, "theme"), key, ""); }

    public boolean returnOnCancel() {
        JsonElement e = Json.field(settings, "return_on_cancel");
        return e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean() || e.getAsBoolean();
    }

    public boolean startMatchOne() { return "one".equals(setting("start_match")); }

    public List<Condition> startConditions() {
        return conditions(Json.array(settings, "start_conditions"));
    }

    /** The conditions of a Check node or the start conditions (key, value, parameter). */
    public static List<Condition> conditions(Iterable<JsonElement> list) {
        List<Condition> out = new ArrayList<>();
        for (JsonElement e : list) {
            if (!e.isJsonObject()) continue;
            JsonObject c = e.getAsJsonObject();
            out.add(new Condition(Json.str(c, "condition_key", ""), Json.str(c, "value", ""), Json.str(c, "parameter", "")));
        }
        return out;
    }
}
