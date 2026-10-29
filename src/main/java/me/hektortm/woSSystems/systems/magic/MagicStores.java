package me.hektortm.woSSystems.systems.magic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.systems.magic.model.MagicSettings;
import me.hektortm.woSSystems.systems.magic.model.SpellCatalog;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.systems.magic.model.WandSkin;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The magic content (part 2's routes): the published spells (with the format catalogue),
 * the wand skins and the one magic settings entry. Reloaded by the webhook like every
 * content store (keys {@code spells}, {@code wandskins}, {@code magic}).
 */
public final class MagicStores {

    private static final String SPELLS = "/v1/content/spells";
    public static final String SETTINGS_ID = "magic";

    private final WosApi api;
    private final Logger log;
    private final ContentStore<SpellDef> spells;
    private final ContentStore<WandSkin> skins;
    private final ContentStore<MagicSettings> settings;
    private volatile SpellCatalog catalog = SpellCatalog.empty();

    public MagicStores(ApiServices s) {
        this.api = s.api();
        this.log = s.log();
        this.spells = s.content().register(new ContentStore<>("spells", "Spell", new SpellSource()));
        this.skins = s.content().register(new ContentStore<>("wandskins", "Wand skin",
                ApiSource.flat(api, "/v1/content/wandskins", "id", WandSkin::parse, log)));
        this.settings = s.content().register(new ContentStore<>("magic", "Magic settings", new SettingsSource()));
    }

    @Nullable
    public SpellDef spell(String id) { return spells.get(id); }

    public Collection<SpellDef> spells() { return spells.all(); }

    public ContentStore<SpellDef> spellStore() { return spells; }

    @Nullable
    public WandSkin skin(String id) { return skins.get(id); }

    public Collection<WandSkin> skins() { return skins.all(); }

    public ContentStore<WandSkin> skinStore() { return skins; }

    /** The settings; the defaults until they are loaded. */
    public MagicSettings settings() {
        MagicSettings s = settings.get(SETTINGS_ID);
        return s == null ? MagicSettings.defaults() : s;
    }

    public SpellCatalog catalog() { return catalog; }

    /**
     * A spell's draft as a spell to cast ({@code /spell test}); null when it doesn't exist.
     * Blocking: never on the main thread.
     */
    @Nullable
    public SpellDef fetchDraft(String id) throws ApiException {
        JsonElement json = api.getJson(SPELLS + "/" + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
        if (json == null || !json.isJsonObject()) return null;
        JsonObject draft = json.getAsJsonObject().deepCopy();
        draft.addProperty("spell_id", Json.str(draft, "id", id));
        draft.addProperty("version", 0);
        return SpellDef.parse(draft, catalog);
    }

    /** The newest published version of every spell, read with the catalogue. */
    private final class SpellSource implements ContentStore.Source<SpellDef> {
        @Override
        public Map<String, SpellDef> loadAll() throws ApiException {
            JsonElement cat = api.getJson(SPELLS + "/catalog");
            if (cat != null && cat.isJsonObject()) catalog = SpellCatalog.parse(cat.getAsJsonObject());
            Map<String, SpellDef> out = new LinkedHashMap<>();
            JsonElement list = api.getJson(SPELLS + "/published");
            if (list == null || !list.isJsonArray()) return out;
            for (JsonElement e : list.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                try {
                    SpellDef d = SpellDef.parse(e.getAsJsonObject(), catalog);
                    if (!d.warnings().isEmpty()) log.warning("[Magic] spell " + d.id() + ": parts skipped " + d.warnings());
                    out.put(d.id(), d);
                } catch (RuntimeException ex) {
                    log.warning("[Magic] skipped a published spell — " + ex);
                }
            }
            return out;
        }

        /** One spell: the published list is small, so it is read whole. */
        @Override
        public Optional<SpellDef> loadOne(String id) throws ApiException {
            return Optional.ofNullable(loadAll().get(id));
        }
    }

    private final class SettingsSource implements ContentStore.Source<MagicSettings> {
        @Override
        public Map<String, MagicSettings> loadAll() throws ApiException {
            JsonElement json = api.getJson("/v1/content/magic");
            JsonObject s = json != null && json.isJsonObject() ? Json.object(json.getAsJsonObject(), "settings") : new JsonObject();
            return Map.of(SETTINGS_ID, MagicSettings.parse(s));
        }

        @Override
        public Optional<MagicSettings> loadOne(String id) throws ApiException {
            return Optional.ofNullable(loadAll().get(SETTINGS_ID));
        }
    }
}
