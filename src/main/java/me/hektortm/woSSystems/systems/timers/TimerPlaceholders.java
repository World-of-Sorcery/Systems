package me.hektortm.woSSystems.systems.timers;

import me.hektortm.woSSystems.utils.TimeFormat;
import org.jetbrains.annotations.Nullable;

/**
 * The text of {@code {timers.duration|seconds|best|last:<id>}}. An empty text
 * when there is nothing to show (no running timer, no best yet), so a hologram
 * line can stay blank; null for a key timers don't have (left as written).
 */
public final class TimerPlaceholders {
    private TimerPlaceholders() {}

    public static @Nullable String value(String key, @Nullable Long runningMs, @Nullable Long bestMs, @Nullable Long lastMs) {
        return switch (key) {
            case "duration" -> runningMs == null ? "" : TimeFormat.millis(runningMs);
            case "seconds" -> runningMs == null ? "" : String.valueOf(runningMs / 1000);
            case "best" -> bestMs == null ? "" : TimeFormat.millis(bestMs);
            case "last" -> lastMs == null ? "" : TimeFormat.millis(lastMs);
            default -> null;
        };
    }
}
