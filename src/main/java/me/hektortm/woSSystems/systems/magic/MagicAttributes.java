package me.hektortm.woSSystems.systems.magic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;

/**
 * Magic attributes a custom item's magic component gives (spec §6.3): max mana +N, mana
 * regen +N per second, cooldowns −N %, spell damage +N %. Bonuses of several items add up.
 */
public record MagicAttributes(double maxMana, double regen, double cooldownPct, double damagePct) {

    public static final MagicAttributes NONE = new MagicAttributes(0, 0, 0, 0);

    public MagicAttributes plus(MagicAttributes o) {
        return new MagicAttributes(maxMana + o.maxMana, regen + o.regen, cooldownPct + o.cooldownPct, damagePct + o.damagePct);
    }

    /**
     * The attributes of an item's magic component (the citem data's {@code magic}, kept as
     * JSON on the item) for an item in {@code where} ({@code main_hand}, {@code off_hand},
     * {@code armor}); NONE when the component's slot is another one or it can't be read.
     */
    public static MagicAttributes parse(@Nullable String json, String where) {
        if (json == null || json.isBlank()) return NONE;
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) return NONE;
            o = e.getAsJsonObject();
        } catch (RuntimeException e) {
            return NONE;
        }
        String slot = str(o, "slot");
        if (!"any".equals(slot) && !where.equals(slot)) return NONE;
        return new MagicAttributes(num(o, "max_mana"), num(o, "mana_regen"), num(o, "cooldown_reduction"), num(o, "spell_damage"));
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "any";
    }

    private static double num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
    }
}
