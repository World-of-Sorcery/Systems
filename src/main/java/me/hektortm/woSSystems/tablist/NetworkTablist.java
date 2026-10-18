package me.hektortm.woSSystems.tablist;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import me.hektortm.wosCore.presence.NetworkOnline;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Shows the players on the other servers in this server's tab list as fake entries (player info
 * packets, never a real player), from {@link NetworkOnline} every 5 seconds: grey " · server" after the
 * name. The rules of who is shown are in {@link TablistRules}.
 *
 * <p>Threads: the request runs on {@link NetworkOnline}'s own thread; everything else, including the
 * packets, runs on the main thread (the answer hops back with the scheduler). A refresh that fails
 * keeps the entries for {@link TablistRules#MAX_FAILURES} refreshes in a row, then removes them all, so
 * nobody stays in the list who left long ago. Entries are removed at {@link #stop} too.</p>
 */
public final class NetworkTablist implements Listener {
    private static final long PERIOD_TICKS = 100L; // 5 s

    private final Plugin plugin;
    private final NetworkOnline online;
    private final String selfId;
    /** What the players' lists hold now: uuid to the entry. Main thread only. */
    private final Map<UUID, TablistRules.Entry> shown = new LinkedHashMap<>();
    /** Players between a login that was allowed and the join. Main thread only. */
    private final Set<UUID> arriving = new HashSet<>();
    private int failures;
    /** Whether a refresh's answer has not been handled yet. Main thread only. */
    private boolean refreshing;

    public NetworkTablist(Plugin plugin, NetworkOnline online, String selfId) {
        this.plugin = plugin;
        this.online = online;
        this.selfId = selfId;
    }

    /** Main thread, once at enable. */
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 40L, PERIOD_TICKS);
    }

    /** Main thread, at disable: takes every fake entry out of every list. */
    public void stop() {
        if (!shown.isEmpty()) {
            sendRemove(new ArrayList<>(shown.keySet()), Bukkit.getOnlinePlayers());
            shown.clear();
        }
    }

    /**
     * Skipped while the last one is unanswered: a hanging request is shared ({@link NetworkOnline#fetchAsync}),
     * so each refresh would add its own handler and one failure would count several times.
     */
    private void refresh() {
        if (refreshing) return;
        refreshing = true;
        online.fetchAsync().whenComplete((players, error) -> {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    refreshing = false;
                    if (error != null) failed();
                    else {
                        failures = 0;
                        apply(TablistRules.wanted(players, selfId, localUuids()));
                    }
                });
            } catch (IllegalStateException | org.bukkit.plugin.IllegalPluginAccessException stopping) {
                // the plugin is disabling: stop() cleans up
            }
        });
    }

    private void failed() {
        failures++;
        if (TablistRules.clearAfterFailures(failures)) apply(Map.of());
    }

    private void apply(Map<UUID, TablistRules.Entry> wanted) {
        TablistRules.Diff diff = TablistRules.diff(shown, wanted, localUuids());
        // Dropped from the book without a packet: these are real players here now.
        shown.keySet().removeIf(id -> !wanted.containsKey(id));
        if (diff.isEmpty()) return;

        var everyone = Bukkit.getOnlinePlayers();
        if (!diff.remove().isEmpty()) sendRemove(new ArrayList<>(diff.remove()), everyone);
        if (!diff.add().isEmpty()) sendAdd(diff.add(), everyone);
        if (!diff.rename().isEmpty()) sendRename(diff.rename(), everyone);
        shown.putAll(diff.add());
        shown.putAll(diff.rename());
    }

    /** Players on this server, plus those logging in (not yet in the online list, but about to be). */
    private Set<UUID> localUuids() {
        Set<UUID> ids = new HashSet<>(arriving);
        for (Player p : Bukkit.getOnlinePlayers()) ids.add(p.getUniqueId());
        return ids;
    }

    /**
     * A player logging in who is shown as a fake entry: the fake is removed from every client now,
     * before the server adds the real one (the join broadcast comes later in the login, after this
     * event). A 1.21 client keeps the profile of an entry it already holds and ignores the new one,
     * so without this the player would stay skinless for everyone who held the fake. The uuid counts
     * as local until the join (or 30 s), so a refresh in between does not show it again.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        UUID id = event.getPlayer().getUniqueId();
        arriving.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> arriving.remove(id), 600L);
        if (shown.remove(id) != null) sendRemove(List.of(id), Bukkit.getOnlinePlayers());
    }

    /** A player who joins gets the entries already shown, a tick later, after the server's own join packets. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        arriving.remove(joined.getUniqueId());
        shown.remove(joined.getUniqueId());
        if (shown.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!joined.isOnline() || shown.isEmpty()) return;
            Map<UUID, TablistRules.Entry> mine = new LinkedHashMap<>(shown);
            mine.remove(joined.getUniqueId());
            if (!mine.isEmpty()) sendAdd(mine, List.of(joined));
        });
    }

    private void sendAdd(Map<UUID, TablistRules.Entry> entries, Iterable<? extends Player> to) {
        List<PlayerInfo> infos = new ArrayList<>();
        entries.forEach((id, entry) -> infos.add(new PlayerInfo(
                new UserProfile(id, trimName(entry.name())), true, 0, GameMode.SURVIVAL,
                LegacyComponentSerializer.legacySection().deserialize(entry.text()), null)));
        send(() -> new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_LATENCY,
                        Action.UPDATE_GAME_MODE, Action.UPDATE_DISPLAY_NAME), infos), to);
    }

    private void sendRename(Map<UUID, TablistRules.Entry> entries, Iterable<? extends Player> to) {
        List<PlayerInfo> infos = new ArrayList<>();
        entries.forEach((id, entry) -> {
            PlayerInfo info = new PlayerInfo(id);
            info.setDisplayName(LegacyComponentSerializer.legacySection().deserialize(entry.text()));
            infos.add(info);
        });
        send(() -> new WrapperPlayServerPlayerInfoUpdate(EnumSet.of(Action.UPDATE_DISPLAY_NAME), infos), to);
    }

    private void sendRemove(List<UUID> ids, Iterable<? extends Player> to) {
        send(() -> new WrapperPlayServerPlayerInfoRemove(ids), to);
    }

    /** A profile name is at most 16 characters. */
    private static String trimName(String name) {
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    /** A packet is built for each player: a wrapper keeps its own buffer. */
    private void send(java.util.function.Supplier<? extends com.github.retrooper.packetevents.wrapper.PacketWrapper<?>> packet, Iterable<? extends Player> to) {
        var api = PacketEvents.getAPI();
        if (api == null) return;
        for (Player p : to) {
            try {
                var user = api.getPlayerManager().getUser(p);
                if (user != null) user.sendPacket(packet.get());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "Could not send a tab list packet to " + p.getName(), e);
            }
        }
    }
}
