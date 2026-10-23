package me.hektortm.woSSystems.systems.resourcepack;

/**
 * A font glyph made in the portal: an icon or a GUI background, shown by one character.
 * {@code offsetX} moves a background right (+) or left (-) from the centred position (0).
 */
public record Glyph(String id, String kind, int codepoint, int height, int ascent, int width, int rows, int offsetX) {

    /** A glyph without an offset (centred), as an older API sends it. */
    public Glyph(String id, String kind, int codepoint, int height, int ascent, int width, int rows) {
        this(id, kind, codepoint, height, ascent, width, rows, 0);
    }

    /** The character that draws this glyph. */
    public String character() {
        return new String(Character.toChars(codepoint));
    }
}
