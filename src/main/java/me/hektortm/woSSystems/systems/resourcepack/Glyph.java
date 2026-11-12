package me.hektortm.woSSystems.systems.resourcepack;

import org.jetbrains.annotations.Nullable;

/**
 * A font glyph made in the portal: an icon or a GUI background, shown by one character.
 * {@code offsetX} moves a background right (+) or left (-) from the centred position (0).
 * A cell of a glyph sheet names its {@code sheet} and the {@code letter} it stands for in
 * {@code {text:sheet:…}} (both null for a plain glyph; the letter null for a cell without one);
 * {@code lettersTouch}: the sheet's cells are written without the game's 1 px gap between them.
 */
public record Glyph(String id, String kind, int codepoint, int height, int ascent, int width, int rows, int offsetX,
                    @Nullable String sheet, @Nullable String letter, boolean lettersTouch) {

    /** A glyph that is no sheet cell. */
    public Glyph(String id, String kind, int codepoint, int height, int ascent, int width, int rows, int offsetX) {
        this(id, kind, codepoint, height, ascent, width, rows, offsetX, null, null, false);
    }

    /** A glyph without an offset (centred), as an older API sends it. */
    public Glyph(String id, String kind, int codepoint, int height, int ascent, int width, int rows) {
        this(id, kind, codepoint, height, ascent, width, rows, 0);
    }

    /** The character that draws this glyph. */
    public String character() {
        return new String(Character.toChars(codepoint));
    }
}
