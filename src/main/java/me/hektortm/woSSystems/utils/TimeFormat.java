package me.hektortm.woSSystems.utils;

/**
 * How timers and timer leaderboards show a time: {@code m:ss.cc} below one hour
 * ({@code 1:23.45}), {@code h:mm:ss.cc} from one hour ({@code 1:02:03.40}).
 * Hundredths are cut off, not rounded, so a shown time is never faster than the real one.
 */
public final class TimeFormat {
    private TimeFormat() {}

    public static String millis(long ms) {
        long t = Math.max(0, ms);
        long hundredths = (t / 10) % 100;
        long seconds = (t / 1000) % 60;
        long minutes = (t / 60_000) % 60;
        long hours = t / 3_600_000;
        return hours > 0
                ? String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
                : String.format("%d:%02d.%02d", minutes, seconds, hundredths);
    }
}
