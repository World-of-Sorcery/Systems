package me.hektortm.woSSystems.systems.resourcepack;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Text written with a glyph sheet ({@code {text:sheet:Quest Complete}}): each character that
 * a cell of the sheet stands for becomes that cell's glyph character. A character the sheet
 * has no cell for uses the other case's cell if there is one, else stays as it is (spaces,
 * digits …, drawn by the normal font). A sheet whose letters touch (a band like fontline) gets
 * a -1 px shift between two cells, closing the 1 px the game adds after every glyph. Pure.
 */
public final class SheetText {

    /** A sheet's letters (letter code point → the cell's glyph character) and whether they touch. */
    public record Sheet(Map<Integer, String> letters, boolean touch) {}

    private static final String CLOSE_GAP = Shift.of(-1);

    private SheetText() {}

    /** sheet → its letters, from the glyphs that are sheet cells with a letter. */
    public static Map<String, Sheet> index(Collection<Glyph> glyphs) {
        Map<String, Map<Integer, String>> letters = new HashMap<>();
        Map<String, Boolean> touch = new HashMap<>();
        for (Glyph g : glyphs) {
            if (g.sheet() == null || g.letter() == null || g.letter().isEmpty()) continue;
            letters.computeIfAbsent(g.sheet(), k -> new HashMap<>()).put(g.letter().codePointAt(0), g.character());
            touch.merge(g.sheet(), g.lettersTouch(), Boolean::logicalOr);
        }
        Map<String, Sheet> out = new HashMap<>();
        letters.forEach((sheet, map) -> out.put(sheet, new Sheet(map, touch.getOrDefault(sheet, false))));
        return out;
    }

    /** {@code text} written with a sheet's letters; null if the sheet has no letters (unknown). */
    @Nullable
    public static String write(@Nullable Sheet sheet, String text) {
        if (sheet == null || sheet.letters().isEmpty()) return null;
        Map<Integer, String> letters = sheet.letters();
        StringBuilder out = new StringBuilder(text.length() * 2);
        boolean afterCell = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            String cell = cellOf(letters, cp);
            if (cell == null) {
                out.appendCodePoint(cp);
                afterCell = false;
                continue;
            }
            if (afterCell && sheet.touch()) out.append(CLOSE_GAP);
            out.append(cell);
            afterCell = true;
        }
        return out.toString();
    }

    @Nullable
    private static String cellOf(Map<Integer, String> letters, int cp) {
        String cell = letters.get(cp);
        if (cell == null) cell = letters.get(Character.toUpperCase(cp));
        if (cell == null) cell = letters.get(Character.toLowerCase(cp));
        return cell;
    }
}
