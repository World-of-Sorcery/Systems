package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonElement;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;

import javax.annotation.Nullable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The published quests and sequences. The newest version of each comes from
 * {@code GET /v1/content/quests/published} and is reloaded by the webhook when
 * one is published; an older version a player's run is still on is fetched
 * once with {@code GET /v1/content/quests/{id}/versions/{n}}. Drafts never
 * reach the game.
 */
public final class QuestStore {

    private static final String BASE = "/v1/content/quests";

    private final ContentStore<QuestDef> latest;
    private final Map<String, QuestDef> older = new ConcurrentHashMap<>();
    /** Quest id → title (a published version carries none; the drafts do). */
    private final Map<String, String> titles = new ConcurrentHashMap<>();
    private final ApiWriter writer;
    private final WosApi api;
    private final Logger log;

    public QuestStore(ApiServices s) {
        this.api = s.api();
        this.log = s.log();
        this.writer = s.writer();
        this.latest = s.content().register(new ContentStore<>("quests", "Quest", new Source()));
    }

    /** Where the engine saves runs and completions. */
    public ApiWriter writer() {
        return writer;
    }

    /** The quest's title, or its id made readable while none is known. */
    public String title(String id) {
        String t = titles.get(id);
        return t == null || t.isBlank() ? QuestRules.pretty(id) : t;
    }

    public ContentStore<QuestDef> store() {
        return latest;
    }

    @Nullable
    public QuestDef latest(String id) {
        return latest.get(id);
    }

    public Collection<QuestDef> all() {
        return latest.all();
    }

    /** A version that is already known (the newest, or an older one fetched before). */
    @Nullable
    public QuestDef version(String id, int version) {
        QuestDef d = latest.get(id);
        if (d != null && d.version() == version) return d;
        return older.get(id + "@" + version);
    }

    /** Fetches an older version (blocking: never on the main thread). */
    @Nullable
    public QuestDef fetch(String id, int version) {
        QuestDef known = version(id, version);
        if (known != null) return known;
        try {
            JsonElement json = api.getJson(BASE + "/" + enc(id) + "/versions/" + version);
            if (json == null || !json.isJsonObject()) return null;
            QuestDef d = QuestDef.parse(json.getAsJsonObject());
            older.put(id + "@" + version, d);
            return d;
        } catch (ApiException e) {
            log.warning("[Quests] could not load " + id + " version " + version + ": " + e.getMessage());
            return null;
        }
    }

    private static String enc(String id) {
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** The published list, and one quest's newest published version. */
    private final class Source implements ContentStore.Source<QuestDef> {
        @Override
        public Map<String, QuestDef> loadAll() throws ApiException {
            Map<String, QuestDef> out = new LinkedHashMap<>();
            JsonElement list = api.getJson(BASE);
            if (list != null && list.isJsonArray()) {
                for (JsonElement e : list.getAsJsonArray()) {
                    if (e.isJsonObject()) titles.put(Json.str(e.getAsJsonObject(), "id", ""), Json.str(e.getAsJsonObject(), "title", ""));
                }
            }
            JsonElement json = api.getJson(BASE + "/published");
            if (json == null || !json.isJsonArray()) return out;
            for (JsonElement e : json.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                try {
                    QuestDef d = QuestDef.parse(e.getAsJsonObject());
                    out.put(d.id(), d);
                } catch (RuntimeException ex) {
                    log.warning("[Quests] skipped a published quest — " + ex);
                }
            }
            return out;
        }

        @Override
        public Optional<QuestDef> loadOne(String id) throws ApiException {
            try {
                JsonElement draft = api.getJson(BASE + "/" + enc(id));
                if (draft == null || !draft.isJsonObject()) return Optional.empty();
                titles.put(id, Json.str(draft.getAsJsonObject(), "title", ""));
                int version = Json.integer(draft.getAsJsonObject(), "published_version", 0);
                if (version <= 0) return Optional.empty(); // never published: not in the game
                JsonElement row = api.getJson(BASE + "/" + enc(id) + "/versions/" + version);
                return row != null && row.isJsonObject() ? Optional.of(QuestDef.parse(row.getAsJsonObject())) : Optional.empty();
            } catch (ApiException e) {
                if (e.isNotFound()) return Optional.empty();
                throw e;
            }
        }
    }
}
