package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.systems.time.ActivitySchedule.Occurrence;
import me.hektortm.woSSystems.utils.model.Activity;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Follows real time through the schedule: which activities run now, which started or ended
 * since the last look, and which comes next. It has no clock of its own; {@link TimeEvents}
 * hands it the time once a second. Not thread safe: use it from one thread.
 *
 * <p>The first {@link #resync} is the start-up: what runs at that moment is running, and nothing
 * is reported as started (a restart during an activity must not start it a second time). A later
 * resync is a reload: the new schedule is taken as if it had been there at the last look, so an
 * activity removed while it runs just disappears (no end), one that now covers that moment runs
 * (no start), and a start or end that falls between the last look and now is still reported.
 */
public final class ActivityClock {

    /**
     * What one look found.
     *
     * @param started occurrences whose start passed since the last look, by start then id
     * @param ended   occurrences whose end passed since the last look, by end then id; one that
     *                started and ended in between is in both lists
     * @param shown   the running occurrence for the boss bar (the latest start; same start: the
     *                first id), or null
     * @param next    the next occurrence to start, or null when nothing is scheduled
     */
    public record Tick(List<Occurrence> started, List<Occurrence> ended, Occurrence shown, Occurrence next) {

        /**
         * The starts and ends in the order they happened. At the same moment ends come first, so
         * an activity that closes something is done before the one that follows it opens it again.
         */
        public List<Change> changes() {
            List<Change> out = new ArrayList<>();
            for (Occurrence o : started) out.add(new Change(o, true));
            for (Occurrence o : ended) out.add(new Change(o, false));
            out.sort(Comparator.comparing(Change::at)
                    .thenComparing(Change::start)
                    .thenComparing(c -> String.valueOf(c.occurrence().activity().getId())));
            return out;
        }
    }

    /** One start ({@code start} true) or end of an occurrence. */
    public record Change(Occurrence occurrence, boolean start) {
        public Instant at() {
            return start ? occurrence.start() : occurrence.end();
        }
    }

    private static final Comparator<Occurrence> BY_ID = Comparator.comparing(o -> String.valueOf(o.activity().getId()));
    private static final Comparator<Occurrence> BY_START = Comparator.comparing(Occurrence::start).thenComparing(BY_ID);
    private static final Comparator<Occurrence> BY_END = Comparator.comparing(Occurrence::end).thenComparing(BY_ID);
    /** Latest start first; of those, the first id. */
    private static final Comparator<Occurrence> SHOWN_FIRST = Comparator.comparing(Occurrence::start).reversed().thenComparing(BY_ID);

    private List<Activity> activities = List.of();
    private ZoneId zone;
    private Instant last;
    private final List<Occurrence> running = new ArrayList<>();
    private Occurrence next;

    /** Takes a (new) schedule: {@code enabled} are the activities that happen, {@code zone} their time zone. */
    public Tick resync(Collection<Activity> enabled, ZoneId zone, Instant now) {
        this.activities = List.copyOf(enabled);
        this.zone = zone;
        // The start-up (or a clock set back): begin at now. A reload: stay at the last look.
        if (last == null || now.isBefore(last)) last = now;
        running.clear();
        // "Contains that moment": started at or before it, ends after it.
        for (Activity a : activities) running.addAll(ActivitySchedule.between(a, zone, last, last.plusNanos(1)));
        next = findNext(last);
        return tick(now);
    }

    /** Moves on to {@code now}. Before the first {@link #resync}, and when time went backwards, nothing happens. */
    public Tick tick(Instant now) {
        if (last == null || !now.isAfter(last)) return new Tick(List.of(), List.of(), shown(), next);

        List<Occurrence> started = new ArrayList<>();
        for (Activity a : activities) {
            for (Occurrence o : ActivitySchedule.between(a, zone, last, now.plusNanos(1))) {
                if (o.start().isAfter(last) && !o.start().isAfter(now)) started.add(o);
            }
        }
        started.sort(BY_START);
        running.addAll(started);

        List<Occurrence> ended = new ArrayList<>();
        running.removeIf(o -> {
            if (o.end().isAfter(now)) return false;
            ended.add(o);
            return true;
        });
        ended.sort(BY_END);

        last = now;
        // No next means nothing will ever start with this schedule: only a passed one is looked up again.
        if (next != null && !next.start().isAfter(now)) next = findNext(now);
        return new Tick(started, ended, shown(), next);
    }

    private Occurrence shown() {
        return running.stream().min(SHOWN_FIRST).orElse(null);
    }

    private Occurrence findNext(Instant now) {
        Occurrence best = null;
        for (Activity a : activities) {
            Occurrence o = ActivitySchedule.nextAfter(a, zone, now).orElse(null);
            if (o != null && (best == null || BY_START.compare(o, best) < 0)) best = o;
        }
        return best;
    }
}
