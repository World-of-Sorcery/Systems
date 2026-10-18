package me.hektortm.woSSystems.player;

import com.google.gson.JsonElement;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Owns every online player's {@link PlayerSession}.
 *
 * <ul>
 *   <li><b>Login</b> ({@link AsyncPlayerPreLoginEvent}, off the main thread): the
 *       player row is upserted and the full session loaded from wos-api. If that
 *       fails the login is refused — never let a player in with empty data that
 *       could then be written over their real data.</li>
 *   <li><b>Quit</b>: the session is dropped one tick later, after every other
 *       quit handler has run.</li>
 *   <li><b>Offline players</b>: {@link #getOrFetch} loads a read-only snapshot
 *       (blocking, briefly cached) for commands that target offline players.</li>
 * </ul>
 * DAOs that keep a cross-player index subscribe with {@link #onLoad}/{@link #onUnload}.
 */
public final class PlayerSessions implements Listener {
    private static final long OFFLINE_TTL_MS = 30_000;
    static final String KICK_MESSAGE = "§cYour player data could not be loaded right now.\n§7Please try again in a moment.";

    private record Snapshot(PlayerSession session, long fetchedAt) {}

    private final Plugin plugin;
    private final WosApi api;
    private final Map<UUID, PlayerSession> online = new ConcurrentHashMap<>();
    private final Map<UUID, Snapshot> offline = new ConcurrentHashMap<>();
    private final List<Consumer<PlayerSession>> loadHooks = new CopyOnWriteArrayList<>();
    private final List<Consumer<PlayerSession>> unloadHooks = new CopyOnWriteArrayList<>();
    private final List<Consumer<PlayerSession>> resetHooks = new CopyOnWriteArrayList<>();
    private final FrozenPlayers frozen;
    private final List<Consumer<UUID>> leavingHooks = new CopyOnWriteArrayList<>();
    private final java.util.Set<UUID> leasedAtPreLogin = ConcurrentHashMap.newKeySet();
    @Nullable private volatile Handoff handoff;

    public PlayerSessions(Plugin plugin, WosApi api) {
        this.plugin = plugin;
        this.api = api;
        this.frozen = new FrozenPlayers(plugin.getLogger());
    }

    /** Players another server took over; {@link ApiWriter} drops their writes. */
    public FrozenPlayers frozen() { return frozen; }

    /** Runs after a player's session is loaded (async thread). */
    public void onLoad(Consumer<PlayerSession> hook) { loadHooks.add(hook); }

    /** Runs when a player's session is dropped (main thread). */
    public void onUnload(Consumer<PlayerSession> hook) { unloadHooks.add(hook); }

    /**
     * Runs on the main thread after an online player's session was swapped for a fresh one
     * because their test progress was reset ({@link #reloadAfterReset}): state kept outside
     * the session (quest runs …) must be dropped WITHOUT saving and rebuilt from it.
     */
    public void onReset(Consumer<PlayerSession> hook) { resetHooks.add(hook); }

    /** The online player's session, or {@code null} if not online/loaded. */
    @Nullable
    public PlayerSession get(UUID uuid) {
        return online.get(uuid);
    }

    /**
     * The online session, else a recent read-only snapshot, else a fresh blocking
     * load; {@code null} if the player is unknown or the API is unavailable.
     * Mutating a snapshot changes nothing persistent — writes go through the DAOs.
     */
    @Nullable
    public PlayerSession getOrFetch(UUID uuid) {
        PlayerSession s = online.get(uuid);
        if (s != null) return s;
        Snapshot snap = offline.get(uuid);
        if (snap != null && System.currentTimeMillis() - snap.fetchedAt() < OFFLINE_TTL_MS) return snap.session();
        try {
            PlayerSession fetched = fetch(uuid);
            offline.put(uuid, new Snapshot(fetched, System.currentTimeMillis()));
            return fetched;
        } catch (ApiException e) {
            if (!e.isNotFound()) plugin.getLogger().warning("[Sessions] offline lookup of " + uuid + " failed: " + e.getMessage());
            return null;
        }
    }

    private PlayerSession fetch(UUID uuid) throws ApiException {
        JsonElement json = api.getJson("/v1/players/" + uuid + "/session");
        return PlayerSession.fromJson(json.getAsJsonObject());
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Turns on the session hand-off between servers (set at enable, before players can join).
     * Without it (tests) logins load at once and no lease is taken.
     */
    public void useHandoff(Handoff handoff) { this.handoff = handoff; }

    /** Another server takes this player over: their later writes are dropped (see {@link FrozenPlayers}). */
    public void freeze(UUID uuid) { frozen.freeze(uuid); }

    /** Every player with a loaded session (shutdown: their leases are released). */
    public java.util.Set<UUID> onlineIds() { return java.util.Set.copyOf(online.keySet()); }

    /**
     * Registers writes a player's quit makes (temp unlockables, quest saves …). They also run,
     * on the main thread, when another server takes the player over before the quit arrives.
     */
    public void onLeaving(Consumer<UUID> hook) { leavingHooks.add(hook); }

    /**
     * True when the quit-time writes of this player already ran for a hand-off: quit handlers
     * skip them (the player is frozen then, they would only be dropped).
     */
    public boolean wasLeavingSaved(UUID uuid) {
        Handoff h = handoff;
        return h != null && h.wasLeavingSaved(uuid);
    }

    /** Runs the leaving hooks on the main thread; the future completes once their writes are queued. */
    public CompletableFuture<Void> leavingSaveOnMainThread(UUID uuid) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    for (Consumer<UUID> hook : leavingHooks) hook.accept(uuid);
                    done.complete(null);
                } catch (RuntimeException e) {
                    done.completeExceptionally(e);
                }
            });
        } catch (RuntimeException e) { // plugin disabled: no main-thread task possible
            done.completeExceptionally(e);
        }
        return done;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID uuid = event.getUniqueId();
        Handoff h = handoff;
        // Hand-off first: load only once the server the player comes from has written
        // everything and released them (blocks this async thread at most 5 s).
        if (h != null && !h.acquire(uuid, event.getName())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, KICK_MESSAGE);
            return;
        }
        frozen.unfreeze(uuid); // the lease is ours: writes for this player go out again
        try {
            api.send("PUT", "/v1/players/" + uuid, Map.of("username", event.getName()));
            PlayerSession session = fetch(uuid);
            online.put(uuid, session);
            offline.remove(uuid);
            for (Consumer<PlayerSession> hook : loadHooks) hook.accept(session);
            if (h != null) leasedAtPreLogin.add(uuid);
        } catch (ApiException e) {
            plugin.getLogger().severe("[Sessions] could not load " + event.getName() + " (" + uuid + "): " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, KICK_MESSAGE);
            releaseLease(uuid);
        }
    }

    /** Another plugin refused the pre-login after we took the lease and loaded: drop both. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        if (!leasedAtPreLogin.remove(uuid)) return;
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getPlayer(uuid) == null) unload(uuid); // unload hooks run on the main thread
        });
        releaseLease(uuid);
    }

    /** Another plugin refused the login after we loaded the session — drop it and the lease. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            UUID uuid = event.getPlayer().getUniqueId();
            unload(uuid);
            releaseLease(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getPlayer(uuid) != null) return; // re-joined within the tick: keep session and lease
            unload(uuid);
            Handoff h = handoff;
            if (h != null) h.playerLeft(uuid); // ends the freeze, releases the lease after the queued writes
            else frozen.unfreeze(uuid);
        });
    }

    /** Async: after every queued write, release the lease — unless it was taken again since now. */
    private void releaseLease(UUID uuid) {
        Handoff h = handoff;
        if (h != null) h.releaseAfterQuit(uuid, h.generation(uuid));
    }

    /**
     * The player's progress was reset in the API (test realm): mark them as seen in this realm
     * again (the same PUT as at pre-login, so the starting balance comes back at once), load
     * the session again off the main thread, then on the main thread run the reset hooks (quest
     * runs …) and tell the player. Not online here: nothing to do (the next login loads fresh
     * data). If the reload fails the old session stays; if the player left during the fetch,
     * nothing is swapped, no hook runs and no chat line is sent.
     */
    public void reloadAfterReset(String uuidText) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Sessions] player_reset with a bad id: " + uuidText);
            return;
        }
        PlayerSession current = online.get(uuid);
        if (current == null) return;
        String username = current.username();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerSession fresh;
            try {
                api.send("PUT", "/v1/players/" + uuid, Map.of("username", username));
                fresh = fetch(uuid);
            } catch (ApiException e) {
                plugin.getLogger().warning("[Sessions] reload after reset of " + uuid + " failed: " + e.getMessage());
                return;
            }
            if (online.computeIfPresent(uuid, (k, old) -> fresh) == null) return; // left meanwhile
            offline.remove(uuid);
            for (Consumer<PlayerSession> hook : loadHooks) hook.accept(fresh);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (online.get(uuid) != fresh) return; // unloaded or loaded again since
                for (Consumer<PlayerSession> hook : resetHooks) hook.accept(fresh);
                org.bukkit.entity.Player p = Bukkit.getPlayer(uuid);
                if (p != null) me.hektortm.wosCore.Utils.noPrefix(p, "stats", "player.reset-test-progress");
            });
        });
    }

    private void unload(UUID uuid) {
        PlayerSession s = online.remove(uuid);
        if (s != null) for (Consumer<PlayerSession> hook : unloadHooks) hook.accept(s);
    }
}
