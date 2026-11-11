package me.hektortm.woSSystems.systems.magic.ring;

import me.hektortm.woSSystems.utils.ColorText;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The text of a spell in the spellbook (spec 2026-11-11 §6, §7), as {@code &} colour text
 * for {@code ColorText}. Pure, so it is tested without a server.
 */
public final class SpellTooltip {

    static final int SEGMENTS = 10;
    private static final String FALLBACK_FULL = "■";
    private static final String FALLBACK_EMPTY = "□";
    /**
     * A line that sets its own colour first: {@code &c}, {@code §c}, {@code §x…}, {@code &#RRGGBB},
     * {@code <#RRGGBB>}, {@code #RRGGBB}. Format codes ({@code &l}, {@code &o} …) are no colour.
     */
    private static final Pattern STARTS_COLOURED = Pattern.compile(
            "^(?:[&§][0-9a-fA-FxX]|&#[0-9a-fA-F]{6}|<#[0-9a-fA-F]{6}>|#[0-9a-fA-F]{6}).*", Pattern.DOTALL);

    private SpellTooltip() { }

    /** Filled bar segments (0–10) for {@code xp} of {@code toNext}; nothing to gather = full. */
    static int filledSegments(double xp, double toNext) {
        if (toNext <= 0) return SEGMENTS;
        int filled = (int) Math.floor(xp / toNext * SEGMENTS);
        return Math.max(0, Math.min(SEGMENTS, filled));
    }

    /**
     * The bar: filled part aqua (gold when mastered), empty part dark grey. The two glyphs
     * are used only when both are set; otherwise ■ and □.
     */
    static String bar(int filled, boolean mastered, @Nullable String fullGlyph, @Nullable String emptyGlyph) {
        boolean glyphs = fullGlyph != null && !fullGlyph.isEmpty() && emptyGlyph != null && !emptyGlyph.isEmpty();
        String full = glyphs ? fullGlyph : FALLBACK_FULL;
        String empty = glyphs ? emptyGlyph : FALLBACK_EMPTY;
        StringBuilder b = new StringBuilder();
        if (filled > 0) b.append(mastered ? "&6" : "&b").append(full.repeat(filled));
        if (filled < SEGMENTS) b.append("&8").append(empty.repeat(SEGMENTS - filled));
        return b.toString();
    }

    /** One lore line per line of the description; a line without its own colour starts grey. */
    static List<String> descriptionLines(@Nullable String description) {
        List<String> out = new ArrayList<>();
        if (description == null || description.isEmpty()) return out;
        for (String line : description.split("\r?\n", -1)) {
            out.add(line.isEmpty() || STARTS_COLOURED.matcher(line).matches() ? line : "&7" + line);
        }
        return out;
    }

    /** The item name: the spell's own colours, else light purple; mastered adds gold stars. */
    static String displayName(String name, boolean mastered) {
        boolean coloured = STARTS_COLOURED.matcher(name).matches();
        if (!mastered) return coloured ? name : "&d" + name;
        return "&6✦ " + (coloured ? "&r" : "&6") + name + " &6✦";
    }

    /** The name as players read it: every colour and format code taken out (for the search). */
    static String plainName(String name) {
        return PlainTextComponentSerializer.plainText().serialize(ColorText.component(name));
    }

    /** Mastered: the spell has levels and the player is at (or past) the top one. */
    static boolean mastered(int level, int maxLevel) {
        return maxLevel > 1 && level >= maxLevel;
    }
}
