package me.hektortm.woSSystems.systems.resourcepack;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Text written with a glyph sheet ({@code {text:sheet:Quest Complete}}): each character that
 * a cell of the sheet stands for becomes that cell's glyph character. A character the sheet
 * has no cell for uses the other case's cell if there is one, else stays as it is (spaces,
 * digits …, drawn by the normal font). A sheet with a letter overlap of n px gets a -n px shift
 * between two cells: 1 closes the 1 px the game adds after every glyph (the cells touch, a band
 * like fontline), 2 pulls them 1 px over each other's edge. Pure.
 */
public final class SheetText {

    /** A sheet's letters (letter code point → the cell's glyph character) and how many px two are pulled together. */
    public record Sheet(Map<Integer, String> letters, int overlap) {}

    private SheetText() {}

    /** sheet → its letters, from the glyphs that are sheet cells with a letter. */
    public static Map<String, Sheet> index(Collection<Glyph> glyphs) {
        Map<String, Map<Integer, String>> letters = new HashMap<>();
        Map<String, Integer> overlap = new HashMap<>();
        for (Glyph g : glyphs) {
            if (g.sheet() == null || g.letter() == null || g.letter().isEmpty()) continue;
            letters.computeIfAbsent(g.sheet(), k -> new HashMap<>()).put(g.letter().codePointAt(0), g.character());
            overlap.merge(g.sheet(), g.lettersOverlap(), Math::max);
        }
        Map<String, Sheet> out = new HashMap<>();
        letters.forEach((sheet, map) -> out.put(sheet, new Sheet(map, overlap.getOrDefault(sheet, 0))));
        return out;
    }

    /** {@code text} written with a sheet's letters; null if the sheet has no letters (unknown). */
    @Nullable
    public static String write(@Nullable Sheet sheet, String text) {
        if (sheet == null || sheet.letters().isEmpty()) return null;
        Map<Integer, String> letters = sheet.letters();
        String gap = sheet.overlap() > 0 ? Shift.of(-sheet.overlap()) : null;
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
            if (afterCell && gap != null) out.append(gap);
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
