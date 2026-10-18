package me.hektortm.woSSystems.systems.time;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.model.Activity;
import org.bukkit.entity.Player;

import java.util.List;

public class TimeEvents {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final BossBarManager bossBarManager;

    public TimeEvents(DAOHub hub, BossBarManager bossBarManager) {
        this.hub = hub;
        this.bossBarManager = bossBarManager;
    }

    public void checkForActivity(int hour, String time, String formattedDate) {
        List<Activity> activities = hub.getTimeDAO().getAllActivities();
        String date = String.format("%02d-%02d", plugin.getTimeManager().getInGameMonth(), plugin.getTimeManager().getInGameDayOfMonth());

        boolean foundMatchingActivity = false;
        // Start/end interactions and messages are server-wide work: once per realm (live, staging).
        boolean fireEvents = me.hektortm.woSSystems.JobRules.runsScheduled();

        for (Activity activity : activities) {
            boolean isEnabled = activity.getIsEnabled();
            String activityDate = activity.getDate();
            int startTime = activity.getStartTime();
            int endTime = activity.getEndTime();
            boolean isDefault = activity.isDefault();
            String startInteraction = activity.getStartInteraction();
            String endInteraction = activity.getEndInteraction();
            if (!isEnabled) {
                continue;
            }
            boolean dateMatches = isDefault || (activityDate != null && activityDate.equals(date));
            boolean isWithinTime = hour >= startTime && hour < endTime;

            if (dateMatches && isWithinTime) {
                foundMatchingActivity = true;

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    bossBarManager.updateBossBar(player, time, formattedDate, activity.getName());
                }
            }

            // Trigger start interaction/messages only once per hour at exact start
            if (fireEvents && dateMatches && hour == startTime && plugin.getTimeManager().getInGameTimeMinutes() % 60 == 0) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (startInteraction != null && !startInteraction.isEmpty()) {
                        plugin.getInteractionManager().triggerInteraction(startInteraction, player, null);
                    }

                    String message = activity.getMessage();
                    if (message != null && !message.isEmpty()) {
                        player.sendMessage(message);
                    }
                }
            }

            // Trigger end interaction at end time
            if (fireEvents && dateMatches && hour == endTime && plugin.getTimeManager().getInGameTimeMinutes() % 60 == 0) {
                plugin.getLogger().info("[DEBUG] Triggering END for activity: " + activity.getName());
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (endInteraction != null && !endInteraction.isEmpty()) {
                        plugin.getInteractionManager().triggerInteraction(endInteraction, player, null);
                    }
                }
            }

            if (!dateMatches) {
            }
        }

        if (!foundMatchingActivity) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                bossBarManager.updateBossBar(p, time, formattedDate, null);
            }
        }
    }



}