package me.hektortm.woSSystems.systems.chat;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.presence.NetworkPlayer;
import me.hektortm.wosCore.presence.NetworkPresenceRules;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /msg and /r across servers. A player on this server is messaged directly; anyone else is
 * looked up in the network's online list (wos-api) and the message goes out on {@value #CHANNEL},
 * where the server that has the player shows it ({@link NetworkMsg}).
 */
public final class MsgRules {
    /** The bus channel private messages travel on. */
    public static final String CHANNEL = "wos:msg";
    /** The sender of a message from the console, a command block or content run as the console. */
    public static final String SERVER_NAME = "Server";
    /** The sender uuid of such a message (the nil uuid, no player's). */
    public static final UUID SERVER_UUID = new UUID(0L, 0L);

    private MsgRules() {}

    /** Whether a message came from the server, not a player: it is shown, but nobody can /r to it. */
    public static boolean isServer(UUID sender) {
        return SERVER_UUID.equals(sender);
    }

    /** Whom /r answers: the last player someone messaged or got a message from. */
    public record Partner(UUID uuid, String name) {}

    /**
     * The player named {@code name} on another server. Someone the list still has on this server
     * is not here any more (the list lags behind a quit): not found.
     */
    public static Optional<NetworkPlayer> findRemote(String name, String selfId, Collection<NetworkPlayer> online) {
        return NetworkPresenceRules.find(name, online).filter(p -> !p.server().equals(selfId));
    }

    /** The message body: {@code {from, from_name, to, to_name, text}}. */
    public static JsonObject body(UUID from, String fromName, UUID to, String toName, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("from", from.toString());
        o.addProperty("from_name", fromName);
        o.addProperty("to", to.toString());
        o.addProperty("to_name", toName);
        o.addProperty("text", text);
        return o;
    }

    /** Each player's last partner. Written on the main thread and the bus thread. */
    public static final class ReplyBook {
        private final Map<UUID, Partner> partners = new ConcurrentHashMap<>();

        public void remember(UUID player, UUID partner, String partnerName) {
            partners.put(player, new Partner(partner, partnerName));
        }

        public Optional<Partner> partnerOf(UUID player) {
            return Optional.ofNullable(partners.get(player));
        }

        /** At quit: the entry would only grow the map. */
        public void forget(UUID player) {
            partners.remove(player);
        }
    }
}
