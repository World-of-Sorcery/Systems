package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.utils.Letters_bg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BossBarTitleTest {

    private static final String CLOCK = Letters_bg.CLOCK.getLetter();

    /** The pieces of the title as drawn: their text and their own style. */
    private static List<TextComponent> pieces(Component title) {
        List<TextComponent> out = new ArrayList<>();
        for (Component child : title.children()) out.add((TextComponent) child);
        return out;
    }

    /** The style of the piece that holds the boxed glyph of {@code c}. */
    private static Style styleOf(Component title, String glyph) {
        for (TextComponent piece : pieces(title)) {
            if (piece.content().contains(glyph)) return piece.style();
        }
        return fail("no piece holds " + glyph);
    }

    private static String glyph(char letter) {
        return Letters_bg.valueOf(String.valueOf(letter)).getLetter();
    }

    @Test
    void theTitleIsTwoBoxesWithTheClockInTheSecond() {
        Component title = BossBarManager.title("Duel", "12:34");
        String all = String.join("", pieces(title).stream().map(TextComponent::content).toList());
        assertFalse(all.contains(Letters_bg.CALENDER.getLetter()), "the date box is gone");
        assertEquals(2, all.split(Letters_bg.BORDER_LEFT.getLetter(), -1).length - 1);
        assertEquals(2, all.split(Letters_bg.BORDER_RIGHT.getLetter(), -1).length - 1);
        assertTrue(all.indexOf(glyph('D')) < all.indexOf(CLOCK));
        assertTrue(all.indexOf(Letters_bg.COLON.getLetter()) > all.indexOf(CLOCK));
    }

    @Test
    void twoEntriesAreTwoPairsOfBoxesInOrderEachWithItsOwnColour() {
        Component title = BossBarManager.title(List.of(
                new BossBarManager.Entry("&6Duel", "12:34"),
                new BossBarManager.Entry("Next: &#ff8800Hunt", "2h 5m")));
        String all = String.join("", pieces(title).stream().map(TextComponent::content).toList());
        assertEquals(4, all.split(Letters_bg.BORDER_LEFT.getLetter(), -1).length - 1);
        assertEquals(2, all.split(CLOCK, -1).length - 1);
        // Duel, its clock, Next: Hunt, its clock.
        int duel = all.indexOf(glyph('D'));
        int firstClock = all.indexOf(CLOCK);
        int hunt = all.indexOf(glyph('H'));
        int secondClock = all.indexOf(CLOCK, firstClock + 1);
        assertTrue(duel < firstClock && firstClock < hunt && hunt < secondClock, all);
        assertEquals(NamedTextColor.GOLD, styleOf(title, glyph('D')).color());
        assertEquals(TextColor.fromHexString("#ff8800"), styleOf(title, glyph('H')).color());
        assertNull(styleOf(title, glyph('N')).color());
        assertNull(styleOf(title, Letters_bg.FIVE.getLetter()).color());
    }

    @Test
    void anEntryWithoutACountdownIsOnlyItsNameBox() {
        Component title = BossBarManager.title(List.of(
                new BossBarManager.Entry("&6Duel", null),
                new BossBarManager.Entry("Next: Hunt", "2h 5m")));
        String all = String.join("", pieces(title).stream().map(TextComponent::content).toList());
        assertEquals(3, all.split(Letters_bg.BORDER_LEFT.getLetter(), -1).length - 1, all);
        assertEquals(1, all.split(CLOCK, -1).length - 1, "only the next one has a clock");
        assertTrue(all.indexOf(glyph('H')) < all.indexOf(CLOCK), "the clock belongs to Next");

        String alone = String.join("", pieces(BossBarManager.title(List.of(new BossBarManager.Entry("Duel", null))))
                .stream().map(TextComponent::content).toList());
        assertFalse(alone.contains(CLOCK));
        assertEquals(1, alone.split(Letters_bg.BORDER_LEFT.getLetter(), -1).length - 1);
    }

    @Test
    void theTitleHasNoShadow() {
        assertEquals(ShadowColor.shadowColor(0), BossBarManager.title("Duel", "00:10").style().shadowColor());
    }

    @Test
    void aColourCodeColoursTheName() {
        assertEquals(NamedTextColor.GOLD, styleOf(BossBarManager.title("&6Duel", "00:10"), glyph('D')).color());
        assertEquals(NamedTextColor.GOLD, styleOf(BossBarManager.title("§6Duel", "00:10"), glyph('D')).color());
    }

    @Test
    void aHexCodeColoursTheName() {
        TextColor orange = TextColor.fromHexString("#ff8800");
        assertEquals(orange, styleOf(BossBarManager.title("&#ff8800Duel", "00:10"), glyph('D')).color());
        assertEquals(orange, styleOf(BossBarManager.title("&#FF8800Duel", "00:10"), glyph('D')).color());
        assertEquals(orange, styleOf(BossBarManager.title("#ff8800Duel", "00:10"), glyph('D')).color());
        // The code itself is not drawn.
        String all = String.join("", pieces(BossBarManager.title("&#ff8800Duel", "00:10")).stream().map(TextComponent::content).toList());
        assertFalse(all.contains(glyph('f')) || all.contains(Letters_bg.EIGHT.getLetter()), all);
    }

    @Test
    void theNamesColourStopsAtItsBox() {
        Component title = BossBarManager.title("Next: &#ff8800Duel", "12:34");
        assertNull(styleOf(title, glyph('N')).color(), "\"Next:\" keeps the default colour");
        assertNull(styleOf(title, CLOCK).color(), "the clock keeps the default colour");
        assertNull(styleOf(title, Letters_bg.TWO.getLetter()).color(), "the countdown keeps the default colour");
        assertNotEquals(TextDecoration.State.TRUE, styleOf(title, Letters_bg.TWO.getLetter()).decoration(TextDecoration.BOLD));
    }

    @Test
    void formatsWorkLikeInChat() {
        Component title = BossBarManager.title("&6&lA&rB&lC&cD", "00:10");
        assertEquals(NamedTextColor.GOLD, styleOf(title, glyph('A')).color());
        assertEquals(TextDecoration.State.TRUE, styleOf(title, glyph('A')).decoration(TextDecoration.BOLD));
        // &r resets everything.
        assertNull(styleOf(title, glyph('B')).color());
        assertNotEquals(TextDecoration.State.TRUE, styleOf(title, glyph('B')).decoration(TextDecoration.BOLD));
        // A colour after a format ends the format, as in chat.
        assertEquals(TextDecoration.State.TRUE, styleOf(title, glyph('C')).decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.RED, styleOf(title, glyph('D')).color());
        assertNotEquals(TextDecoration.State.TRUE, styleOf(title, glyph('D')).decoration(TextDecoration.BOLD));
    }

    @Test
    void anUnfinishedCodeIsDrawnAsText() {
        Component title = BossBarManager.title("Duel & #12", "00:10");
        assertNull(styleOf(title, glyph('D')).color());
        String all = String.join("", pieces(title).stream().map(TextComponent::content).toList());
        assertTrue(all.contains("&") && all.contains("#"), all);
    }

    @Test
    void theBarIsOnScreenFromJoinSoItStaysAboveTheOthers() {
        // Boss bars stack in the order a player gets them: this one must come first, at join,
        // even while nothing is scheduled, and must stay when it has nothing to show.
        BossBarManager bars = new BossBarManager();
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        bars.createBossBar(p);
        BossBar bar = bars.bar(p);
        verify(p).showBossBar(bar); // shown at join
        bars.hide(p);
        assertEquals(Component.empty(), bar.name());
        bars.show(p, "Duel", "00:10", 0.5f);
        assertEquals(0.5f, bar.progress());
        bars.hide(p);
        // Emptied, never taken away: taking it away and back would put it below the other bars.
        verify(p, never()).hideBossBar(any(BossBar.class));
        verify(p, times(1)).showBossBar(any(BossBar.class));
    }
}
