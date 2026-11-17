package me.hektortm.woSSystems.systems.leaderboards;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.net.Envelope;
import me.hektortm.wosCore.net.NetworkBus;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * A leaderboard locked, unlocked, reset or ended on one server, told to the
 * others of its realm on {@value #CHANNEL}. They read the board again; after a
 * reset or an end they first clear the reset data from their online sessions,
 * so a session still holding an old stat can't write it back (the plugin saves
 * stats as absolute values). Without Redis nothing is sent: one server needs nothing.
 */
public final class NetworkLeaderboards {
    public static final String CHANNEL = "wos:leaderboard";

    private final String selfId;
    private final String realm;
    private final Executor mainThread;
    private final Consumer<String> clearSource;
    private final Consumer<String> refresh;

    /**
     * @param clearSource removes a board's source data from this server's sessions (called on the main thread)
     * @param refresh     reads a board again (called on the main thread; it reads off it itself)
     */
    public NetworkLeaderboards(String selfId, String realm, Executor mainThread, Consumer<String> clearSource, Consumer<String> refresh) {
        this.selfId = selfId;
        this.realm = realm;
        this.mainThread = mainThread;
        this.clearSource = clearSource;
        this.refresh = refresh;
    }

    /** Receives other servers' changes. A disabled bus never calls it. Call once at enable. */
    public void register(NetworkBus bus) {
        bus.subscribe(CHANNEL, this::handle);
    }

    /** Tells the other servers. A no-op without Redis. */
    public static void publish(NetworkBus bus, String realm, String id, String action) {
        if (bus.isEnabled()) bus.publish(CHANNEL, body(realm, id, action));
    }

    /** The message body: {@code {realm, id, action}}. */
    public static JsonObject body(String realm, String id, String action) {
        JsonObject o = new JsonObject();
        o.addProperty("realm", realm);
        o.addProperty("id", id);
        o.addProperty("action", action);
        return o;
    }

    /** Called on the bus thread. */
    public void handle(Envelope env) {
        if (selfId.equals(env.from())) return;
        String from, id, action;
        try {
            from = env.body().get("realm").getAsString();
            id = env.body().get("id").getAsString();
            action = env.body().get("action").getAsString();
        } catch (RuntimeException e) {
            Logger.getLogger("WoSSystems").fine("Ignoring a bad leaderboard message from " + env.from() + ": " + e);
            return;
        }
        if (!realm.equals(from)) return;
        boolean clears = action.equals("reset") || action.equals("end");
        mainThread.execute(() -> {
            if (clears) clearSource.accept(id);
            refresh.accept(id);
        });
    }
}
