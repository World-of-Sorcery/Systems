package me.hektortm.woSSystems.database.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentRegistry;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.cscreens.CscreenSettings;
import me.hektortm.woSSystems.utils.model.Cscreen;
import me.hektortm.wosCore.api.WosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Custom screens: the definitions (settings, body, inputs, buttons and their
 * conditions) come from wos-api ({@code /v1/content/cscreens/{id}}) and are
 * reloaded one by one when the portal changes them.
 */
public class CscreenDAO {
    private static final Set<String> CONDITION_TYPES = Set.of("cscreen");

    private final ConditionDAO conditions;
    private final ContentStore<Cscreen> store;

    public CscreenDAO(ContentRegistry content, WosApi api, ConditionDAO conditions, Logger log) {
        this.conditions = conditions;
        this.store = content.register(new ContentStore<>("cscreens", "Custom screen",
                ApiSource.tree(api, "/v1/content/cscreens", this::map, log)));
    }

    public Cscreen getCscreen(String id) {
        return store.get(id);
    }

    public boolean exists(String id) {
        return store.exists(id);
    }

    // ── tree → model ───────────────────────────────────────────────────────────

    private Cscreen map(JsonObject tree) {
        String id = Json.str(tree, "id");
        JsonArray childConditions = new JsonArray();
        Cscreen screen = new Cscreen(id, Json.str(tree, "title", ""), Json.str(tree, "type", "notice"),
                CscreenSettings.screen(Json.str(tree, "settings")),
                elements(tree, "body", CscreenSettings::body, childConditions),
                elements(tree, "inputs", CscreenSettings::input, childConditions),
                elements(tree, "buttons", CscreenSettings::button, childConditions));
        conditions.replaceChildren(CONDITION_TYPES, id, childConditions);
        return screen;
    }

    private static <T> List<Cscreen.Element<T>> elements(JsonObject tree, String list, Function<String, T> settings, JsonArray conditions) {
        List<Cscreen.Element<T>> out = new ArrayList<>();
        for (JsonElement el : Json.array(tree, list)) {
            JsonObject e = el.getAsJsonObject();
            out.add(new Cscreen.Element<>(Json.integer(e, "element_id", 0), Json.str(e, "matchtype", "all"),
                    settings.apply(Json.str(e, "settings"))));
            conditions.addAll(Json.array(e, "conditions"));
        }
        return out;
    }
}
