package me.hektortm.woSSystems.core;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final WoSSystems plugin;
    private final DAOHub hub;

    public JoinListener(WoSSystems plugin, DAOHub hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isNew = !player.hasPlayedBefore();

        // Join message must be set synchronously on the event (with Redis, NetworkJoinLeaveListener replaces it).
        event.setJoinMessage(NetworkJoinLeave.joinLine(player.getName(), isNew));
        plugin.getBossBarManager().createBossBar(player);
        plugin.getRegionBossBarManager().createBossBar(player);
    }


}
