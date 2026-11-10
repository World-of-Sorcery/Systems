package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.systems.time.ActivityClock.Tick;
import me.hektortm.woSSystems.systems.time.ActivitySchedule.Occurrence;
import me.hektortm.woSSystems.systems.time.BossBarManager.Entry;
import me.hektortm.woSSystems.utils.model.Activity;
import me.hektortm.woSSystems.utils.model.Activity.Repeat;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the activity bar shows for a look of the clock. */
class BarContentTest {

    private static final Instant NOW = Instant.parse("2026-11-14T19:30:00Z");

    private static Occurrence occurrence(String name, Instant start, int minutes) {
        Activity a = ActivityScheduleTest.activity(name.toLowerCase(), "2026-11-14T20:00:00", minutes, Repeat.NONE, 1, Set.of(), null);
        Activity named = new Activity(a.getId(), true, name, null, a.getStartsAt(), minutes, Repeat.NONE, 1, Set.of(), null, null, null);
        return new Occurrence(named, start, start.plusSeconds(60L * minutes));
    }

    private static Tick tick(Occurrence shown, Occurrence next) {
        return new Tick(List.of(), List.of(), shown, next);
    }

    @Test
    void whileOneRunsTheNextIsShownBesideIt() {
        Occurrence running = occurrence("&6Duel", NOW.minusSeconds(30 * 60), 60);      // 30 min left
        Occurrence next = occurrence("&#ff8800Hunt", NOW.plusSeconds(2 * 3600 + 300), 60); // in 2h 5m
        BarContent bar = BarContent.of(tick(running, next), NOW, "Next:");
        assertEquals(List.of(new Entry("&6Duel", null), new Entry("Next: &#ff8800Hunt&r | 2h 5m", null)), bar.entries());
        assertEquals(0.5f, bar.progress(), 0.001f, "the bar drains with the running one");
    }

    @Test
    void whileOneRunsAndNothingElseIsComingOnlyItIsShown() {
        Occurrence running = occurrence("Duel", NOW.minusSeconds(15 * 60), 60);
        BarContent bar = BarContent.of(tick(running, null), NOW, "Next:");
        assertEquals(List.of(new Entry("Duel", null)), bar.entries());
        assertEquals(0.75f, bar.progress(), 0.001f);
    }

    @Test
    void withNothingRunningOnlyTheNextIsShown() {
        Occurrence next = occurrence("Hunt", NOW.plusSeconds(90), 60);
        BarContent bar = BarContent.of(tick(null, next), NOW, "Next:");
        assertEquals(List.of(new Entry("Next: Hunt&r | 01:30", null)), bar.entries());
        assertEquals(1f, bar.progress());
    }

    @Test
    void withNothingAtAllTheBarIsEmpty() {
        BarContent bar = BarContent.of(tick(null, null), NOW, "Next:");
        assertTrue(bar.entries().isEmpty());
    }

    @Test
    void anActivityWithoutANameShowsItsId() {
        Occurrence running = occurrence("", NOW.minusSeconds(60), 60);
        assertEquals("", running.activity().getName());
        assertEquals(running.activity().getId(), BarContent.of(tick(running, null), NOW, "Next:").entries().get(0).label());
    }
}
