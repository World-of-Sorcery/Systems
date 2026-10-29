package me.hektortm.woSSystems.systems.magic.model;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import javax.annotation.Nullable;

/**
 * A wand skin ({@code GET /v1/content/wandskins}): replaces the wand's item model; with
 * {@code tint} the player's wand colour is applied. Owning it is the unlockable it names
 * (none: every wand owner may pick it).
 */
public record WandSkin(String id, String name, String itemModel, boolean tint, @Nullable String unlockable) {

    public static WandSkin parse(JsonObject o) {
        String unlockable = Json.str(o, "unlockable");
        return new WandSkin(Json.str(o, "id", ""), Json.str(o, "name", ""), Json.str(o, "item_model", ""),
                Json.bool(o, "tint", true), unlockable == null || unlockable.isBlank() ? null : unlockable);
    }

    /** The name players see; the id when it has none. */
    public String displayName() {
        return name.isBlank() ? id : name;
    }
}
