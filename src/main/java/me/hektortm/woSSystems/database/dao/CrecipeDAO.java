package me.hektortm.woSSystems.database.dao;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentRegistry;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.crecipes.CrecipeRules;
import me.hektortm.woSSystems.utils.model.Crecipe;
import me.hektortm.wosCore.api.WosApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Custom recipes: the definitions (slots, result, commands and conditions)
 * come from wos-api ({@code /v1/content/crecipes/{id}}) and are reloaded one by
 * one when the portal changes them.
 */
public class CrecipeDAO {
    private static final Set<String> CONDITION_TYPES = Set.of("recipe");

    private final ConditionDAO conditions;
    private final ContentStore<Crecipe> store;

    public CrecipeDAO(ContentRegistry content, WosApi api, ConditionDAO conditions, Logger log) {
        this.conditions = conditions;
        this.store = content.register(new ContentStore<>("crecipes", "Custom recipe",
                ApiSource.tree(api, "/v1/content/crecipes", this::map, log)));
    }

    public Crecipe getCrecipe(String id) {
        return store.get(id);
    }

    /** Every recipe, as loaded (complete or not). */
    public Collection<Crecipe> all() {
        return store.all();
    }

    /** Runs {@code listener} after every load and reload (on the thread that loaded). */
    public void onChange(Runnable listener) {
        store.onChange(s -> listener.run());
    }

    // ── tree → model ───────────────────────────────────────────────────────────

    private Crecipe map(JsonObject tree) {
        String id = Json.str(tree, "id");
        JsonObject settings = object(Json.str(tree, "settings"));
        Crecipe recipe = new Crecipe(id, Json.str(tree, "type", Crecipe.SHAPED), slots(Json.str(tree, "ingredients")),
                new Crecipe.Item(Json.str(tree, "result_kind", "citem"), Json.str(tree, "result_id", "").trim()),
                Math.max(1, Math.min(Json.integer(tree, "result_amount", 1), 64)),
                Json.str(tree, "category", "misc"), Json.str(tree, "matchtype", "all"),
                Json.strings(tree, "commands"), cooking(settings), Json.bool(settings, "keep_base", false));
        conditions.replaceChildren(CONDITION_TYPES, id, Json.array(tree, "conditions"));
        return recipe;
    }

    /** The slots as stored: a JSON list of {@code {"items": [{"kind", "id"}]}}. Anything else reads as no slots. */
    static List<Crecipe.Slot> slots(String json) {
        List<Crecipe.Slot> out = new ArrayList<>();
        JsonElement parsed = parse(json);
        if (parsed == null || !parsed.isJsonArray()) return out;
        for (JsonElement el : parsed.getAsJsonArray()) {
            List<Crecipe.Item> items = new ArrayList<>();
            if (el.isJsonObject()) {
                for (JsonElement item : Json.array(el.getAsJsonObject(), "items")) {
                    if (!item.isJsonObject()) continue;
                    String itemId = Json.str(item.getAsJsonObject(), "id", "").trim();
                    if (!itemId.isEmpty()) items.add(new Crecipe.Item(Json.str(item.getAsJsonObject(), "kind", "item"), itemId));
                }
            }
            out.add(Crecipe.Slot.of(items));
        }
        return out;
    }

    static Crecipe.Cooking cooking(JsonObject settings) {
        List<String> stations = new ArrayList<>();
        for (JsonElement el : Json.array(settings, "stations")) {
            if (el.isJsonPrimitive()) stations.add(el.getAsString());
        }
        return new Crecipe.Cooking(stations, (float) Math.max(0, number(settings, "experience", 0)),
                CrecipeRules.ticks(number(settings, "cook_time", 10)));
    }

    private static double number(JsonObject o, String name, double fallback) {
        JsonElement e = Json.field(o, name);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static JsonObject object(String json) {
        JsonElement parsed = parse(json);
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }

    private static JsonElement parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JsonParser.parseString(json);
        } catch (Exception e) {
            return null;
        }
    }
}
