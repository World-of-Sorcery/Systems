package me.hektortm.woSSystems.systems.magic.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The spell format as wos-api defines it ({@code GET /v1/content/spells/catalog}): every
 * node kind with its field defaults and outlets, the settings' defaults, and the limits.
 * The plugin never hard-codes a default, so the portal, the agent guide and the engine agree.
 */
public final class SpellCatalog {

    /** One node kind: its defaults by field key, its outlet keys, whether it acts on a target entity. */
    public record Kind(String type, String category, Map<String, JsonElement> defaults, List<String> outlets, boolean entityEffect,
                       boolean harmful, Map<String, List<String>> harmlessWhen) {
        public boolean isShape() {
            return "shape".equals(category);
        }

        public boolean harmful(SpellNode n) {
            if (!harmful) return false;
            if (harmlessWhen.isEmpty()) return true;
            for (Map.Entry<String, List<String>> e : harmlessWhen.entrySet()) {
                String val = n.str(e.getKey());
                if (e.getValue().contains(val)) return false;
            }
            return true;
        }
    }

    /** The caps every spell obeys (spec §3.4). */
    public record Limits(int maxNodes, int maxDepth, int maxShapes, int maxAreas, int maxCommands, int areaEntitiesPerTick, int maxAnimations,
                         int maxLevel, int maxVelocity, int maxRuns, int maxItems, int maxDisplays, int maxFakeBlocks, int maxCastDepth) { }

    private final Map<String, Kind> kinds;
    private final Map<String, JsonElement> settingDefaults;
    private final Limits limits;

    private SpellCatalog(Map<String, Kind> kinds, Map<String, JsonElement> settingDefaults, Limits limits) {
        this.kinds = kinds;
        this.settingDefaults = settingDefaults;
        this.limits = limits;
    }

    public static SpellCatalog parse(JsonObject json) {
        Map<String, Kind> kinds = new HashMap<>();
        for (JsonElement e : Json.array(json, "kinds")) {
            if (!e.isJsonObject()) continue;
            JsonObject k = e.getAsJsonObject();
            List<String> outlets = new ArrayList<>();
            for (JsonElement o : Json.array(k, "outlets")) {
                if (o.isJsonObject()) outlets.add(Json.str(o.getAsJsonObject(), "key", ""));
            }
            String type = Json.str(k, "type", "");
            boolean harmful = Json.bool(k, "harmful", false);
            Map<String, List<String>> harmlessWhen = new HashMap<>();
            JsonObject hw = Json.object(k, "harmless_when");
            for (String key : hw.keySet()) {
                JsonElement elem = hw.get(key);
                if (elem != null && elem.isJsonArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonElement item : elem.getAsJsonArray()) {
                        if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                            list.add(item.getAsString());
                        }
                    }
                    harmlessWhen.put(key, List.copyOf(list));
                }
            }
            kinds.put(type, new Kind(type, Json.str(k, "category", ""), defaults(Json.array(k, "fields")), List.copyOf(outlets),
                    Json.bool(k, "entity_effect", false), harmful, Map.copyOf(harmlessWhen)));
        }
        JsonObject l = Json.object(json, "limits");
        Limits limits = new Limits(Json.integer(l, "max_nodes", 40), Json.integer(l, "max_depth", 6), Json.integer(l, "max_shapes", 10),
                Json.integer(l, "max_areas", 4), Json.integer(l, "max_commands", 20), Json.integer(l, "area_entities_per_tick", 16),
                Json.integer(l, "max_animations", 8), Json.integer(l, "max_level", 20), Json.integer(l, "max_velocity", 4),
                Json.integer(l, "max_runs", 500), Json.integer(l, "max_items", 64), Json.integer(l, "max_displays", 16),
                Json.integer(l, "max_fake_blocks", 200), Json.integer(l, "max_cast_depth", 2));
        return new SpellCatalog(Map.copyOf(kinds), defaults(Json.array(json, "settings")), limits);
    }

    /** A catalogue that knows nothing (before the first load): every spell's nodes are skipped. */
    public static SpellCatalog empty() {
        return parse(new JsonObject());
    }

    private static Map<String, JsonElement> defaults(Iterable<JsonElement> fields) {
        Map<String, JsonElement> out = new HashMap<>();
        for (JsonElement f : fields) {
            if (!f.isJsonObject()) continue;
            JsonObject o = f.getAsJsonObject();
            JsonElement d = o.get("default");
            if (d != null && !d.isJsonNull()) out.put(Json.str(o, "key", ""), d);
        }
        return Map.copyOf(out);
    }

    @Nullable
    public Kind kind(String type) {
        return type == null ? null : kinds.get(type);
    }

    @Nullable
    public JsonElement settingDefault(String key) {
        return settingDefaults.get(key);
    }

    public Limits limits() {
        return limits;
    }
}
