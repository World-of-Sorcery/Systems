package me.hektortm.woSSystems.systems.chat;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.net.Envelope;
import me.hektortm.wosCore.net.NetworkBus;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Chat between game servers. The server where a message is typed formats the whole line
 * ({@link ChatManager#render}) and publishes it on {@value #CHANNEL}; every other server shows it
 * to its local players who may read the line's chat channel.
 *
 * <p>The bus calls {@link #handle} off the main thread: the delivery is handed to {@code mainThread}.
 * Click callbacks and item snapshots exist only on the sending server, so they are stripped
 * ({@link #stripCallbacks}) before a line is published and again on receipt. On receipt that drops
 * every click, whatever its action: a line from the bus must never run a command for whoever clicks it.</p>
 */
public final class NetworkChat {
    /** The bus channel chat lines travel on. */
    public static final String CHANNEL = "wos:chat";
    /** The chat channel of every line today (chat has no channels of its own yet). */
    public static final String GLOBAL = "global";

    private final String selfId;
    private final Executor mainThread;
    private final Supplier<? extends Collection<? extends Audience>> viewers;
    private final BiPredicate<Audience, String> canRead;
    private final Logger log;

    /**
     * @param selfId     this server's id; its own messages are skipped
     * @param mainThread runs a task on the main server thread
     * @param viewers    the local players (read on the main thread)
     * @param canRead    whether a viewer may read lines of a chat channel
     */
    public NetworkChat(String selfId, Executor mainThread, Supplier<? extends Collection<? extends Audience>> viewers,
                       BiPredicate<Audience, String> canRead) {
        this(selfId, mainThread, viewers, canRead, Logger.getLogger("WoSSystems"));
    }

    public NetworkChat(String selfId, Executor mainThread, Supplier<? extends Collection<? extends Audience>> viewers,
                       BiPredicate<Audience, String> canRead, Logger log) {
        this.selfId = selfId;
        this.mainThread = mainThread;
        this.viewers = viewers;
        this.canRead = canRead;
        this.log = log;
    }

    /** Receives other servers' lines. A disabled bus never calls it. Call once at enable. */
    public void register(NetworkBus bus) {
        bus.subscribe(CHANNEL, this::handle);
    }

    /** Publishes a line this server rendered. A no-op when the bus is disabled. */
    public static void publish(NetworkBus bus, String channel, Component line, String sender) {
        if (!bus.isEnabled()) return;
        try {
            bus.publish(CHANNEL, body(channel, line, sender));
        } catch (RuntimeException e) {
            Logger.getLogger("WoSSystems").warning("Could not publish a chat line: " + e);
        }
    }

    /** The message body: {@code {channel, line (Adventure JSON), sender}}. */
    public static JsonObject body(String channel, Component line, String sender) {
        JsonObject o = new JsonObject();
        o.addProperty("channel", channel);
        o.addProperty("line", GsonComponentSerializer.gson().serialize(stripCallbacks(line)));
        o.addProperty("sender", sender);
        return o;
    }

    /** Called on the bus thread. */
    public void handle(Envelope env) {
        if (selfId.equals(env.from())) return;
        Component line;
        String channel;
        try {
            JsonObject body = env.body();
            channel = body.get("channel").getAsString();
            line = stripCallbacks(GsonComponentSerializer.gson().deserialize(body.get("line").getAsString()));
        } catch (RuntimeException e) {
            log.fine("Ignoring a bad chat message from " + env.from() + ": " + e);
            return;
        }
        String ch = channel;
        Component shown = line;
        mainThread.execute(() -> {
            for (Audience a : recipients(ch, viewers.get(), canRead)) a.sendMessage(shown);
        });
    }

    /** The viewers of {@code viewers} who may read {@code channel}. */
    public static <T> List<T> recipients(String channel, Collection<? extends T> viewers, BiPredicate<? super T, String> canRead) {
        return viewers.stream().<T>map(v -> v).filter(v -> canRead.test(v, channel)).toList();
    }

    /**
     * Removes every click event (a rendered line only has callbacks and dialogs, which work only on
     * the server that built it; anything else came from whoever published on the bus), and turns item
     * hovers into a plain text hover with the item's id. Goes through children and translation
     * arguments. The text, colours and text hovers stay.
     */
    public static Component stripCallbacks(Component c) {
        if (c.clickEvent() != null) c = c.clickEvent(null);
        if (c instanceof TranslatableComponent t && !t.arguments().isEmpty()) {
            c = t.arguments(t.arguments().stream()
                    .map(a -> a.value() instanceof Component arg ? TranslationArgument.component(stripCallbacks(arg)) : a)
                    .toList());
        }
        HoverEvent<?> hover = c.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_ITEM) {
            HoverEvent.ShowItem item = (HoverEvent.ShowItem) hover.value();
            String text = item.count() > 1 ? item.item().asString() + " x" + item.count() : item.item().asString();
            c = c.hoverEvent(HoverEvent.showText(Component.text(text, NamedTextColor.GRAY)));
        }
        List<Component> children = c.children();
        if (!children.isEmpty()) {
            c = c.children(children.stream().map(NetworkChat::stripCallbacks).toList());
        }
        return c;
    }
}
