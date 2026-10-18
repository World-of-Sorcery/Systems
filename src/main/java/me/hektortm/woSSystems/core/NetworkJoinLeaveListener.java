package me.hektortm.woSSystems.core;

import me.hektortm.wosCore.net.NetworkBus;
import me.hektortm.wosCore.presence.NetworkOnline;
import me.hektortm.wosCore.presence.NetworkPresenceRules;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feeds {@link NetworkJoinLeave} from Bukkit's events while the Redis bus is on (without it nothing
 * changes: {@link JoinListener} sets the join line, there is no leave line).
 *
 * <p>The network's online list is asked for at pre-login: this server reports the player as its own
 * as soon as they join, and after that the list no longer shows the server they came from.</p>
 */
public final class NetworkJoinLeaveListener implements Listener {
    private static final long QUIT_DELAY_TICKS = NetworkPresenceRules.QUIT_DELAY.toMillis() / 50;

    private final Plugin plugin;
    private final NetworkBus bus;
    private final @Nullable NetworkOnline online;
    private final NetworkJoinLeave rules;
    private final Map<UUID, CompletableFuture<NetworkOnline.Snapshot>> atLogin = new ConcurrentHashMap<>();

    public NetworkJoinLeaveListener(Plugin plugin, NetworkBus bus, @Nullable NetworkOnline online, NetworkJoinLeave rules) {
        this.plugin = plugin;
        this.bus = bus;
        this.online = online;
        this.rules = rules;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!bus.isEnabled() || online == null || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        atLogin.put(event.getUniqueId(), online.fetchSnapshot());
    }

    /**
     * A login this server accepted: tells the server the player comes from not to say "left". Sent here,
     * before the configuration phase, because the old server may see the quit before this server's join
     * (a slow resource pack); sent again at the join for the other order ({@link #onJoin}). A login refused
     * after this means Velocity sends them back to the old server, where {@code backHere} covers it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (!bus.isEnabled() || event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        rules.arrived(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    /** After {@link JoinListener} (NORMAL): replaces its line with the network's. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (!bus.isEnabled()) return;
        event.joinMessage(null);
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        boolean isNew = !player.hasPlayedBefore();
        Instant joinedAt = Instant.now();
        // Again at the join: if the old server only lets go once the configuration phase is over, the
        // login's arrival can be older than JOIN_GRACE by the time it sees the quit.
        rules.arrived(uuid, name);
        CompletableFuture<NetworkOnline.Snapshot> list = atLogin.remove(uuid);
        // No list from pre-login (a reload): one asked for now comes after the join and can't tell; no line.
        if (list == null) return;
        // wos-api not answering: not known, so no "joined" (a switch must never look like a join).
        list.handle((snapshot, err) -> err == null ? snapshot : null)
                .thenAccept(snapshot -> onMain(() -> rules.joined(uuid, name, isNew, snapshot, joinedAt)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!bus.isEnabled()) return;
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        atLogin.remove(uuid);
        Instant quitAt = Instant.now();
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> rules.quitSettled(uuid, name, quitAt, Bukkit.getPlayer(uuid) != null), QUIT_DELAY_TICKS);
    }

    private void onMain(Runnable task) {
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalStateException | org.bukkit.plugin.IllegalPluginAccessException disabled) {
            // the plugin is shutting down
        }
    }
}
