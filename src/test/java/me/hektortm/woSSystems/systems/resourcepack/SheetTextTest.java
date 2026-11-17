package me.hektortm.woSSystems.systems.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SheetTextTest {

    private static Glyph cell(String id, int cp, String sheet, String letter, int overlap) {
        return new Glyph(id, "icon", cp, 7, 6, 7, 0, 0, sheet, letter, overlap);
    }

    private static final Map<String, SheetText.Sheet> INDEX = SheetText.index(List.of(
            cell("ft_h", 0xE900, "ft", "H", 0),
            cell("ft_i", 0xE901, "ft", "I", 0),
            cell("ft_excl", 0xE902, "ft", "!", 0),
            cell("ft_4", 0xE903, "ft", null, 0),
            cell("ot_a", 0xE904, "ot", "a", 0),
            cell("band_h", 0xE910, "band", "H", 1),
            cell("band_i", 0xE911, "band", "I", 1),
            cell("band_space", 0xE912, "band", " ", 1),
            cell("wide_h", 0xE920, "wide", "H", 2),
            cell("wide_i", 0xE921, "wide", "I", 2),
            new Glyph("coin", "icon", 0xE905, 9, 8, 9, 0)));

    @Test
    void indexesOnlyCellsWithLetters() {
        assertThat(INDEX).containsOnlyKeys("ft", "ot", "band", "wide");
        assertThat(INDEX.get("ft").letters()).hasSize(3);
        assertThat(INDEX.get("ft").overlap()).isZero();
        assertThat(INDEX.get("band").overlap()).isEqualTo(1);
        assertThat(INDEX.get("wide").overlap()).isEqualTo(2);
    }

    @Test
    void writesWithTheSheetAndKeepsTheRest() {
        // h has no cell of its own: the capital's; space and 2 stay as they are.
        assertThat(SheetText.write(INDEX.get("ft"), "Hi h 2!")).isEqualTo("  2");
        assertThat(SheetText.write(INDEX.get("ot"), "A")).isEqualTo("");
    }

    @Test
    void touchingLettersGetAMinusOneShiftBetweenTwoCells() {
        // -1 px (U+F801) between two cells only; the space is a cell of this sheet, "2" is not.
        assertThat(SheetText.write(INDEX.get("band"), "Hi hi")).isEqualTo("");
        assertThat(SheetText.write(INDEX.get("band"), "H2i")).isEqualTo("2");
    }

    @Test
    void overlappingLettersGetAMinusTwoShiftBetweenTwoCells() {
        String minusTwo = Shift.of(-2);
        assertThat(minusTwo).isNotEmpty().isNotEqualTo(Shift.of(-1));
        assertThat(SheetText.write(INDEX.get("wide"), "HI H")).isEqualTo("\uE920" + minusTwo + "\uE921 \uE920");
    }

    @Test
    void anUnknownSheetGivesNull() {
        assertThat(SheetText.write(INDEX.get("nope"), "Hi")).isNull();
    }
}
