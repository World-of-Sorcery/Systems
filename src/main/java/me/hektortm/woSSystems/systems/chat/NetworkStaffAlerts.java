package me.hektortm.woSSystems.systems.chat;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.net.Envelope;
import me.hektortm.wosCore.net.NetworkBus;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Staff warnings (a blocked command in content, ...) for staff on every server. The server where
 * it happens warns its own staff and publishes the text on {@value #CHANNEL}; the others show it,
 * marked with the server it came from, to their staff through the same check
 * ({@code LogManager.sendWarning}: the {@code core_warnings} unlockable).
 */
public final class NetworkStaffAlerts {
    /** The bus channel staff warnings travel on. */
    public static final String CHANNEL = "wos:staff";

    private final String selfId;
    private final Executor mainThread;
    private final Consumer<String> warnLocalStaff;

    /**
     * @param selfId         this server's id; its own warnings are skipped
     * @param mainThread     runs a task on the main server thread
     * @param warnLocalStaff shows a warning to this server's staff (called on the main thread)
     */
    public NetworkStaffAlerts(String selfId, Executor mainThread, Consumer<String> warnLocalStaff) {
        this.selfId = selfId;
        this.mainThread = mainThread;
        this.warnLocalStaff = warnLocalStaff;
    }

    /** Receives other servers' warnings. A disabled bus never calls it. Call once at enable. */
    public void register(NetworkBus bus) {
        bus.subscribe(CHANNEL, this::handle);
    }

    /** Sends a warning this server already showed to its staff to the other servers. A no-op without Redis. */
    public static void publish(NetworkBus bus, String message) {
        if (!bus.isEnabled()) return;
        bus.publish(CHANNEL, body(message));
    }

    /** The message body: {@code {message}}. */
    public static JsonObject body(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("message", message);
        return o;
    }

    /** Called on the bus thread. */
    public void handle(Envelope env) {
        if (selfId.equals(env.from())) return;
        String message;
        try {
            message = env.body().get("message").getAsString();
        } catch (RuntimeException e) {
            Logger.getLogger("WoSSystems").fine("Ignoring a bad staff warning from " + env.from() + ": " + e);
            return;
        }
        String shown = "[" + env.from() + "] " + message;
        mainThread.execute(() -> warnLocalStaff.accept(shown));
    }
}
