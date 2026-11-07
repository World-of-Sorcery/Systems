package me.hektortm.woSSystems.systems.mail.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import javax.annotation.Nullable;

/**
 * How a mail (or a mailbox button) looks: {@code kind} is material, citem, head (texture or player name) or model
 * (an item model of the resource pack), {@code id} names it.
 */
public record MailIcon(String kind, String id) {

    /** The icon in a JSON value, or null for null, a missing value or one without an id. */
    @Nullable
    public static MailIcon of(@Nullable JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        String id = Json.str(o, "id", "");
        return id.isBlank() ? null : new MailIcon(Json.str(o, "kind", "material"), id);
    }
}
