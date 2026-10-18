package me.hektortm.woSSystems.core;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.net.Envelope;
import me.hektortm.wosCore.net.NetworkBus;
import me.hektortm.wosCore.presence.NetworkOnline;
import me.hektortm.wosCore.presence.NetworkPresenceRules;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * "joined" and "left" lines for the whole network, only when a player enters or leaves it, not on a
 * switch between servers ({@link NetworkPresenceRules}). Messages on {@value #CHANNEL} are
 * {@code {uuid, name, event: "join"|"quit", announce, new}}: every join goes out at once without a line
 * ({@link #arrived}: the server the player came from must not say "left"), a network join again with
 * {@code announce} once the online list is known, and a network leave as "quit". The other servers show
 * the announced lines.
 *
 * <p>Only used while the Redis bus is on; without it the join line is the one set on the event,
 * as before ({@link JoinListener}). {@link NetworkJoinLeaveListener} feeds it from Bukkit's events.</p>
 */
public final class NetworkJoinLeave {
    /** The bus channel joins and leaves travel on. */
    public static final String CHANNEL = "wos:presence";
    /** Remote joins older than this are of no use for a quit decision and are dropped. */
    private static final Duration KEEP_JOINS = Duration.ofMinutes(1);

    private final String selfId;
    private final Executor mainThread;
    private final BiConsumer<String, JsonObject> publish;
    private final Consumer<String> broadcast;
    private final Supplier<Instant> clock;
    /** When another server last said a player joined it. */
    private final Map<UUID, Instant> joinedElsewhere = new ConcurrentHashMap<>();

    /**
     * @param selfId     this server's id; its own messages are skipped
     * @param mainThread runs a task on the main server thread
     * @param publish    publishes on the bus (channel, body)
     * @param broadcast  shows a line to everyone on this server (called on the main thread)
     * @param clock      the time now
     */
    public NetworkJoinLeave(String selfId, Executor mainThread, BiConsumer<String, JsonObject> publish,
                            Consumer<String> broadcast, Supplier<Instant> clock) {
        this.selfId = selfId;
        this.mainThread = mainThread;
        this.publish = publish;
        this.broadcast = broadcast;
        this.clock = clock;
    }

    /** Receives other servers' joins and leaves. Call once at enable. */
    public void register(NetworkBus bus) {
        bus.subscribe(CHANNEL, this::handle);
    }

    /** The line for a join; the look of the old join message. */
    public static String joinLine(String name, boolean isNew) {
        return (isNew ? "§7(New) " : "") + "§x§7§a§9§9§7§2§o" + name + " joined";
    }

    /** The line for a leave, in the same look. */
    public static String leaveLine(String name) {
        return "§x§7§a§9§9§7§2§o" + name + " left";
    }

    /** The message body. */
    public static JsonObject body(UUID uuid, String name, String event, boolean announce, boolean isNew) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        o.addProperty("name", name);
        o.addProperty("event", event);
        o.addProperty("announce", announce);
        o.addProperty("new", isNew);
        return o;
    }

    /**
     * Main thread, when this server accepts a player's login and again when they join: tells the server
     * they may have come from not to say "left" (no line anywhere). The "joined" line follows from {@link #joined}.
     */
    public void arrived(UUID uuid, String name) {
        publish.accept(CHANNEL, body(uuid, name, "join", false, false));
    }

    /**
     * Main thread, once the network's list asked for at login is known ({@code online}; null when
     * wos-api did not answer). Says "joined" everywhere when that list, asked for before the join at
     * {@code joinedAt}, does not have them on another server. A later list or none: no line
     * ({@link NetworkPresenceRules#isNetworkJoin(UUID, String, NetworkOnline.Snapshot, Instant)}).
     */
    public void joined(UUID uuid, String name, boolean isNew, @Nullable NetworkOnline.Snapshot online, Instant joinedAt) {
        if (!NetworkPresenceRules.isNetworkJoin(uuid, selfId, online, joinedAt)) return;
        publish.accept(CHANNEL, body(uuid, name, "join", true, isNew));
        broadcast.accept(joinLine(name, isNew));
    }

    /**
     * Main thread, {@link NetworkPresenceRules#QUIT_DELAY} after a player quit this server at
     * {@code quitAt}: says "left" everywhere unless they joined another server (or this one) meanwhile.
     */
    public void quitSettled(UUID uuid, String name, Instant quitAt, boolean backHere) {
        if (!NetworkPresenceRules.isNetworkQuit(quitAt, joinedElsewhere.get(uuid), backHere)) return;
        publish.accept(CHANNEL, body(uuid, name, "quit", true, false));
        broadcast.accept(leaveLine(name));
    }

    /** Called on the bus thread. */
    public void handle(Envelope env) {
        if (selfId.equals(env.from())) return;
        UUID uuid;
        String name;
        String event;
        boolean announce;
        boolean isNew;
        try {
            JsonObject b = env.body();
            uuid = UUID.fromString(b.get("uuid").getAsString());
            name = b.get("name").getAsString();
            event = b.get("event").getAsString();
            announce = b.has("announce") && b.get("announce").getAsBoolean();
            isNew = b.has("new") && b.get("new").getAsBoolean();
        } catch (RuntimeException e) {
            Logger.getLogger("WoSSystems").fine("Ignoring a bad presence message from " + env.from() + ": " + e);
            return;
        }
        switch (event) {
            case "join" -> {
                Instant now = clock.get();
                joinedElsewhere.values().removeIf(t -> t.isBefore(now.minus(KEEP_JOINS)));
                joinedElsewhere.put(uuid, now);
                if (announce) mainThread.execute(() -> broadcast.accept(joinLine(name, isNew)));
            }
            case "quit" -> mainThread.execute(() -> broadcast.accept(leaveLine(name)));
            default -> { }
        }
    }
}
