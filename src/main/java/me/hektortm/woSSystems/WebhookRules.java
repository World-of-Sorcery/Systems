package me.hektortm.woSSystems;

import me.hektortm.wosCore.server.ServerIdentity;

/**
 * Decides which webhook messages this server takes. The live server loads content only at
 * start, so it refuses content reloads; balance changes still apply. The tag is Core's
 * ({@code WoSCore.identity()}: env {@code WOS_SERVER_TAG} or {@code server.tag} in Core's config).
 */
final class WebhookRules {

    private WebhookRules() {
    }

    /**
     * True only on a server whose tag was set to {@code live}. A server without a tag is the
     * pre-network single server and takes every message, content reloads included.
     */
    static boolean refusesContent(ServerIdentity identity) {
        return !identity.takesContentReloads();
    }

    /**
     * On an explicitly configured live server only non-content messages pass, {@code player_balance} and {@code player_reset} (a player's test progress was reset; live never gets it). Language
     * messages count as content. Off live everything passes.
     */
    static boolean accept(String type, boolean live) {
        if (!live) return true;
        return "player_balance".equals(type) || "player_reset".equals(type);
    }

    /**
     * Who made the change, from the message's {@code editor_uuid} (with or without dashes),
     * or null: a write made with the web service token (a seed script, the portal's server
     * side) has none, and its content must reload all the same.
     */
    @javax.annotation.Nullable
    static java.util.UUID editor(@javax.annotation.Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.contains("-") ? raw : raw.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        try {
            return java.util.UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
