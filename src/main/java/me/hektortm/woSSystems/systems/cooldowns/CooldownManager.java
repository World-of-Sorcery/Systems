package me.hektortm.woSSystems.systems.cooldowns;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.database.dao.CooldownDAO;
import me.hektortm.woSSystems.systems.quests.QuestHooks;
import me.hektortm.woSSystems.utils.model.Cooldown;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.logging.Level;

/**
 * Periodic task that removes run-out cooldowns of online players once per
 * second and fires their end interactions.
 */
public class CooldownManager extends BukkitRunnable {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;

    /**
     * @param hub the DAO hub used to access cooldowns
     */
    public CooldownManager(DAOHub hub) {
        this.hub = hub;
    }

    /**
     * Tick body — runs once per second on the main thread. Expiry is an
     * in-memory scan of the online players' sessions, so this is cheap.
     */
    @Override
    public void run() {
        for (CooldownDAO.Expired e : hub.getCooldownDAO().expireDue(Bukkit.getOnlinePlayers())) {
            onCooldownExpire(Bukkit.getOfflinePlayer(e.player()), e.cooldownId());
        }
    }

    /**
     * Called on the main thread when a cooldown expires: triggers the
     * cooldown's {@code end_interaction}, if any, for the (online) player.
     *
     * @param player     the player whose cooldown expired
     * @param cooldownId the expired cooldown's definition ID
     */
    private void onCooldownExpire(OfflinePlayer player, String cooldownId) {
        plugin.writeLog("CooldownManager", Level.INFO, "Cooldown expired for player: " + player.getName() + " with ID: " + cooldownId);
        QuestHooks.report(player.getUniqueId(), "cooldown", "expired", Map.of("cooldown", cooldownId));
        Cooldown cd = hub.getCooldownDAO().getCooldown(cooldownId);
        String endInt = cd == null ? null : cd.getEnd_interaction();

        if (endInt != null) {
            Player online = player.getPlayer();
            if (online != null) plugin.getInteractionManager().triggerInteraction(endInt, online, null);
        }
    }

    /**
     * Schedules this task to run on the Bukkit scheduler every second
     * (20 ticks), starting immediately.
     */
    public void start() {
        this.runTaskTimer(plugin, 0, 20);
    }
}
