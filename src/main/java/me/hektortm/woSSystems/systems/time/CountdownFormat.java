package me.hektortm.woSSystems.systems.time;

import java.time.Duration;

/** A countdown as the boss bar shows it. */
public final class CountdownFormat {
    private CountdownFormat() {
    }

    /**
     * {@code 2d 5h} from a day up, {@code 1h 23m} from an hour up, {@code 12:34} below an hour.
     * A started second counts as a whole one (the last second reads 00:01, not 00:00); a time
     * that has passed is 00:00.
     */
    public static String format(Duration left) {
        long seconds = Math.max(0, Math.floorDiv(left.toMillis() + 999, 1000));
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return String.format("%02d:%02d", minutes, seconds % 60);
    }
}
