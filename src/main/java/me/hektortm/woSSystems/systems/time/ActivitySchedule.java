package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.utils.model.Activity;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * When an activity happens: its repeat rule as occurrences.
 *
 * <p>The rule works on wall clock times ({@link #localStarts}); the portal has the same rule in
 * TypeScript (AdminPortal {@code schedule.ts}) and both run the cases in
 * {@code schedule-cases.json}. Turning a wall clock time into a moment is done here only, with
 * the schedule zone: a time in the spring gap moves forward, a time that happens twice uses the
 * first. An occurrence lasts real minutes.
 *
 * <ul>
 *   <li>daily: every N days from the start;</li>
 *   <li>weekly: weeks counted from the Monday of the start's week, every N weeks, one occurrence
 *       per picked weekday (none picked: the start's weekday); nothing before the start;</li>
 *   <li>monthly / yearly: the start's day of the month (and month), every N months / years; a
 *       month or year without that day is skipped;</li>
 *   <li>no occurrence starts after the last day ({@code repeatUntil}).</li>
 * </ul>
 */
public final class ActivitySchedule {
    private ActivitySchedule() {
    }

    /** One happening of an activity. */
    public record Occurrence(Activity activity, Instant start, Instant end) {
    }

    /**
     * The largest "every N" the API saves. A row with more (it could only come from outside the
     * portal) never happens, rather than walking the dates off the end of the calendar.
     */
    private static final int MAX_REPEAT_INTERVAL = 1000;

    /** The widest look ahead for a next occurrence: far enough for "every 10 years", bounded so a search ends. */
    private static final long MAX_LOOKAHEAD_DAYS = 366L * 40;

    /** The wall clock starts of the occurrences that end after {@code from} and start before {@code to}, in order. */
    public static List<LocalDateTime> localStarts(Activity a, LocalDateTime from, LocalDateTime to) {
        if (!usable(a)) return List.of();
        return starts(a, from.minusMinutes(a.getDurationMinutes()), to);
    }

    /** The occurrences that end after {@code from} and start before {@code to}, in order. */
    public static List<Occurrence> between(Activity a, ZoneId zone, Instant from, Instant to) {
        if (!usable(a)) return List.of();
        // A day of margin on both sides covers any shift a summer time change can cause.
        LocalDateTime lower = LocalDateTime.ofInstant(from, zone).minusMinutes(a.getDurationMinutes()).minusDays(1);
        LocalDateTime upper = LocalDateTime.ofInstant(to, zone).plusDays(1);
        List<Occurrence> out = new ArrayList<>();
        for (LocalDateTime local : starts(a, lower, upper)) {
            Occurrence o = occurrence(a, zone, local);
            if (o.end().isAfter(from) && o.start().isBefore(to)) out.add(o);
        }
        out.sort(Comparator.comparing(Occurrence::start));
        return out;
    }

    /** The first occurrence that starts strictly after {@code after}; empty when none ever does. */
    public static Optional<Occurrence> nextAfter(Activity a, ZoneId zone, Instant after) {
        if (!usable(a)) return Optional.empty();
        LocalDateTime lower = LocalDateTime.ofInstant(after, zone).minusDays(1);
        // Widen the window until one is found; a series that has ended is given up on at once.
        for (long days = 32; ; days = Math.min(days * 4, MAX_LOOKAHEAD_DAYS)) {
            for (LocalDateTime local : starts(a, lower, lower.plusDays(days))) {
                Occurrence o = occurrence(a, zone, local);
                if (o.start().isAfter(after)) return Optional.of(o);
            }
            if (days >= MAX_LOOKAHEAD_DAYS || over(a, lower.plusDays(days))) return Optional.empty();
        }
    }

    private static boolean usable(Activity a) {
        return a.getStartsAt() != null && a.getDurationMinutes() > 0
                && a.getRepeatInterval() >= 1 && a.getRepeatInterval() <= MAX_REPEAT_INTERVAL;
    }

    /** Nothing of the series can start at or after {@code time}. */
    private static boolean over(Activity a, LocalDateTime time) {
        if (a.getRepeat() == Activity.Repeat.NONE) return !a.getStartsAt().isAfter(time);
        return a.getRepeatUntil() != null && a.getRepeatUntil().isBefore(time.toLocalDate());
    }

    private static Occurrence occurrence(Activity a, ZoneId zone, LocalDateTime local) {
        Instant start = local.atZone(zone).toInstant();
        return new Occurrence(a, start, start.plus(Duration.ofMinutes(a.getDurationMinutes())));
    }

    /** Every start of the series strictly between {@code lower} and {@code upper}, in order. */
    private static List<LocalDateTime> starts(Activity a, LocalDateTime lower, LocalDateTime upper) {
        return switch (a.getRepeat()) {
            case NONE -> kept(a, List.of(a.getStartsAt()), lower, upper);
            case DAILY -> kept(a, daily(a, lower, upper), lower, upper);
            case WEEKLY -> kept(a, weekly(a, lower, upper), lower, upper);
            case MONTHLY -> kept(a, monthly(a, a.getRepeatInterval(), lower, upper), lower, upper);
            case YEARLY -> kept(a, monthly(a, 12L * a.getRepeatInterval(), lower, upper), lower, upper);
        };
    }

    /** The candidates that belong to the series and lie in the window. */
    private static List<LocalDateTime> kept(Activity a, List<LocalDateTime> candidates, LocalDateTime lower, LocalDateTime upper) {
        LocalDate until = a.getRepeatUntil();
        List<LocalDateTime> out = new ArrayList<>();
        for (LocalDateTime t : candidates) {
            if (t.isBefore(a.getStartsAt()) || !t.isAfter(lower) || !t.isBefore(upper)) continue;
            if (until != null && t.toLocalDate().isAfter(until)) continue;
            out.add(t);
        }
        return out;
    }

    /** How many whole periods lie before the window, less one for safety: where the walk begins. */
    private static long firstPeriod(long unitsToWindow, long step) {
        return Math.max(0, unitsToWindow / step - 1);
    }

    /** The end of the walk: the window's end, or the day after the last day if that comes first. */
    private static LocalDateTime walkEnd(Activity a, LocalDateTime upper) {
        if (a.getRepeatUntil() == null) return upper;
        LocalDateTime afterLastDay = a.getRepeatUntil().plusDays(1).atStartOfDay();
        return afterLastDay.isBefore(upper) ? afterLastDay : upper;
    }

    private static List<LocalDateTime> daily(Activity a, LocalDateTime lower, LocalDateTime upper) {
        LocalDateTime start = a.getStartsAt();
        long step = a.getRepeatInterval();
        LocalDateTime end = walkEnd(a, upper);
        List<LocalDateTime> out = new ArrayList<>();
        for (long k = firstPeriod(ChronoUnit.DAYS.between(start.toLocalDate(), lower.toLocalDate()), step); ; k++) {
            LocalDateTime t = start.plusDays(k * step);
            if (!t.isBefore(end)) return out;
            out.add(t);
        }
    }

    private static List<LocalDateTime> weekly(Activity a, LocalDateTime lower, LocalDateTime upper) {
        LocalDateTime start = a.getStartsAt();
        LocalTime time = start.toLocalTime();
        LocalDate monday = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Set<DayOfWeek> days = a.getRepeatWeekdays().isEmpty()
                ? Set.of(start.getDayOfWeek())
                : new TreeSet<>(a.getRepeatWeekdays());
        long step = a.getRepeatInterval();
        LocalDateTime end = walkEnd(a, upper);
        List<LocalDateTime> out = new ArrayList<>();
        for (long k = firstPeriod(ChronoUnit.WEEKS.between(monday, lower.toLocalDate()), step); ; k++) {
            LocalDate week = monday.plusWeeks(k * step);
            if (!week.atStartOfDay().isBefore(end)) break;
            for (DayOfWeek day : days) out.add(week.plusDays(day.getValue() - 1L).atTime(time));
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /** Every {@code step} months on the start's day of the month; yearly is 12 × N months. */
    private static List<LocalDateTime> monthly(Activity a, long step, LocalDateTime lower, LocalDateTime upper) {
        LocalDateTime start = a.getStartsAt();
        YearMonth first = YearMonth.from(start);
        int day = start.getDayOfMonth();
        LocalDateTime end = walkEnd(a, upper);
        LocalDateTime from = lower.isAfter(start) ? lower : start;
        List<LocalDateTime> out = new ArrayList<>();
        for (long k = firstPeriod(ChronoUnit.MONTHS.between(first, YearMonth.from(from)), step); ; k++) {
            YearMonth month = first.plusMonths(k * step);
            if (!month.atDay(1).atStartOfDay().isBefore(end)) return out;
            // A month without that day (31 April, 29 February) is skipped.
            if (month.isValidDay(day)) out.add(month.atDay(day).atTime(start.toLocalTime()));
        }
    }
}
