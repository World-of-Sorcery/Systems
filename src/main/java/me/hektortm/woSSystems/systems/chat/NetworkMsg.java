package me.hektortm.woSSystems.systems.chat;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.net.Envelope;
import me.hektortm.wosCore.net.NetworkBus;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Receives private messages sent from other servers ({@value MsgRules#CHANNEL}): every server
 * gets each one, and the one that has the player shows it and remembers the sender for /r (not when
 * the sender is the server: {@link MsgRules#isServer}).
 *
 * <p>The bus calls {@link #handle} off the main thread; the delivery is handed to {@code mainThread}.</p>
 */
public final class NetworkMsg {
    private final String selfId;
    private final Executor mainThread;
    private final Function<UUID, ? extends Audience> localPlayer;
    private final MsgRules.ReplyBook book;
    private final BiFunction<String, String, Component> fromLine;

    /**
     * @param selfId      this server's id; its own messages are skipped (it delivered them directly)
     * @param mainThread  runs a task on the main server thread
     * @param localPlayer the player with that uuid on this server, or null (called on the main thread)
     * @param book        the /r partners
     * @param fromLine    the line the receiver sees, from the sender's name and the text
     */
    public NetworkMsg(String selfId, Executor mainThread, Function<UUID, ? extends Audience> localPlayer,
                      MsgRules.ReplyBook book, BiFunction<String, String, Component> fromLine) {
        this.selfId = selfId;
        this.mainThread = mainThread;
        this.localPlayer = localPlayer;
        this.book = book;
        this.fromLine = fromLine;
    }

    /** Receives other servers' messages. A disabled bus never calls it. Call once at enable. */
    public void register(NetworkBus bus) {
        bus.subscribe(MsgRules.CHANNEL, this::handle);
    }

    /** Called on the bus thread. */
    public void handle(Envelope env) {
        if (selfId.equals(env.from())) return;
        UUID from;
        UUID to;
        String fromName;
        String text;
        try {
            JsonObject b = env.body();
            from = UUID.fromString(b.get("from").getAsString());
            fromName = b.get("from_name").getAsString();
            to = UUID.fromString(b.get("to").getAsString());
            text = b.get("text").getAsString();
        } catch (RuntimeException e) {
            Logger.getLogger("WoSSystems").fine("Ignoring a bad private message from " + env.from() + ": " + e);
            return;
        }
        mainThread.execute(() -> {
            Audience target = localPlayer.apply(to);
            if (target == null) return; // on another server
            target.sendMessage(fromLine.apply(fromName, text));
            if (!MsgRules.isServer(from)) book.remember(to, from, fromName);
        });
    }
}
