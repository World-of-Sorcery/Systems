package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.systems.time.ActivityClock.Tick;
import me.hektortm.woSSystems.systems.time.ActivitySchedule.Occurrence;
import me.hektortm.woSSystems.utils.model.Activity;
import me.hektortm.woSSystems.utils.model.Activity.Repeat;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityClockTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** A wall clock time in Berlin as an instant. */
    private static Instant at(String local) {
        return LocalDateTime.parse(local).atZone(BERLIN).toInstant();
    }

    private static Activity once(String id, String start, int minutes) {
        return ActivityScheduleTest.activity(id, start, minutes, Repeat.NONE, 1, Set.of(), null);
    }

    private static List<String> ids(List<Occurrence> occurrences) {
        return occurrences.stream().map(o -> o.activity().getId()).toList();
    }

    private static String id(Occurrence o) {
        return o == null ? null : o.activity().getId();
    }

    @Test
    void aRestartDuringAnActivityShowsItWithoutStartingItAgain() {
        ActivityClock clock = new ActivityClock();
        Tick t = clock.resync(List.of(once("a", "2026-11-14T20:00:00", 60)), BERLIN, at("2026-11-14T20:30:00"));
        assertEquals("a", id(t.shown()));
        assertTrue(t.started().isEmpty());
        assertTrue(t.ended().isEmpty());
        // And no start on the ticks after it either.
        assertTrue(clock.tick(at("2026-11-14T20:30:01")).started().isEmpty());
    }

    @Test
    void startAndEndAreEachReportedOnce() {
        ActivityClock clock = new ActivityClock();
        Activity a = once("a", "2026-11-14T20:00:00", 60);
        Activity b = once("b", "2026-11-15T10:00:00", 60);
        Tick t = clock.resync(List.of(a, b), BERLIN, at("2026-11-14T19:59:59"));
        assertNull(t.shown());
        assertEquals("a", id(t.next()));

        t = clock.tick(at("2026-11-14T20:00:00"));
        assertEquals(List.of("a"), ids(t.started()));
        assertEquals("a", id(t.shown()));
        assertEquals("b", id(t.next()));

        t = clock.tick(at("2026-11-14T20:00:01"));
        assertTrue(t.started().isEmpty());
        assertEquals("a", id(t.shown()));

        t = clock.tick(at("2026-11-14T21:00:00"));
        assertEquals(List.of("a"), ids(t.ended()));
        assertNull(t.shown());
        assertEquals("b", id(t.next()));

        t = clock.tick(at("2026-11-14T21:00:01"));
        assertTrue(t.ended().isEmpty());
    }

    @Test
    void ofOverlappingActivitiesTheLatestStartIsShown() {
        ActivityClock clock = new ActivityClock();
        Activity longOne = once("long", "2026-11-14T20:00:00", 120);
        Activity shortOne = once("short", "2026-11-14T20:30:00", 30);
        clock.resync(List.of(longOne, shortOne), BERLIN, at("2026-11-14T19:00:00"));

        assertEquals(List.of("long"), ids(clock.tick(at("2026-11-14T20:00:00")).started()));
        Tick t = clock.tick(at("2026-11-14T20:30:00"));
        assertEquals(List.of("short"), ids(t.started()));
        assertEquals("short", id(t.shown()));

        t = clock.tick(at("2026-11-14T21:00:00"));
        assertEquals(List.of("short"), ids(t.ended()));
        assertEquals("long", id(t.shown()));
    }

    @Test
    void activitiesStartingTogetherShowTheFirstById() {
        ActivityClock clock = new ActivityClock();
        clock.resync(List.of(once("b", "2026-11-14T20:00:00", 60), once("a", "2026-11-14T20:00:00", 60)), BERLIN, at("2026-11-14T19:00:00"));
        Tick t = clock.tick(at("2026-11-14T20:00:00"));
        assertEquals(List.of("a", "b"), ids(t.started()));
        assertEquals("a", id(t.shown()));
    }

    @Test
    void aTickGapOverAWholeOccurrenceReportsItsStartAndItsEnd() {
        ActivityClock clock = new ActivityClock();
        clock.resync(List.of(once("e", "2026-11-14T20:00:00", 1)), BERLIN, at("2026-11-14T19:59:59"));
        Tick t = clock.tick(at("2026-11-14T20:03:00"));
        assertEquals(List.of("e"), ids(t.started()));
        assertEquals(List.of("e"), ids(t.ended()));
        assertNull(t.shown());
        // Once: nothing again afterwards.
        t = clock.tick(at("2026-11-14T20:03:01"));
        assertTrue(t.started().isEmpty());
        assertTrue(t.ended().isEmpty());
    }

    @Test
    void anActivityRemovedWhileItRunsDisappearsWithoutAnEnd() {
        ActivityClock clock = new ActivityClock();
        clock.resync(List.of(once("a", "2026-11-14T20:00:00", 60)), BERLIN, at("2026-11-14T20:10:00"));
        Tick t = clock.resync(List.of(), BERLIN, at("2026-11-14T20:20:00"));
        assertNull(t.shown());
        assertTrue(t.ended().isEmpty());
        t = clock.tick(at("2026-11-14T21:00:00"));
        assertTrue(t.ended().isEmpty());
        assertTrue(t.started().isEmpty());
    }

    @Test
    void anActivityMovedWhileItRunsStartsAtItsNewTime() {
        ActivityClock clock = new ActivityClock();
        clock.resync(List.of(once("a", "2026-11-14T20:00:00", 60)), BERLIN, at("2026-11-14T20:10:00"));
        Tick t = clock.resync(List.of(once("a", "2026-11-14T21:10:00", 60)), BERLIN, at("2026-11-14T20:10:30"));
        assertNull(t.shown());
        assertTrue(t.started().isEmpty());
        assertEquals("a", id(t.next()));
        assertEquals(List.of("a"), ids(clock.tick(at("2026-11-14T21:10:00")).started()));
    }

    @Test
    void aReloadDoesNotSwallowAStartOrAnEndInTheSameSecond() {
        ActivityClock clock = new ActivityClock();
        Activity ending = once("ending", "2026-11-14T19:00:00", 60);
        Activity starting = once("starting", "2026-11-14T20:00:00", 60);
        clock.resync(List.of(ending, starting), BERLIN, at("2026-11-14T19:59:59"));
        // Staff save something unrelated; the next look is a reload, just after 20:00.
        Tick t = clock.resync(List.of(ending, starting, once("other", "2026-12-01T10:00:00", 60)), BERLIN, at("2026-11-14T20:00:01"));
        assertEquals(List.of("starting"), ids(t.started()));
        assertEquals(List.of("ending"), ids(t.ended()));
        assertEquals("starting", id(t.shown()));
        assertTrue(clock.tick(at("2026-11-14T20:00:02")).started().isEmpty());
    }

    @Test
    void changesComeInTheOrderTheyHappenedWithEndsBeforeStartsAtTheSameMoment() {
        ActivityClock clock = new ActivityClock();
        // a ends at 21:00 exactly when b starts; c starts and ends inside the gap, before both.
        Activity a = once("a", "2026-11-14T20:00:00", 60);
        Activity b = once("b", "2026-11-14T21:00:00", 60);
        Activity c = once("c", "2026-11-14T20:30:00", 10);
        clock.resync(List.of(a, b, c), BERLIN, at("2026-11-14T20:10:00"));
        List<String> seen = new java.util.ArrayList<>();
        clock.tick(at("2026-11-14T21:00:00")).changes().forEach(ch -> seen.add((ch.start() ? "start " : "end ") + ch.occurrence().activity().getId()));
        assertEquals(List.of("start c", "end c", "end a", "start b"), seen);
    }

    @Test
    void withNothingScheduledThereIsNothingToShow() {
        ActivityClock clock = new ActivityClock();
        Tick t = clock.resync(List.of(), BERLIN, at("2026-11-14T20:00:00"));
        assertNull(t.shown());
        assertNull(t.next());
        t = clock.tick(at("2026-11-14T20:00:01"));
        assertNull(t.shown());
        assertNull(t.next());

        // A weekly event whose last day lies before its first occurrence never happens.
        Activity never = ActivityScheduleTest.activity("never", "2026-11-09T18:00:00", 60, Repeat.WEEKLY, 1,
                Set.of(java.time.DayOfWeek.SATURDAY), "2026-11-13");
        t = clock.resync(List.of(never), BERLIN, at("2026-11-01T00:00:00"));
        assertNull(t.shown());
        assertNull(t.next());
    }

    @Test
    void aRepeatingActivityIsNextAgainAfterItEnds() {
        ActivityClock clock = new ActivityClock();
        Activity weekly = ActivityScheduleTest.activity("w", "2026-11-14T20:00:00", 60, Repeat.WEEKLY, 1, Set.of(), null);
        clock.resync(List.of(weekly), BERLIN, at("2026-11-14T19:00:00"));
        Tick t = clock.tick(at("2026-11-14T20:00:00"));
        assertEquals("w", id(t.shown()));
        assertNotNull(t.next());
        assertEquals(at("2026-11-21T20:00:00"), t.next().start());
        t = clock.tick(at("2026-11-14T21:00:00"));
        assertNull(t.shown());
        assertEquals(at("2026-11-21T20:00:00"), t.next().start());
    }

    @Test
    void aClockThatJumpsBackChangesNothing() {
        ActivityClock clock = new ActivityClock();
        clock.resync(List.of(once("a", "2026-11-14T20:00:00", 60)), BERLIN, at("2026-11-14T20:10:00"));
        Tick t = clock.tick(at("2026-11-14T19:00:00"));
        assertEquals("a", id(t.shown()));
        assertTrue(t.started().isEmpty());
        assertTrue(t.ended().isEmpty());
    }

    @Test
    void tickingBeforeTheFirstResyncIsEmpty() {
        Tick t = new ActivityClock().tick(Instant.parse("2026-11-14T20:00:00Z"));
        assertNull(t.shown());
        assertNull(t.next());
        assertTrue(t.started().isEmpty());
    }
}
