package me.hektortm.woSSystems.systems.magic.ring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpellTooltipTest {

    @Test
    void thePlainNameHasNoColourCodes() {
        assertThat(SpellTooltip.plainName("&c&lFire#00FF00ball")).isEqualTo("Fireball");
        assertThat(SpellTooltip.plainName("<#112233>Frost &#AABBCCNova")).isEqualTo("Frost Nova");
        assertThat(SpellTooltip.plainName("Wildfire")).isEqualTo("Wildfire");
    }

    @Test
    void segmentsAreTenthsOfTheWayToTheNextLevel() {
        assertThat(SpellTooltip.filledSegments(60, 100)).isEqualTo(6);
        assertThat(SpellTooltip.filledSegments(0, 100)).isEqualTo(0);
        assertThat(SpellTooltip.filledSegments(99.9, 100)).isEqualTo(9);
        assertThat(SpellTooltip.filledSegments(150, 100)).isEqualTo(10);
        assertThat(SpellTooltip.filledSegments(-5, 100)).isEqualTo(0);
    }

    @Test
    void noXpNeededMeansAFullBar() {
        assertThat(SpellTooltip.filledSegments(5, 0)).isEqualTo(10);
        assertThat(SpellTooltip.filledSegments(5, -1)).isEqualTo(10);
    }

    @Test
    void theBarFallsBackToTextCharacters() {
        assertThat(SpellTooltip.bar(6, false, null, null)).isEqualTo("&b■■■■■■&8□□□□");
        assertThat(SpellTooltip.bar(3, false, "A", "")).isEqualTo("&b■■■&8□□□□□□□");
    }

    @Test
    void theBarUsesTheGlyphsAndGoesGoldWhenMastered() {
        assertThat(SpellTooltip.bar(10, true, "A", "B")).isEqualTo("&6AAAAAAAAAA");
        assertThat(SpellTooltip.bar(2, false, "A", "B")).isEqualTo("&bAA&8BBBBBBBB");
        assertThat(SpellTooltip.bar(0, false, "A", "B")).isEqualTo("&8BBBBBBBBBB");
    }

    @Test
    void descriptionLinesSplitAtNewLinesAndDefaultToGrey() {
        assertThat(SpellTooltip.descriptionLines("a\r\n\r\n&cb")).containsExactly("&7a", "", "&cb");
        assertThat(SpellTooltip.descriptionLines("one\ntwo")).containsExactly("&7one", "&7two");
        assertThat(SpellTooltip.descriptionLines("")).isEmpty();
        assertThat(SpellTooltip.descriptionLines(null)).isEmpty();
    }

    @Test
    void aLineThatStartsWithAColourKeepsIt() {
        assertThat(SpellTooltip.descriptionLines("#FF0000x")).containsExactly("#FF0000x");
        assertThat(SpellTooltip.descriptionLines("&#00FF00x")).containsExactly("&#00FF00x");
        assertThat(SpellTooltip.descriptionLines("<#0000FF>x")).containsExactly("<#0000FF>x");
        assertThat(SpellTooltip.descriptionLines("§ax")).containsExactly("§ax");
        assertThat(SpellTooltip.descriptionLines("&lbold")).containsExactly("&7&lbold"); // a format code is no colour
        assertThat(SpellTooltip.descriptionLines("&oslanted")).containsExactly("&7&oslanted");
        assertThat(SpellTooltip.descriptionLines("#hashtag")).containsExactly("&7#hashtag");
    }

    @Test
    void theNameIsPurpleOrGoldWithStarsWhenMastered() {
        assertThat(SpellTooltip.displayName("Fireball", false)).isEqualTo("&dFireball");
        assertThat(SpellTooltip.displayName("Fireball", true)).isEqualTo("&6✦ &6Fireball &6✦");
        assertThat(SpellTooltip.displayName("&cFireball", false)).isEqualTo("&cFireball");
        assertThat(SpellTooltip.displayName("&cFireball", true)).isEqualTo("&6✦ &r&cFireball &6✦");
        assertThat(SpellTooltip.displayName("&lFireball", false)).isEqualTo("&d&lFireball");
        assertThat(SpellTooltip.displayName("&lFireball", true)).isEqualTo("&6✦ &6&lFireball &6✦");
    }

    @Test
    void masteredNeedsLevelsAndTheTopLevel() {
        assertThat(SpellTooltip.mastered(5, 5)).isTrue();
        assertThat(SpellTooltip.mastered(7, 5)).isTrue();
        assertThat(SpellTooltip.mastered(1, 1)).isFalse();
        assertThat(SpellTooltip.mastered(3, 5)).isFalse();
    }
}
