package me.hektortm.woSSystems.database.dao;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentRegistry;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.content.MissingAsEmpty;
import me.hektortm.woSSystems.systems.resourcepack.Glyph;
import me.hektortm.woSSystems.systems.resourcepack.SheetText;
import me.hektortm.wosCore.api.WosApi;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.logging.Logger;

/**
 * The portal's font glyphs, served from wos-api ({@code /v1/content/rpglyphs/published}). An API
 * without glyphs (404) gives none instead of failing the preload, so players can still join; a
 * glyph without a usable codepoint is skipped (and logged) as if it were not published. The
 * letters of glyph sheets are indexed for {@code {text:sheet:…}}, rebuilt on every change.
 */
public class GlyphDAO {
    private final ContentStore<Glyph> store;
    private volatile Map<String, SheetText.Sheet> sheets = Map.of();

    public GlyphDAO(ContentRegistry registry, WosApi api, Logger log) {
        this.store = registry.register(new ContentStore<>("rpglyphs", "Glyph", MissingAsEmpty.of(
                ApiSource.flat(api, "/v1/content/rpglyphs/published", "id", GlyphDAO::map, log), "rpglyphs", log)));
        store.onChange(s -> sheets = SheetText.index(s.all()));
    }

    static Glyph map(JsonObject j) {
        int codepoint = Json.integer(j, "codepoint", 0);
        if (codepoint <= 0 || !Character.isValidCodePoint(codepoint)) {
            throw new IllegalArgumentException("no usable codepoint (" + codepoint + ")");
        }
        return new Glyph(Json.str(j, "id"), Json.str(j, "kind", "icon"), codepoint, Json.integer(j, "height", 0),
                Json.integer(j, "ascent", 0), Json.integer(j, "width", 0),
                Json.integer(j, "rows", 0), Json.integer(j, "offset_x", 0),
                blankToNull(Json.str(j, "sheet", null)), blankToNull(Json.str(j, "letter", null)),
                Json.bool(j, "letters_touch", false));
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** The glyph, or {@code null} if no such glyph is published. */
    @Nullable
    public Glyph get(String id) {
        return store.get(id);
    }

    /** {@code text} written with a glyph sheet's letters, or {@code null} if the sheet has none. */
    @Nullable
    public String sheetText(String sheet, String text) {
        return SheetText.write(sheets.get(sheet), text);
    }
}
