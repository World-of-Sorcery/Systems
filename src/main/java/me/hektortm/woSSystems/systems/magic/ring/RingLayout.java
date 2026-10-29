package me.hektortm.woSSystems.systems.magic.ring;

import me.hektortm.woSSystems.systems.resourcepack.Shift;

import java.util.ArrayList;
import java.util.List;

/**
 * The spell ring as title text (magic spec §5.2; proven by the 2026-10-23 spike). The game
 * draws a title 4x around the screen centre, so the ring is placed in title units (1 u =
 * 4 GUI px, origin on the crosshair). The pictures and their heights are in the resource
 * pack the API builds ({@code api/internal/resourcepack/ring.go}: same numbers): font
 * {@code wos:ring} holds the frames, shades, key badges and the shift characters, font
 * {@code wos:ring_icon_<row>} every font icon at one of the five slot heights, under the
 * icon's own character. Each part is placed with shift characters; the text's net
 * advance is 0, so the game centres it on the crosshair.
 */
public final class RingLayout {

    /** The eight slot centres (u), clockwise from the top. */
    static final int[][] POS = {{0, -12}, {8, -8}, {12, 0}, {8, 8}, {0, 12}, {-8, 8}, {-12, 0}, {-8, -8}};
    /** The heights of the icon fonts' rows. */
    static final int[] ROWS = {-12, -8, 0, 8, 12};
    static final int FRAME_W = 8;
    static final int ICON_W = 6;
    static final int BADGE_W = 4;

    /** One slot: the icon's character (-1: empty), selected, shaded (can't be cast now), its number key. */
    public record Slot(int iconCodepoint, boolean selected, boolean shaded, int key) { }

    /** A run of text in one font ("ring" or "ring_icon_<row>", namespace wos). */
    public record Part(String text, String font) { }

    private RingLayout() { }

    /** The icon font row of a ring position. */
    public static int rowOf(int pos) {
        int y = POS[pos][1];
        for (int r = 0; r < ROWS.length; r++) {
            if (ROWS[r] == y) return r;
        }
        throw new IllegalArgumentException("no row for position " + pos);
    }

    /** The title text for eight slots (position 0 = top, clockwise). */
    public static List<Part> parts(List<Slot> slots) {
        Builder b = new Builder();
        for (int p = 0; p < POS.length && p < slots.size(); p++) {
            Slot s = slots.get(p);
            int cx = POS[p][0];
            b.glyph("ring", cx - FRAME_W / 2, (s.selected() ? 0xE008 : 0xE000) + p, FRAME_W);
            if (s.iconCodepoint() >= 0) b.glyph("ring_icon_" + rowOf(p), cx - ICON_W / 2, s.iconCodepoint(), ICON_W);
            if (s.shaded()) b.glyph("ring", cx - FRAME_W / 2, 0xE010 + p, FRAME_W);
            int key = Math.max(1, Math.min(9, s.key()));
            b.glyph("ring", cx * 3 / 2 - BADGE_W / 2, 0xE100 + (key - 1) * 8 + p, BADGE_W);
        }
        b.shift(-b.pen);
        return b.finish();
    }

    /** Collects parts, merging neighbours of one font, and tracks the pen (u). */
    private static final class Builder {
        private final List<Part> parts = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private String font = "ring";
        private int pen;

        void glyph(String f, int left, int codepoint, int width) {
            shift(left - pen);
            use(f);
            text.appendCodePoint(codepoint);
            pen = left + width + 1;
        }

        /** Shift characters live in the ring font only. */
        void shift(int px) {
            if (px == 0) return;
            String s = Shift.of(px);
            if (s == null) throw new IllegalStateException("shift out of range: " + px);
            use("ring");
            text.append(s);
        }

        private void use(String f) {
            if (f.equals(font)) return;
            if (text.length() > 0) parts.add(new Part(text.toString(), font));
            text.setLength(0);
            font = f;
        }

        List<Part> finish() {
            if (text.length() > 0) parts.add(new Part(text.toString(), font));
            return parts;
        }
    }
}
