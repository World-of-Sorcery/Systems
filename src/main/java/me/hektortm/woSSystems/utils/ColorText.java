package me.hektortm.woSSystems.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coloured text as staff type it in holograms and displays: {@code &} / {@code §} codes and hex
 * colours written {@code <#RRGGBB>}, {@code &#RRGGBB} or {@code #RRGGBB}.
 */
public final class ColorText {

    // Matches both &#RRGGBB and #RRGGBB hex color codes (exactly 6 hex digits)
    private static final Pattern HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");

    private ColorText() {}

    /** The text as a component, every colour form above understood. */
    public static Component component(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy(text == null ? "" : text));
    }

    /**
     * Converts <#RRGGBB>, &#RRGGBB and #RRGGBB hex codes to the §x§R§R§G§G§B§B format that
     * LegacyComponentSerializer understands, then converts & to §.
     */
    static String legacy(String text) {
        // Normalise <#RRGGBB> → &#RRGGBB so the existing parser handles both forms
        text = text.replaceAll("<#([A-Fa-f0-9]{6})>", "&#$1");
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString().replace('&', '§');
    }
}
