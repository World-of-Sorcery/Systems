package me.hektortm.woSSystems.systems.timers;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.dao.TimerDAO;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

/** Once a second, drops the online players' running timers that are past their max duration. */
public class TimerManager extends BukkitRunnable {
    private final TimerDAO timers;

    public TimerManager(TimerDAO timers) {
        this.timers = timers;
    }

    @Override
    public void run() {
        timers.expireDue(Bukkit.getOnlinePlayers());
    }

    /** Runs every second (20 ticks), starting now. */
    public void start() {
        this.runTaskTimer(WoSSystems.getPlugin(WoSSystems.class), 0, 20);
    }
}
