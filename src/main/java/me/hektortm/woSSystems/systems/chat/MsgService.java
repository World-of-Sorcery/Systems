package me.hektortm.woSSystems.systems.chat;

import me.hektortm.wosCore.LangManager;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.net.NetworkBus;
import me.hektortm.wosCore.presence.NetworkOnline;
import me.hektortm.wosCore.presence.NetworkPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends /msg and /r. A player on this server gets the message directly. Anyone else is looked up
 * in the network's online list (wos-api, off the main thread) and reached through the bus
 * ({@link MsgRules#CHANNEL}, shown by {@link NetworkMsg} on their server). Without Redis, or when
 * wos-api can't be asked, only players on this server can be messaged.
 */
public final class MsgService implements Listener {
    private static final String LANG = "chat";

    private final Plugin plugin;
    private final NetworkBus bus;
    private final @Nullable NetworkOnline online;
    private final String selfId;
    private final LangManager lang;
    private final MsgRules.ReplyBook book = new MsgRules.ReplyBook();

    public MsgService(Plugin plugin, NetworkBus bus, @Nullable NetworkOnline online, String selfId, LangManager lang) {
        this.plugin = plugin;
        this.bus = bus;
        this.online = online;
        this.selfId = selfId;
        this.lang = lang;
    }

    public MsgRules.ReplyBook book() {
        return book;
    }

    /** Main thread: {@code from} messages the player named {@code targetName}, wherever they are. */
    public void send(Player from, String targetName, String text) {
        send(from, from.getUniqueId(), from.getName(), targetName, text);
    }

    /**
     * Main thread: the console, a command block or content run as the console messages a player, as
     * {@value MsgRules#SERVER_NAME}. Nobody can /r to it ({@link MsgRules#isServer}).
     */
    public void sendFromServer(CommandSender from, String targetName, String text) {
        send(from, MsgRules.SERVER_UUID, MsgRules.SERVER_NAME, targetName, text);
    }

    private void send(CommandSender from, UUID fromId, String fromName, String targetName, String text) {
        if (deliveredHere(from, fromId, fromName, targetName, text)) return;
        if (!bus.isEnabled() || online == null) {
            notOnline(from, targetName);
            return;
        }
        online.fetchAsync()
                .handle((list, err) -> err == null ? list : null)
                .thenAccept(list -> onMain(() -> sendRemote(from, fromId, fromName, targetName, text, list)));
    }

    /** Main thread: answers the last partner. */
    public void reply(Player from, String text) {
        Optional<MsgRules.Partner> partner = book.partnerOf(from.getUniqueId());
        if (partner.isEmpty()) {
            Utils.error(from, LANG, "error.no-partner");
            return;
        }
        send(from, partner.get().name(), text);
    }

    /** True if the target is on this server (then it is handled: delivered, or refused for oneself). */
    private boolean deliveredHere(CommandSender from, UUID fromId, String fromName, String targetName, String text) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return false;
        if (target.equals(from)) {
            Utils.error(from, LANG, "error.self");
            return true;
        }
        from.sendMessage(toLine(target.getName(), text));
        target.sendMessage(fromLine(fromName, text));
        if (!MsgRules.isServer(fromId)) {
            book.remember(fromId, target.getUniqueId(), target.getName());
            book.remember(target.getUniqueId(), fromId, fromName);
        }
        return true;
    }

    private void sendRemote(CommandSender from, UUID fromId, String fromName, String targetName, String text,
                            @Nullable List<NetworkPlayer> list) {
        if (from instanceof Player p && !p.isOnline()) return;
        // They may have joined this server while the list was fetched.
        if (deliveredHere(from, fromId, fromName, targetName, text)) return;
        Optional<NetworkPlayer> target = list == null ? Optional.empty() : MsgRules.findRemote(targetName, selfId, list);
        if (target.isEmpty() || target.get().uuid().equals(fromId)) {
            notOnline(from, targetName);
            return;
        }
        NetworkPlayer t = target.get();
        bus.publish(MsgRules.CHANNEL, MsgRules.body(fromId, fromName, t.uuid(), t.username(), text));
        from.sendMessage(toLine(t.username(), text));
        if (!MsgRules.isServer(fromId)) book.remember(fromId, t.uuid(), t.username());
    }

    private void notOnline(CommandSender from, String targetName) {
        Utils.error(from, LANG, "error.not-online", "%player%", targetName);
    }

    private void onMain(Runnable task) {
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalStateException | org.bukkit.plugin.IllegalPluginAccessException disabled) {
            // the plugin is shutting down
        }
    }

    /** The line the sender sees. */
    public Component toLine(String targetName, String text) {
        return line("msg.to", targetName, text);
    }

    /** The line the receiver sees. */
    public Component fromLine(String senderName, String text) {
        return line("msg.from", senderName, text);
    }

    private Component line(String key, String name, String text) {
        // The player's text goes in last (never read as a placeholder), without colour codes of its own.
        String template = lang.getMessage(LANG, key).replace("%player%", name);
        return LegacyComponentSerializer.legacySection().deserialize(template.replace("%message%", text.replace('§', '&')));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        book.forget(event.getPlayer().getUniqueId());
    }
}
