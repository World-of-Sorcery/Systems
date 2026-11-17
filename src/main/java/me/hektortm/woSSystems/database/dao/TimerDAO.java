package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSessions;
import me.hektortm.woSSystems.systems.timers.TimerRules;
import me.hektortm.woSSystems.utils.model.Timer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static me.hektortm.woSSystems.player.ApiWriter.body;
import static me.hektortm.woSSystems.player.ApiWriter.seg;

/**
 * Timers: definitions from {@code /v1/content/timers}; each player's running
 * timers and best times live in their {@link PlayerSession} and are saved in the
 * API (spec 2026-11-17-timers-and-leaderboards). A running timer older than its
 * max duration counts as not running and is removed.
 */
public class TimerDAO {
    /** How one stop ended: the run's time, and whether it beat the player's best. */
    public record StopResult(long millis, boolean newBest) {}

    private final ContentStore<Timer> store;
    private final PlayerSessions sessions;
    private final ApiWriter writer;
    private volatile Consumer<String> onNewBest = id -> {};

    public TimerDAO(ApiServices s) {
        this.sessions = s.sessions();
        this.writer = s.writer();
        this.store = s.content().register(new ContentStore<>("timers", "Timer",
                ApiSource.flat(s.api(), "/v1/content/timers", "id", Timer::fromJson, s.log())));
        sessions.onLoad(this::dropExpired);
    }

    public @Nullable Timer getTimer(String id) { return store.get(id); }

    public Map<String, Timer> getAll() { return store.asMap(); }

    /** Called with the timer id after a new best time on this server (refreshes its leaderboards). */
    public void onNewBest(Consumer<String> hook) { this.onNewBest = hook; }

    /** Starts (or restarts) the timer now. */
    public void start(Player p, String id) {
        Instant now = Instant.now();
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s != null) {
            s.timers.put(id, now);
            s.lastRuns.remove(id);
        }
        writer.put(path(p.getUniqueId(), id), body("started_at", now.toString()));
    }

    /**
     * Stops the timer: the run's time, saved as the best when it beats it. Null
     * when the timer was not running or is past its max duration (then it is just removed).
     */
    public @Nullable StopResult stop(Player p, String id) {
        PlayerSession s = sessions.get(p.getUniqueId());
        Instant start = s == null ? null : s.timers.remove(id);
        if (start == null) return null;
        writer.delete(path(p.getUniqueId(), id));
        Instant now = Instant.now();
        if (TimerRules.expired(start, maxDuration(id), now)) return null;
        long ms = Math.max(1, TimerRules.elapsedMillis(start, now));
        s.lastRuns.put(id, ms);
        boolean newBest = TimerRules.isNewBest(s.timerBests.get(id), ms);
        if (newBest) {
            s.timerBests.put(id, ms);
            writer.put(path(p.getUniqueId(), id) + "/best", body("best_ms", ms));
            // Only once the best is saved, or the board would be read without it.
            writer.afterWrites(() -> onNewBest.accept(id));
        }
        return new StopResult(ms, newBest);
    }

    /** Removes the running timer without saving anything; false when it was not running. */
    public boolean cancel(Player p, String id) {
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s == null || s.timers.remove(id) == null) return false;
        writer.delete(path(p.getUniqueId(), id));
        return true;
    }

    /** Milliseconds the timer has run, or null when it is not running (or expired). */
    public @Nullable Long runningMillis(Player p, String id) {
        PlayerSession s = sessions.get(p.getUniqueId());
        Instant start = s == null ? null : s.timers.get(id);
        Instant now = Instant.now();
        if (start == null || TimerRules.expired(start, maxDuration(id), now)) return null;
        return TimerRules.elapsedMillis(start, now);
    }

    public @Nullable Long best(Player p, String id) {
        PlayerSession s = sessions.get(p.getUniqueId());
        return s == null ? null : s.timerBests.get(id);
    }

    public @Nullable Long lastRun(Player p, String id) {
        PlayerSession s = sessions.get(p.getUniqueId());
        return s == null ? null : s.lastRuns.get(id);
    }

    /** Forgets the player's best time of this timer (staff). */
    public void resetBest(OfflinePlayer p, String id) {
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s != null) s.timerBests.remove(id);
        writer.delete(path(p.getUniqueId(), id) + "/best");
    }

    /** Drops the expired running timers of the online players. Pure in-memory scan plus the deletes. */
    public void expireDue(Iterable<? extends Player> online) {
        for (Player p : online) {
            PlayerSession s = sessions.get(p.getUniqueId());
            if (s != null) dropExpired(s);
        }
    }

    /** Removes the best times of one timer from every loaded session (after a leaderboard reset). */
    public void clearBests(String timerId) {
        for (UUID uuid : sessions.onlineIds()) {
            PlayerSession s = sessions.get(uuid);
            if (s != null) s.timerBests.remove(timerId);
        }
    }

    private void dropExpired(PlayerSession s) {
        Instant now = Instant.now();
        for (Iterator<Map.Entry<String, Instant>> it = s.timers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Instant> e = it.next();
            if (!TimerRules.expired(e.getValue(), maxDuration(e.getKey()), now)) continue;
            it.remove();
            writer.delete(path(s.uuid(), e.getKey()));
        }
    }

    /** A timer's max duration in seconds; an unknown timer (deleted) gets the default hour. */
    private long maxDuration(String id) {
        Timer t = store.get(id);
        return t == null ? 3600 : t.getMaxDuration();
    }

    private static String path(UUID uuid, String id) {
        return "/v1/players/" + uuid + "/timers/" + seg(id);
    }
}
