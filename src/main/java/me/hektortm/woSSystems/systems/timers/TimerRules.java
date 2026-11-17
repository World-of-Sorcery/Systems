package me.hektortm.woSSystems.systems.timers;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** The arithmetic of timers, kept apart from the server so it can be tested. */
public final class TimerRules {
    private TimerRules() {}

    /** Whether a timer started at {@code start} is older than its max duration at {@code now}. */
    public static boolean expired(Instant start, long maxDurationSeconds, Instant now) {
        return elapsedMillis(start, now) > maxDurationSeconds * 1000;
    }

    public static long elapsedMillis(Instant start, Instant now) {
        return Math.max(0, now.toEpochMilli() - start.toEpochMilli());
    }

    /** A run of {@code ms} is a new best when there is none yet or it is faster. */
    public static boolean isNewBest(@Nullable Long best, long ms) {
        return best == null || ms < best;
    }
}
