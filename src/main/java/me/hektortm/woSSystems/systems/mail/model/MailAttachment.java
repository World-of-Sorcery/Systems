package me.hektortm.woSSystems.systems.mail.model;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import javax.annotation.Nullable;

/**
 * One item in a mail. {@code kind}: citem (a custom item id and an amount, built when claimed), material (a vanilla
 * material and an amount) or stack (an exact item: {@code data} is its serialised bytes in base64, {@code summary}
 * a plain description for the portal).
 */
public record MailAttachment(String kind, String id, int amount, @Nullable String data, @Nullable Summary summary) {

    /** A stack described without Minecraft's format. {@code citem}: the custom item id, empty for a vanilla item. */
    public record Summary(String material, int amount, String name, String citem) {}

    public static MailAttachment stack(String data, Summary summary) {
        return new MailAttachment("stack", "", summary.amount(), data, summary);
    }

    public static MailAttachment of(JsonObject o) {
        Summary summary = null;
        if (o.has("summary") && o.get("summary").isJsonObject()) {
            JsonObject s = o.getAsJsonObject("summary");
            summary = new Summary(Json.str(s, "material", ""), Json.integer(s, "amount", 1), Json.str(s, "name", ""), Json.str(s, "citem", ""));
        }
        return new MailAttachment(Json.str(o, "kind", ""), Json.str(o, "id", ""), Json.integer(o, "amount", 1),
                Json.str(o, "data"), summary);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        if (!id.isEmpty()) o.addProperty("id", id);
        o.addProperty("amount", amount);
        if (data != null) o.addProperty("data", data);
        if (summary != null) {
            JsonObject s = new JsonObject();
            s.addProperty("material", summary.material());
            s.addProperty("amount", summary.amount());
            s.addProperty("name", summary.name());
            s.addProperty("citem", summary.citem());
            o.add("summary", s);
        }
        return o;
    }
}
