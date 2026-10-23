package me.hektortm.woSSystems.systems.guis;

import me.hektortm.woSSystems.systems.resourcepack.Glyph;
import me.hektortm.woSSystems.systems.resourcepack.Shift;
import me.hektortm.woSSystems.utils.model.BackgroundRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiBackgroundTest {

    private static Glyph background(int rows) {
        return new Glyph("bg", "background", 0xE900, 222, 13, 176, rows);
    }

    private static BackgroundRule rule(int id, String glyph) {
        return new BackgroundRule(id, glyph, "all", List.of());
    }

    @Test
    void titlePrefixFor176() {
        assertEquals("&f" + "" + "&r", GuiBackground.titlePrefix(background(6)));
    }

    private static String prefix(int width, int offsetX) {
        return GuiBackground.titlePrefix(new Glyph("bg", "background", 0xE900, 222, 13, width, 6, offsetX));
    }

    @Test
    void titlePrefixFor176IsUnchanged() {
        assertEquals("&f" + Shift.of(-8) + "\uE900" + Shift.of(-169) + "&r", prefix(176, 0));
        assertEquals(prefix(176, 0), GuiBackground.titlePrefix(background(6)));
    }

    @Test
    void titlePrefixCentresAWiderPicture() {
        // W = 256: shift1 = -8 - 40 = -48, shift2 = -(-48 + 257) = -209
        assertEquals("&f" + Shift.of(-48) + "\uE900" + Shift.of(-209) + "&r", prefix(256, 0));
    }

    @Test
    void titlePrefixAppliesTheOffset() {
        // W = 200, +5: shift1 = -8 - 12 + 5 = -15, shift2 = -(-15 + 201) = -186
        assertEquals("&f" + Shift.of(-15) + "\uE900" + Shift.of(-186) + "&r", prefix(200, 5));
    }

    @Test
    void titlePrefixRoundsDownForANarrowOddPicture() {
        // W = 175: floor(-1 / 2) = -1, shift1 = -8 + 1 = -7, shift2 = -(-7 + 176) = -169
        assertEquals("&f" + Shift.of(-7) + "\uE900" + Shift.of(-169) + "&r", prefix(175, 0));
    }

    @Test
    void titlePrefixIsNullWhenAShiftIsOutOfRange() {
        // offset +2000: shift1 = -8 + 2000 = 1992, above the 1023 the shift font can draw
        assertNull(prefix(176, 2000));
        // W = 3000: shift1 = -8 - 1412 = -1420
        assertNull(prefix(3000, 0));
    }

    @Test
    void titlePrefixIsNullForAGlyphWithoutACodepoint() {
        assertNull(GuiBackground.titlePrefix(new Glyph("bg", "background", 0, 222, 13, 176, 6)));
        assertNull(GuiBackground.titlePrefix(new Glyph("bg", "background", -5, 222, 13, 176, 6)));
    }

    @Test
    void aGlyphWithoutACodepointDoesNotFit() {
        assertFalse(GuiBackground.fits(new Glyph("bg", "background", 0, 222, 13, 176, 6), 6));
        assertFalse(GuiBackground.fits(new Glyph("bg", "background", -1, 222, 13, 176, 0), 6));
    }

    @Test
    void chooseOverrideWins() {
        assertEquals("o", GuiBackground.choose("o", List.of(rule(1, "r")), r -> true, "own"));
    }

    @Test
    void chooseFirstPassingRule() {
        List<BackgroundRule> rules = List.of(rule(1, "a"), rule(2, "b"), rule(3, "c"));
        assertEquals("b", GuiBackground.choose(null, rules, r -> r.id() >= 2, "own"));
    }

    @Test
    void chooseFallsBackToOwn() {
        assertEquals("own", GuiBackground.choose(null, List.of(rule(1, "a")), r -> false, "own"));
    }

    @Test
    void chooseNoneIsNull() {
        assertNull(GuiBackground.choose(null, List.of(), r -> true, null));
    }

    @Test
    void refusesBackgroundWithOtherRows() {
        assertTrue(GuiBackground.fits(background(6), 6));
        assertFalse(GuiBackground.fits(background(6), 3));
        assertFalse(GuiBackground.fits(new Glyph("i", "icon", 0xE901, 9, 8, 9, 6), 6));
        assertFalse(GuiBackground.fits(null, 6));
        assertTrue(GuiBackground.fits(background(0), 3), "rows 0 = made for any size");
        assertTrue(GuiBackground.fits(background(0), 6));
        assertFalse(GuiBackground.fits(new Glyph("i", "icon", 0xE901, 9, 8, 9, 0), 3));
    }
}
