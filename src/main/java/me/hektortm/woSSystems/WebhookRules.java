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
}
