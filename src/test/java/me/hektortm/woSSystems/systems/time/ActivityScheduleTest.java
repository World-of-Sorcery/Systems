package me.hektortm.woSSystems.systems.time;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.database.dao.TimeDAO;
import me.hektortm.woSSystems.systems.time.ActivitySchedule.Occurrence;
import me.hektortm.woSSystems.utils.model.Activity;
import me.hektortm.woSSystems.utils.model.Activity.Repeat;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repeat rule. The cases in time/schedule-cases.json are shared with the portal
 * (AdminPortal/src/modules/timeevents/schedule-cases.json, read by schedule.test.ts):
 * keep the two copies identical.
 */
class ActivityScheduleTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    static Activity activity(String id, String startsAt, int minutes, Repeat repeat, int interval,
                             Set<DayOfWeek> weekdays, String until) {
        return new Activity(id, true, id, null, LocalDateTime.parse(startsAt), minutes, repeat, interval,
                weekdays, until == null ? null : LocalDate.parse(until), null, null);
    }

    @TestFactory
    List<DynamicTest> sharedCases() throws Exception {
        JsonArray cases;
        try (Reader in = new InputStreamReader(
                getClass().getResourceAsStream("/time/schedule-cases.json"), StandardCharsets.UTF_8)) {
            cases = JsonParser.parseReader(in).getAsJsonArray();
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement e : cases) {
            JsonObject c = e.getAsJsonObject();
            tests.add(DynamicTest.dynamicTest(c.get("name").getAsString(), () -> {
                Activity a = TimeDAO.fromJson(c.getAsJsonObject("schedule"));
                List<String> want = new ArrayList<>();
                c.getAsJsonArray("starts").forEach(s -> want.add(LocalDateTime.parse(s.getAsString()).toString()));
                List<String> got = ActivitySchedule.localStarts(a,
                                LocalDateTime.parse(c.get("from").getAsString()),
                                LocalDateTime.parse(c.get("to").getAsString()))
                        .stream().map(LocalDateTime::toString).toList();
                assertEquals(want, got);
            }));
        }
        assertTrue(tests.size() >= 20, "the shared cases were not found");
        return tests;
    }

    @Test
    void anOldSeriesFarFromItsStartAnswersAtOnce() {
        Activity a = activity("daily", "2020-01-01T08:00:00", 60, Repeat.DAILY, 1, Set.of(), null);
        long began = System.nanoTime();
        List<Occurrence> got = ActivitySchedule.between(a, BERLIN,
                Instant.parse("2035-06-01T00:00:00Z"), Instant.parse("2035-06-08T00:00:00Z"));
        long millis = (System.nanoTime() - began) / 1_000_000;
        assertEquals(7, got.size());
        assertTrue(millis < 50, "took " + millis + " ms");
    }

    @Test
    void aTimeInTheSpringGapMovesForward() {
        // 02:30 does not exist on 29 March 2026 in Berlin: the clocks jump from 02:00 to 03:00.
        Activity a = activity("gap", "2026-03-28T02:30:00", 30, Repeat.DAILY, 1, Set.of(), null);
        List<Occurrence> got = ActivitySchedule.between(a, BERLIN,
                Instant.parse("2026-03-29T00:00:00Z"), Instant.parse("2026-03-29T12:00:00Z"));
        assertEquals(List.of(Instant.parse("2026-03-29T01:30:00Z")), got.stream().map(Occurrence::start).toList());
        assertEquals(Instant.parse("2026-03-29T02:00:00Z"), got.get(0).end());
    }

    @Test
    void aTimeThatHappensTwiceUsesTheFirst() {
        // 02:30 happens twice on 25 October 2026 in Berlin: first at 00:30Z (summer time).
        Activity a = activity("twice", "2026-10-24T02:30:00", 30, Repeat.DAILY, 1, Set.of(), null);
        List<Occurrence> got = ActivitySchedule.between(a, BERLIN,
                Instant.parse("2026-10-24T22:00:00Z"), Instant.parse("2026-10-25T12:00:00Z"));
        assertEquals(List.of(Instant.parse("2026-10-25T00:30:00Z")), got.stream().map(Occurrence::start).toList());
    }

    @Test
    void aWeeklyEventStaysAtItsWallClockTimeAcrossSummerTime() {
        Activity a = activity("sat", "2026-03-21T20:00:00", 90, Repeat.WEEKLY, 1, Set.of(), null);
        List<Occurrence> got = ActivitySchedule.between(a, BERLIN,
                Instant.parse("2026-03-27T00:00:00Z"), Instant.parse("2026-04-05T00:00:00Z"));
        assertEquals(List.of(Instant.parse("2026-03-28T19:00:00Z"), Instant.parse("2026-04-04T18:00:00Z")),
                got.stream().map(Occurrence::start).toList());
    }

    @Test
    void betweenKeepsAnOccurrenceThatIsStillRunning() {
        Activity a = activity("late", "2026-11-14T23:00:00", 120, Repeat.NONE, 1, Set.of(), null);
        Instant during = Instant.parse("2026-11-14T23:30:00Z"); // 00:30 in Berlin, an hour and a half in
        List<Occurrence> got = ActivitySchedule.between(a, BERLIN, during, during.plusSeconds(1));
        assertEquals(1, got.size());
        assertEquals(Instant.parse("2026-11-14T22:00:00Z"), got.get(0).start());
        assertEquals(Instant.parse("2026-11-15T00:00:00Z"), got.get(0).end());
        // Over: it ended exactly at the range's start.
        assertTrue(ActivitySchedule.between(a, BERLIN, got.get(0).end(), got.get(0).end().plusSeconds(60)).isEmpty());
    }

    @Test
    void nextAfter() {
        Activity once = activity("once", "2026-11-14T20:00:00", 60, Repeat.NONE, 1, Set.of(), null);
        Instant start = Instant.parse("2026-11-14T19:00:00Z");
        assertEquals(Optional.of(start), ActivitySchedule.nextAfter(once, BERLIN, start.minusSeconds(1)).map(Occurrence::start));
        // Strictly after: at the start itself, and later, a one-off event has no next.
        assertEquals(Optional.empty(), ActivitySchedule.nextAfter(once, BERLIN, start));
        assertEquals(Optional.empty(), ActivitySchedule.nextAfter(once, BERLIN, Instant.parse("2030-01-01T00:00:00Z")));

        Activity weekly = activity("weekly", "2026-11-14T20:00:00", 60, Repeat.WEEKLY, 1, Set.of(), null);
        assertEquals(Optional.of(Instant.parse("2026-11-21T19:00:00Z")),
                ActivitySchedule.nextAfter(weekly, BERLIN, start).map(Occurrence::start));
        // Far ahead of a rare series: every four years on 29 February.
        Activity leap = activity("leap", "2024-02-29T09:00:00", 60, Repeat.YEARLY, 1, Set.of(), null);
        assertEquals(Optional.of(Instant.parse("2028-02-29T08:00:00Z")),
                ActivitySchedule.nextAfter(leap, BERLIN, Instant.parse("2024-03-01T00:00:00Z")).map(Occurrence::start));
        // A series that ended has no next.
        Activity ended = activity("ended", "2026-11-01T08:00:00", 30, Repeat.DAILY, 1, Set.of(), "2026-11-03");
        assertEquals(Optional.empty(), ActivitySchedule.nextAfter(ended, BERLIN, Instant.parse("2026-11-04T00:00:00Z")));
    }

    @Test
    void anActivityWithoutAUsableScheduleNeverHappens() {
        Activity noStart = new Activity("x", true, "x", null, null, 60, Repeat.DAILY, 1, Set.of(), null, null, null);
        Activity noInterval = activity("y", "2026-11-14T20:00:00", 60, Repeat.DAILY, 0, Set.of(), null);
        Activity noLength = activity("z", "2026-11-14T20:00:00", 0, Repeat.NONE, 1, Set.of(), null);
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        // An interval no portal can save: it must not walk off the end of the calendar and throw every second.
        Activity absurd = activity("big", "2026-10-01T20:00:00", 60, Repeat.YEARLY, 2_000_000_000, Set.of(), null);
        for (Activity a : List.of(noStart, noInterval, noLength, absurd)) {
            assertTrue(ActivitySchedule.between(a, BERLIN, from, from.plusSeconds(86_400L * 60)).isEmpty(), a.getId());
            assertEquals(Optional.empty(), ActivitySchedule.nextAfter(a, BERLIN, from), a.getId());
        }
    }
}
