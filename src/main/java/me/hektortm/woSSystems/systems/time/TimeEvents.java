package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.JobRules;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.time.ActivityClock.Tick;
import me.hektortm.woSSystems.utils.model.Activity;
import me.hektortm.wosCore.Utils;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Runs the time events on real time. Once a second it asks the {@link ActivityClock} what
 * started and ended, runs their interactions, and draws the boss bar: the running activity
 * with the time left, or the next one with a countdown.
 */
public class TimeEvents {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final BossBarManager bossBarManager;
    private final ActivityClock clock = new ActivityClock();
    /** Set when the time events were (re)loaded, possibly off the main thread; read by the tick. */
    private volatile boolean stale = true;

    public TimeEvents(DAOHub hub, BossBarManager bossBarManager) {
        this.hub = hub;
        this.bossBarManager = bossBarManager;
    }

    /** Starts the once-a-second run. Call once, when the plugin is enabled. */
    public void start() {
        hub.getTimeDAO().store().onChange(store -> stale = true);
        new BukkitRunnable() {
            @Override
            public void run() {
                tick(Instant.now());
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void tick(Instant now) {
        Tick tick;
        if (stale) {
            // A new schedule: what runs now is shown, but nothing is started or ended for it.
            stale = false;
            List<Activity> enabled = hub.getTimeDAO().getAllActivities().stream().filter(Activity::getIsEnabled).toList();
            tick = clock.resync(enabled, hub.getTimeDAO().zone(), now);
        } else {
            tick = clock.tick(now);
        }

        // Start / end interactions and messages are server-wide work: once per realm (live, staging).
        if (JobRules.runsScheduled()) {
            Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
            // In the order they happened; one activity's broken interaction must not cost the others theirs.
            for (ActivityClock.Change change : tick.changes()) {
                Activity activity = change.occurrence().activity();
                try {
                    if (change.start()) start(activity, online);
                    else end(activity, online);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "[Time Events] The " + (change.start() ? "start" : "end")
                            + " of '" + activity.getId() + "' failed", e);
                }
            }
        }
        draw(tick, now);
    }

    private void start(Activity activity, Collection<? extends Player> players) {
        String interaction = activity.getStartInteraction();
        String message = activity.getMessage();
        for (Player player : players) {
            if (interaction != null && !interaction.isEmpty()) {
                plugin.getInteractionManager().triggerInteraction(interaction, player, null);
            }
            if (message != null && !message.isEmpty()) {
                player.sendMessage(Utils.parseColorCodeString(message));
            }
        }
    }

    private void end(Activity activity, Collection<? extends Player> players) {
        String interaction = activity.getEndInteraction();
        if (interaction == null || interaction.isEmpty()) return;
        for (Player player : players) {
            plugin.getInteractionManager().triggerInteraction(interaction, player, null);
        }
    }

    /** The bar of every online player; it is the same for all of them. */
    private void draw(Tick tick, Instant now) {
        String nextWord = tick.next() == null ? "" : plugin.getLangManager().getMessage("activities", "bar.next");
        BarContent bar = BarContent.of(tick, now, nextWord);
        for (Player p : plugin.getServer().getOnlinePlayers()) bossBarManager.show(p, bar.entries(), bar.progress());
    }
}
