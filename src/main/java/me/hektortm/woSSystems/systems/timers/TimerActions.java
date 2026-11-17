package me.hektortm.woSSystems.systems.timers;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.dao.TimerDAO;
import me.hektortm.woSSystems.systems.debug.DebugFormat;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import me.hektortm.woSSystems.utils.model.Timer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.logging.Level;

/**
 * Starting, stopping and cancelling a player's timer, for the {@code timer}
 * action and {@code /timer}: start runs the timer's start commands; a stop that
 * counted runs its finish commands, then its new-best commands when the time
 * beat the player's best. The commands run at the given binding, like an
 * interaction's actions ({@code @p} is the player).
 */
public final class TimerActions {
    private TimerActions() {}

    /** @return the stop's result for {@code stop} (null when nothing counted); null for the other verbs */
    public static @Nullable TimerDAO.StopResult run(TimerDAO timers, Player player, String verb, String id, @Nullable InteractionKey key) {
        WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
        Timer def = timers.getTimer(id);
        if (def == null) {
            plugin.writeLog("TimerActions", Level.WARNING, "timer action for an unknown timer: " + id);
            plugin.getDebugMode().tell(player, DebugFormat.note("unknown timer " + id));
            return null;
        }
        switch (verb) {
            case "start" -> {
                timers.start(player, id);
                commands(plugin, def.getStartCommands(), player, id, key, "start");
            }
            case "stop" -> {
                TimerDAO.StopResult result = timers.stop(player, id);
                if (result == null) {
                    plugin.getDebugMode().tell(player, DebugFormat.note("timer " + id + " was not running"));
                    return null;
                }
                commands(plugin, def.getFinishCommands(), player, id, key, "finish");
                if (result.newBest()) commands(plugin, def.getNewBestCommands(), player, id, key, "new best");
                return result;
            }
            case "cancel" -> timers.cancel(player, id);
            default -> plugin.writeLog("TimerActions", Level.WARNING, "unknown timer verb: " + verb);
        }
        return null;
    }

    private static void commands(WoSSystems plugin, List<String> lines, Player player, String id, @Nullable InteractionKey key, String when) {
        if (!lines.isEmpty()) plugin.getActionHandler().executeActions(player, lines, ActionHandler.SourceType.TIMER, id, key, when);
    }
}
