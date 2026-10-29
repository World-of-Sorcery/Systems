package me.hektortm.woSSystems.systems.citems;

import me.hektortm.woSSystems.utils.Placeholders;

import java.util.Locale;

/**
 * The rules of custom items that need no server: how the portal's values are
 * read, and the custom (small caps) font.
 */
public final class CitemRules {

    /** Not placeable. */
    public static final int PLACE_NONE = 0;
    /** Placed with a small hitbox (a dead coral fan). */
    public static final int PLACE_SMALL = 1;
    /** Placed with a full-block hitbox (a barrier). */
    public static final int PLACE_NORMAL = 2;

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    /** 𝟘, the first of the ten font digits. */
    private static final int FONT_ZERO = 0x1D7D8;
    private static final String FORMAT_CODES = "0123456789abcdefklmnorx";

    /** A placed item's rotation step, in degrees. */
    private static final int TURN_STEP = 45;

    private CitemRules() {}

    /** Whether the item data marks the one wand item (magic spec §6.2): {@code "wand": true}. */
    public static boolean isWand(com.google.gson.JsonObject data) {
        com.google.gson.JsonElement w = data.get("wand");
        return w != null && w.isJsonPrimitive() && w.getAsJsonPrimitive().isBoolean() && w.getAsBoolean();
    }

    /** The item data's magic component as JSON (kept on the item for the engine), or null for none (spec §6.3). */
    @javax.annotation.Nullable
    public static String magicComponent(com.google.gson.JsonObject data) {
        com.google.gson.JsonElement m = data.get("magic");
        return m != null && m.isJsonObject() ? m.toString() : null;
    }

    /**
     * Whether placing a custom item (as a block or as a display) is refused on this server.
     * Only a server whose tag was set explicitly to {@code live} refuses. A server without a
     * tag is the pre-network legacy single server: its {@code isLive()} is true by default,
     * but it places citems today, and a release before the network exists must not stop that.
     * Hub, backend and staging may place them.
     */
    public static boolean placeRefused(me.hektortm.wosCore.server.ServerIdentity identity) {
        return identity.configured() && identity.isLive();
    }

    /**
     * The yaw of an item placed by a player looking along {@code playerYaw}: turned
     * to face them, at the nearest {@value #TURN_STEP}° step, as 0 up to (not including) 360.
     */
    public static float facingYaw(float playerYaw) {
        int steps = Math.round((playerYaw + 180f) / TURN_STEP);
        return Math.floorMod(steps * TURN_STEP, 360);
    }

    /**
     * The portal's placeable flag as the number stored on the item: "small",
     * "normal" (older items: "large"), anything else is not placeable.
     */
    public static int placeable(String flag) {
        if (flag == null) return PLACE_NONE;
        return switch (flag.trim().toLowerCase(Locale.ROOT)) {
            case "small" -> PLACE_SMALL;
            case "normal", "large" -> PLACE_NORMAL;
            default -> PLACE_NONE;
        };
    }

    /**
     * {@code text} in the custom font: letters as small caps, digits as font
     * digits. Colour codes ({@code &a}, {@code §l}), tags ({@code <#FF0000>},
     * {@code <gradient:…>}) and {@code {placeholders}} are kept as written, so
     * they still work.
     */
    public static String stylize(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() + 8);
        int i = 0;
        while (i < text.length()) {
            int keep = verbatimLength(text, i);
            if (keep > 0) {
                out.append(text, i, i + keep);
                i += keep;
                continue;
            }
            appendStyled(out, text.charAt(i));
            i++;
        }
        return out.toString();
    }

    /** How many characters from {@code i} are a code, tag or placeholder (0: none). */
    private static int verbatimLength(String text, int i) {
        char c = text.charAt(i);
        if ((c == '§' || c == '&') && i + 1 < text.length()
                && FORMAT_CODES.indexOf(Character.toLowerCase(text.charAt(i + 1))) >= 0) {
            return 2;
        }
        if (c == '<') return enclosedLength(text, i, '<', '>', false);
        if (c == '{') return enclosedLength(text, i, '{', '}', true);
        return 0;
    }

    /** The length of a tag or placeholder starting at {@code open}, or 0 if it isn't one. */
    private static int enclosedLength(String text, int open, char opening, char closing, boolean placeholder) {
        int close = text.indexOf(closing, open + 1);
        if (close < 0) return 0;
        int inner = text.indexOf(opening, open + 1);
        if (inner >= 0 && inner < close) return 0;
        String content = text.substring(open + 1, close);
        boolean matches = placeholder ? Placeholders.parse(content) != null : isTag(content);
        return matches ? close - open + 1 : 0;
    }

    /** What is between {@code <} and {@code >} of a tag: {@code #FF0000}, {@code bold}, {@code /gradient} … not "a < b > c". */
    private static boolean isTag(String content) {
        if (content.isEmpty() || content.indexOf(' ') >= 0) return false;
        char first = content.charAt(0);
        return first == '#' || first == '/' || first == '!' || isAsciiLetter(first);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static void appendStyled(StringBuilder out, char c) {
        if (isAsciiLetter(c)) out.append(SMALL_CAPS.charAt(Character.toLowerCase(c) - 'a'));
        else if (c >= '0' && c <= '9') out.appendCodePoint(FONT_ZERO + (c - '0'));
        else out.append(c);
    }
}
