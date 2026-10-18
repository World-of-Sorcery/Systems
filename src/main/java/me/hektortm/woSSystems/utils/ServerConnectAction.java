package me.hektortm.woSSystems.utils;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * The {@code server connect <player|@p> <server>} action line, parsed without
 * a server so it can be tested. The player word is ignored by the handler: it
 * is always the acting player (as with {@code cooldown}).
 */
record ServerConnectAction(String player, String server) {

    /** The servers a player can be sent to (the network's server tags). */
    static final List<String> SERVERS = List.of("hub", "backend", "staging", "live");

    /** Whether the line is a {@code server …} action (valid or not). */
    static boolean isServerAction(String cmd) {
        String t = cmd.trim();
        return t.equals("server") || t.startsWith("server ");
    }

    /**
     * {@code server connect <player|@p> <server>} as an action, or null if it
     * is malformed or the server is not one of {@link #SERVERS}.
     */
    static @Nullable ServerConnectAction parse(String cmd) {
        String[] parts = cmd.trim().split("\\s+");
        if (parts.length != 4 || !parts[0].equals("server") || !parts[1].equalsIgnoreCase("connect")) return null;
        String server = parts[3].toLowerCase(Locale.ROOT);
        if (!SERVERS.contains(server)) return null;
        return new ServerConnectAction(parts[2], server);
    }
}
