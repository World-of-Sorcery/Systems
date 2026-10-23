package me.hektortm.woSSystems.systems.resourcepack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Sends the portal's resource pack to every Java player and kicks those who refuse it, or whose
 * join request fails (a failed refresh later only logs: the player keeps the pack they have). The pack
 * (url, sha1) comes from wos-api. Webhook changes are batched: many {@link #changed()} calls
 * within {@link #DEBOUNCE_MS} cause one fetch, and players get a new request only when the sha1
 * differs. Everything that touches the server or the network is injected so tests need neither.
 */
public final class ResourcePackService implements Listener {

    /** The pack's fixed id; the same on every server, so a new request replaces the old pack. */
    public static final UUID PACK_ID = UUID.fromString("5f0d3c8e-6b1a-4a57-9a43-0d6f0c1e7a01");
    static final long DEBOUNCE_MS = 10_000;
    static final long RETRY_MS = 60_000;

    public record Pack(String url, String sha1, UUID id) {
    }

    /** Blocking; never on the main thread. */
    public interface Fetcher {
        Pack fetch() throws Exception;
    }

    public interface Task {
        void cancel();
    }

    /** Runs a task off the main thread after a delay. */
    public interface Scheduler {
        Task later(Runnable task, long delayMs);
    }

    /** The server side: who is online, sending the pack, kicking with a lang key. */
    public interface Sink {
        Collection<UUID> online();

        void send(UUID player, Pack pack);

        /** @param key a key in {@code lang/resourcepack.yml}: {@code declined}, {@code failed} or {@code too-old} */
        void kick(UUID player, String key);

        /** The player's name for the log; the id when unknown. */
        default String name(UUID player) {
            return player.toString();
        }
    }

    private final Fetcher fetcher;
    private final Scheduler scheduler;
    private final ClientInfo clients;
    private final Sink sink;
    private final Logger log;

    private volatile Pack current;
    private Task pending; // guarded by this
    private long generation; // guarded by this: bumped by every schedule()
    /** Players who loaded the pack since they joined: a failure after that is a refresh, not the join request. */
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public ResourcePackService(Fetcher fetcher, Scheduler scheduler, ClientInfo clients, Sink sink) {
        this(fetcher, scheduler, clients, sink, Logger.getLogger("ResourcePack"));
    }

    public ResourcePackService(Fetcher fetcher, Scheduler scheduler, ClientInfo clients, Sink sink, Logger log) {
        this.fetcher = fetcher;
        this.scheduler = scheduler;
        this.clients = clients;
        this.sink = sink;
        this.log = log;
    }

    /** First fetch, off the main thread. Retries every minute until a pack is known. */
    public void start() {
        schedule(0);
    }

    /** The portal changed pack content: refresh once things have been quiet for 10 s. */
    public synchronized void changed() {
        schedule(DEBOUNCE_MS);
    }

    private synchronized void schedule(long delayMs) {
        if (pending != null) pending.cancel();
        long gen = ++generation;
        pending = scheduler.later(() -> refresh(gen), delayMs);
    }

    private void refresh(long gen) {
        Pack fresh;
        try {
            fresh = fetcher.fetch();
        } catch (Exception e) {
            synchronized (this) {
                // A change that came in during the fetch already has a (sooner) refresh pending: keep it.
                if (gen != generation) return;
                log.warning("Resource pack not available yet (" + e.getMessage() + "); trying again in 60 s.");
                schedule(RETRY_MS);
            }
            return;
        }
        Pack old = current;
        if (old != null && old.sha1().equalsIgnoreCase(fresh.sha1()) && old.url().equals(fresh.url())) return;
        current = fresh;
        log.info("Resource pack sha1 " + fresh.sha1());
        for (UUID id : sink.online()) {
            if (!clients.bedrock(id)) sink.send(id, fresh);
        }
    }

    /** A player joined (any thread rules: only injected collaborators are used). */
    public void join(UUID player) {
        loaded.remove(player);
        if (clients.bedrock(player)) return;
        if (PackRules.tooOld(clients.protocol(player))) {
            sink.kick(player, "too-old");
            return;
        }
        Pack pack = current;
        if (pack != null) sink.send(player, pack);
    }

    /** A player answered the request. Answers about other packs are ignored. */
    public void status(UUID player, UUID packId, PlayerResourcePackStatusEvent.Status status) {
        if (!PACK_ID.equals(packId)) return;
        if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) loaded.add(player);
        switch (PackRules.onStatus(status, !loaded.contains(player))) {
            case KICK_DECLINED -> sink.kick(player, "declined");
            case KICK_FAILED -> sink.kick(player, "failed");
            case LOG_FAILED -> log.warning("Resource pack refresh failed for " + sink.name(player) + " (" + status
                    + "); they keep their current pack.");
            default -> {
            }
        }
    }

    /** A player left: their next join starts with a join request again. */
    public void quit(UUID player) {
        loaded.remove(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        join(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        quit(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        status(e.getPlayer().getUniqueId(), e.getID(), e.getStatus());
    }

    /** Parses the API answer {@code {url, sha1, id}}. */
    static Pack parse(JsonElement json) {
        if (json == null || !json.isJsonObject()) throw new IllegalStateException("no pack in the answer");
        JsonObject o = json.getAsJsonObject();
        String url = o.has("url") && !o.get("url").isJsonNull() ? o.get("url").getAsString() : "";
        String sha1 = o.has("sha1") && !o.get("sha1").isJsonNull() ? o.get("sha1").getAsString() : "";
        if (url.isBlank() || sha1.isBlank()) throw new IllegalStateException("no pack built yet");
        UUID id = PACK_ID;
        try {
            if (o.has("id") && !o.get("id").isJsonNull()) id = UUID.fromString(o.get("id").getAsString());
        } catch (IllegalArgumentException ignored) {
            // keep the fixed id
        }
        return new Pack(url, sha1, id);
    }

    /** The real thing: wos-api for the pack, Bukkit for the rest. */
    public static ResourcePackService create(WoSSystems plugin) {
        Fetcher fetcher = () -> parse(plugin.getCore().getApi().getJson("/v1/server/resourcepack"));
        Scheduler scheduler = (task, delayMs) -> {
            var t = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, Math.max(0, delayMs / 50));
            return t::cancel;
        };
        Sink sink = new Sink() {
            @Override
            public Collection<UUID> online() {
                // Called off the main thread: Bukkit's player list is a safe snapshot.
                return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            }

            @Override
            public void send(UUID id, Pack pack) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) return;
                    ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                            .packs(ResourcePackInfo.resourcePackInfo(pack.id(), URI.create(pack.url()), pack.sha1()))
                            .required(true)
                            .replace(true)
                            .prompt(text(plugin, "prompt"))
                            .build();
                    p.sendResourcePacks(request);
                });
            }

            @Override
            public void kick(UUID id, String key) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.kick(text(plugin, key));
                });
            }

            @Override
            public String name(UUID id) {
                Player p = Bukkit.getPlayer(id);
                return p != null ? p.getName() : id.toString();
            }
        };
        return new ResourcePackService(fetcher, scheduler, ClientInfo.reflective(), sink, plugin.getLogger());
    }

    private static Component text(WoSSystems plugin, String key) {
        String message = plugin.getLangManager().getMessage("resourcepack", key);
        return LegacyComponentSerializer.legacySection().deserialize(Utils.parseColorCodeString(message));
    }
}
