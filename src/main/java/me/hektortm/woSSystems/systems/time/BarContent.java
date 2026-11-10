package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.systems.time.ActivityClock.Tick;
import me.hektortm.woSSystems.systems.time.ActivitySchedule.Occurrence;
import me.hektortm.woSSystems.systems.time.BossBarManager.Entry;
import me.hektortm.woSSystems.utils.model.Activity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What the activity bar shows for one look of the clock: the running activity (its name only),
 * then the next one with the countdown to its start, in one box ("Next: Hunt | 2h 5m"). Either can be
 * missing; with neither the bar is empty. The bar drains with the running activity.
 *
 * @param entries  the pairs of boxes, in order
 * @param progress how full the bar is, 0 to 1
 */
public record BarContent(List<Entry> entries, float progress) {

    /** @param nextWord the language file's word before the next activity ("Next:") */
    public static BarContent of(Tick tick, Instant now, String nextWord) {
        List<Entry> entries = new ArrayList<>(2);
        float progress = 1f;
        Occurrence shown = tick.shown();
        if (shown != null) {
            Duration left = Duration.between(now, shown.end());
            long length = Duration.between(shown.start(), shown.end()).toMillis();
            progress = length <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) left.toMillis() / length));
            entries.add(new Entry(nameOf(shown.activity()), null));
        }
        Occurrence next = tick.next();
        if (next != null) {
            // One box: "Next: Hunt | 2h 5m". &r ends the name's colour before the pipe.
            String countdown = CountdownFormat.format(Duration.between(now, next.start()));
            entries.add(new Entry(nextWord + " " + nameOf(next.activity()) + "&r | " + countdown, null));
        }
        return new BarContent(List.copyOf(entries), progress);
    }

    private static String nameOf(Activity activity) {
        String name = activity.getName();
        return name == null || name.isEmpty() ? activity.getId() : name;
    }
}
