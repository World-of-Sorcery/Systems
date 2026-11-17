package me.hektortm.woSSystems.systems.leaderboards;

import me.hektortm.wosCore.net.Envelope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A board changed on one server is cleared and read again on the others of its realm. */
class LeaderboardSyncTest {
    private final List<String> cleared = new ArrayList<>();
    private final List<String> refreshed = new ArrayList<>();
    private final NetworkLeaderboards sync = new NetworkLeaderboards("live", "live", Runnable::run, cleared::add, refreshed::add);

    private Envelope from(String server, String realm, String action) {
        return new Envelope(server, NetworkLeaderboards.CHANNEL, NetworkLeaderboards.body(realm, "b", action), null);
    }

    @Test
    void resetClearsStatInOnlineSessions() {
        sync.handle(from("live-2", "live", "reset"));
        sync.handle(from("live-2", "live", "end"));
        assertThat(cleared).containsExactly("b", "b");
        assertThat(refreshed).containsExactly("b", "b");
    }

    @Test
    void lockOnlyRefreshes() {
        sync.handle(from("hub", "live", "lock"));
        assertThat(cleared).isEmpty();
        assertThat(refreshed).containsExactly("b");
    }

    @Test
    void ignoresOwnServer() {
        sync.handle(from("live", "live", "reset"));
        assertThat(cleared).isEmpty();
        assertThat(refreshed).isEmpty();
    }

    @Test
    void ignoresOtherRealm() {
        sync.handle(from("staging", "test", "reset"));
        assertThat(cleared).isEmpty();
        assertThat(refreshed).isEmpty();
    }

    @Test
    void ignoresABadMessage() {
        sync.handle(new Envelope("hub", NetworkLeaderboards.CHANNEL, new com.google.gson.JsonObject(), null));
        assertThat(refreshed).isEmpty();
    }
}
