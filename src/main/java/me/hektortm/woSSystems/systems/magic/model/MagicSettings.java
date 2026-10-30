package me.hektortm.woSSystems.systems.magic.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The central magic settings ({@code GET /v1/content/magic}, the "settings" object; wos-api
 * fills every default before it stores them, the same defaults are used here for a
 * missing key). Start values apply to a player's first magic data in a realm.
 */
public record MagicSettings(double startMana, double startMaxMana, double startManaRegen, ColorRange wandColor, boolean hubEnabled) {

    /** The random wand colour's range: hue 0–360, saturation and brightness 0–100 %. */
    public record ColorRange(double hueMin, double hueMax, double satMin, double satMax, double briMin, double briMax) { }

    public static MagicSettings defaults() {
        return parse(new JsonObject());
    }

    public static MagicSettings parse(JsonObject s) {
        JsonObject c = s.has("wand_color") && s.get("wand_color").isJsonObject() ? s.getAsJsonObject("wand_color") : new JsonObject();
        return new MagicSettings(num(s, "start_mana", 100), num(s, "start_max_mana", 100), num(s, "start_mana_regen", 2),
                new ColorRange(num(c, "hue_min", 0), num(c, "hue_max", 360), num(c, "sat_min", 50), num(c, "sat_max", 100),
                        num(c, "bri_min", 60), num(c, "bri_max", 100)),
                s.has("hub_enabled") && s.get("hub_enabled").isJsonPrimitive() && s.get("hub_enabled").getAsBoolean());
    }

    private static double num(JsonObject o, String key, double fallback) {
        JsonElement v = o.get(key);
        return SpellNode.isNumber(v) ? v.getAsDouble() : fallback;
    }
}
