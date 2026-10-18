package me.hektortm.woSSystems;

import me.hektortm.wosCore.server.ServerIdentity;

/**
 * Which server runs the scheduled, server-wide work (time-event interactions, the daily reset).
 * Player progress is kept per realm in the API (live: hub + live; test: backend + staging), so
 * each realm needs exactly one runner: {@code live} for the live realm, {@code staging} for the
 * test realm. Hub and backend never run it. A server without a tag is today's single legacy
 * server and keeps running everything.
 */
public final class JobRules {

    private JobRules() {
    }

    public static boolean runsScheduled(ServerIdentity id) {
        if (!id.configured()) return true;
        return "live".equals(id.tag()) || "staging".equals(id.tag());
    }

    /** {@link #runsScheduled} for this server (Core's identity). */
    public static boolean runsScheduled() {
        return runsScheduled(me.hektortm.wosCore.WoSCore.identity());
    }
}
