package me.hektortm.woSSystems.systems.magic.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One node of a spell's graph: a shape or an effect, its fields, and (for shapes) the nodes
 * of each outlet. A field missing or of the wrong JSON type reads as the catalogue's default.
 */
public final class SpellNode {

    private final SpellCatalog.Kind kind;
    private final String id;
    private final JsonObject data;
    private final Map<String, List<SpellNode>> children;

    private SpellNode(SpellCatalog.Kind kind, String id, JsonObject data, Map<String, List<SpellNode>> children) {
        this.kind = kind;
        this.id = id;
        this.data = data;
        this.children = children;
    }

    /**
     * Reads a list of nodes for a caster at {@code level}. Unknown types, non-objects and nodes
     * nested deeper than the catalogue allows are skipped, each with a warning; their siblings
     * stay. An {@code at_level} node above {@code level} is left out (spec §3.5).
     */
    public static List<SpellNode> parseList(JsonArray list, SpellCatalog catalog, List<String> warnings, String path, int depth, int level) {
        List<SpellNode> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String at = path + "[" + i + "]";
            JsonElement e = list.get(i);
            if (!e.isJsonObject()) {
                warnings.add(at + ": not an object");
                continue;
            }
            JsonObject n = e.getAsJsonObject();
            String type = Json.str(n, "type", "");
            SpellCatalog.Kind kind = catalog.kind(type);
            if (kind == null) {
                warnings.add(at + ": unknown type '" + type + "'");
                continue;
            }
            if (depth > catalog.limits().maxDepth()) {
                warnings.add(at + ": nested too deep");
                continue;
            }
            if ("at_level".equals(type) && Json.integer(n, "level", 2) > level) continue;
            Map<String, List<SpellNode>> children = new HashMap<>();
            for (String outlet : kind.outlets()) {
                JsonElement c = n.get(outlet);
                if (c != null && c.isJsonArray()) {
                    children.put(outlet, parseList(c.getAsJsonArray(), catalog, warnings, at + "." + outlet, depth + 1, level));
                }
            }
            out.add(new SpellNode(kind, Json.str(n, "id", ""), n, children));
        }
        return out;
    }

    public String type() {
        return kind.type();
    }

    public String id() {
        return id;
    }

    public boolean isShape() {
        return kind.isShape();
    }

    public boolean isEntityEffect() {
        return kind.entityEffect();
    }

    public List<SpellNode> children(String outlet) {
        return children.getOrDefault(outlet, List.of());
    }

    private JsonElement value(String key) {
        JsonElement v = data.get(key);
        return v == null || v.isJsonNull() ? null : v;
    }

    private JsonElement fallback(String key) {
        return kind.defaults().get(key);
    }

    public double num(String key) {
        JsonElement v = value(key);
        if (isNumber(v)) return v.getAsDouble();
        JsonElement d = fallback(key);
        return isNumber(d) ? d.getAsDouble() : 0;
    }

    public int integer(String key) {
        return (int) num(key);
    }

    public boolean bool(String key) {
        JsonElement v = value(key);
        if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) return v.getAsBoolean();
        JsonElement d = fallback(key);
        return d != null && d.isJsonPrimitive() && d.getAsJsonPrimitive().isBoolean() && d.getAsBoolean();
    }

    public String str(String key) {
        JsonElement v = value(key);
        if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) return v.getAsString();
        JsonElement d = fallback(key);
        return d != null && d.isJsonPrimitive() && d.getAsJsonPrimitive().isString() ? d.getAsString() : "";
    }

    public List<String> strings(String key) {
        JsonElement v = value(key);
        List<String> out = new ArrayList<>();
        if (v == null || !v.isJsonArray()) return out;
        for (JsonElement e : v.getAsJsonArray()) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) out.add(e.getAsString());
        }
        return out;
    }

    static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }
}
