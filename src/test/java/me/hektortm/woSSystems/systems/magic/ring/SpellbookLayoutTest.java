package me.hektortm.woSSystems.systems.magic.ring;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpellbookLayoutTest {

    private static final Map<String, String> NAMES = Map.of("fireball", "Fireball", "wildfire", "Wildfire", "nova", "Frost Nova");
    private static final List<String> KNOWN = List.of("fireball", "nova", "wildfire");

    @Test
    void searchMatchesThePlainNameAnywhereIgnoringCase() {
        assertThat(SpellbookLayout.search(KNOWN, NAMES::get, "FIRE")).containsExactly("fireball", "wildfire");
        assertThat(SpellbookLayout.search(KNOWN, NAMES::get, " nova ")).containsExactly("nova");
        assertThat(SpellbookLayout.search(KNOWN, NAMES::get, "zzz")).isEmpty();
    }

    @Test
    void anEmptySearchKeepsEverySpell() {
        assertThat(SpellbookLayout.search(KNOWN, NAMES::get, "")).isEqualTo(KNOWN);
        assertThat(SpellbookLayout.search(KNOWN, NAMES::get, "   ")).isEqualTo(KNOWN);
        assertThat(SpellbookLayout.search(KNOWN, NAMES::get, null)).isEqualTo(KNOWN);
    }

    @Test
    void theSearchButtonSitsBottomRightOfThePicker() {
        assertThat(SpellbookLayout.SEARCH).isEqualTo(53);
        assertThat(SpellbookLayout.SEARCH_MAX).isEqualTo(32);
    }

    @Test
    void ringSlotsFollowTheRingClockwiseFromTheTop() {
        assertThat(SpellbookLayout.RING_SLOTS).containsExactly(3, 5, 16, 43, 50, 48, 37, 10);
        assertThat(SpellbookLayout.posOfSlot(3)).isEqualTo(0);
        assertThat(SpellbookLayout.posOfSlot(16)).isEqualTo(2);
        assertThat(SpellbookLayout.posOfSlot(10)).isEqualTo(7);
        assertThat(SpellbookLayout.posOfSlot(SpellbookLayout.SKIN)).isEqualTo(-1);
        assertThat(SpellbookLayout.posOfSlot(SpellbookLayout.WAND)).isEqualTo(-1);
        assertThat(SpellbookLayout.posOfSlot(SpellbookLayout.PAGE_ARROW)).isEqualTo(-1);
        assertThat(SpellbookLayout.posOfSlot(-999)).isEqualTo(-1);
    }

    @Test
    void theFixedSlotsAreTheOwnersLayout() {
        assertThat(SpellbookLayout.SKIN).isEqualTo(22);
        assertThat(SpellbookLayout.WAND).isEqualTo(31);
        assertThat(SpellbookLayout.PAGE_ARROW).isEqualTo(53);
        assertThat(new int[]{SpellbookLayout.BACK, SpellbookLayout.PREV, SpellbookLayout.CLEAR, SpellbookLayout.NEXT})
                .containsExactly(45, 48, 49, 50);
    }

    @Test
    void loadoutIndexIsPageTimesEightPlusPosition() {
        assertThat(SpellbookLayout.loadoutIndex(0, 0)).isEqualTo(0);
        assertThat(SpellbookLayout.loadoutIndex(1, 2)).isEqualTo(10);
        assertThat(SpellbookLayout.loadoutIndex(1, 7)).isEqualTo(15);
    }

    @Test
    void assigningMovesASpellFromItsOtherSlot() {
        String[] l = new String[16];
        Arrays.fill(l, "");
        l[9] = "fire";
        SpellbookLayout.assign(l, 0, "fire");
        assertThat(l[0]).isEqualTo("fire");
        assertThat(l[9]).isEmpty();
        assertThat(Arrays.stream(l).filter("fire"::equals)).hasSize(1);
    }

    @Test
    void assigningEmptyClearsTheSlot() {
        String[] l = new String[16];
        Arrays.fill(l, "");
        l[0] = "fire";
        l[3] = "ice";
        SpellbookLayout.assign(l, 0, "");
        assertThat(l[0]).isEmpty();
        assertThat(l[3]).isEqualTo("ice");
    }

    @Test
    void thePickerPagesHold45Spells() {
        assertThat(SpellbookLayout.PER_PAGE).isEqualTo(45);
        assertThat(SpellbookLayout.pickerPages(0)).isEqualTo(1);
        assertThat(SpellbookLayout.pickerPages(45)).isEqualTo(1);
        assertThat(SpellbookLayout.pickerPages(46)).isEqualTo(2);
        List<String> known = new ArrayList<>();
        for (int i = 1; i <= 46; i++) known.add("s" + i);
        assertThat(SpellbookLayout.pickerSlice(known, 0)).hasSize(45).startsWith("s1").endsWith("s45");
        assertThat(SpellbookLayout.pickerSlice(known, 1)).containsExactly("s46");
        assertThat(SpellbookLayout.pickerSlice(known, 2)).isEmpty();
        assertThat(SpellbookLayout.pickerSlice(List.of(), 0)).isEmpty();
    }
}
