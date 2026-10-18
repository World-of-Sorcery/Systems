package me.hektortm.woSSystems.core;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.net.Envelope;
import me.hektortm.wosCore.presence.NetworkOnline;
import me.hektortm.wosCore.presence.NetworkPlayer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** "joined" / "left" only when a player enters or leaves the network. */
class NetworkJoinLeaveTest {
    private static final UUID ALEX = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final Instant T0 = Instant.parse("2026-10-17T12:00:00Z");

    private final List<JsonObject> published = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private Instant now = T0;

    private NetworkJoinLeave server(String id) {
        return new NetworkJoinLeave(id, Runnable::run, (channel, body) -> {
            assertThat(channel).isEqualTo(NetworkJoinLeave.CHANNEL);
            published.add(body);
        }, shown::add, () -> now);
    }

    /** A list asked for just before the join at T0 (at pre-login). */
    private static NetworkOnline.Snapshot asked(List<NetworkPlayer> players) {
        return new NetworkOnline.Snapshot(players, T0.minusMillis(300));
    }

    @Test
    void aListAskedAfterTheJoinOrNoneIsNoJoin() {
        NetworkJoinLeave live = server("live");

        // Asked after the join: this server may already have reported Alex, hiding the hub.
        live.joined(ALEX, "Alex", false, new NetworkOnline.Snapshot(List.of(new NetworkPlayer(ALEX, "Alex", "live")), T0.plusSeconds(1)), T0);
        live.joined(ALEX, "Alex", false, null, T0); // wos-api did not answer

        assertThat(shown).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void switchIsNotJoin() {
        NetworkJoinLeave live = server("live");

        live.joined(ALEX, "Alex", false, asked(List.of(new NetworkPlayer(ALEX, "Alex", "hub"))), T0);

        assertThat(shown).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void arrivalIsPublishedAtOnceWithoutALine() {
        NetworkJoinLeave live = server("live");

        live.arrived(ALEX, "Alex");

        assertThat(shown).isEmpty();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).get("event").getAsString()).isEqualTo("join");
        assertThat(published.get(0).get("announce").getAsBoolean()).isFalse();
        server("hub").handle(new Envelope("live", NetworkJoinLeave.CHANNEL, published.get(0), null));
        assertThat(shown).isEmpty();
    }

    @Test
    void networkJoinIsShownOnEveryServer() {
        NetworkJoinLeave live = server("live");
        live.joined(ALEX, "Alex", true, asked(List.of()), T0);
        assertThat(shown).containsExactly(NetworkJoinLeave.joinLine("Alex", true));
        assertThat(published).hasSize(1);
        assertThat(published.get(0).get("announce").getAsBoolean()).isTrue();

        shown.clear();
        server("hub").handle(new Envelope("live", NetworkJoinLeave.CHANNEL, published.get(0), null));
        assertThat(shown).containsExactly(NetworkJoinLeave.joinLine("Alex", true));
    }

    @Test
    void switchIsNotLeave() {
        NetworkJoinLeave hub = server("hub");
        // live says Alex arrived there just after the hub saw the quit.
        now = T0.plusSeconds(1);
        hub.handle(new Envelope("live", NetworkJoinLeave.CHANNEL, NetworkJoinLeave.body(ALEX, "Alex", "join", false, false), null));

        hub.quitSettled(ALEX, "Alex", T0, false);

        assertThat(shown).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void switchBeforeTheQuitIsNotLeave() {
        NetworkJoinLeave hub = server("hub");
        // Velocity: the arrival on live comes in before the hub sees the quit.
        now = T0.minusSeconds(1);
        hub.handle(new Envelope("live", NetworkJoinLeave.CHANNEL, NetworkJoinLeave.body(ALEX, "Alex", "join", false, false), null));

        hub.quitSettled(ALEX, "Alex", T0, false);

        assertThat(shown).isEmpty();
    }

    @Test
    void arrivalAtLoginBeforeASlowConfigurationIsNotLeave() {
        NetworkJoinLeave hub = server("hub");
        // live accepted the login (arrival at PlayerLoginEvent); the hub lets go while the
        // configuration phase on live is still running, 4 s later.
        now = T0.minusSeconds(4);
        hub.handle(new Envelope("live", NetworkJoinLeave.CHANNEL, NetworkJoinLeave.body(ALEX, "Alex", "join", false, false), null));

        hub.quitSettled(ALEX, "Alex", T0, false);

        assertThat(shown).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void theArrivalAgainAtTheJoinCoversAQuitAfterALongConfiguration() {
        NetworkJoinLeave hub = server("hub");
        // Login arrival 20 s before the hub sees the quit (the hub only lets go after live's
        // configuration phase): too old on its own ...
        now = T0.minusSeconds(20);
        hub.handle(new Envelope("live", NetworkJoinLeave.CHANNEL, NetworkJoinLeave.body(ALEX, "Alex", "join", false, false), null));
        // ... but live sends it again at the join, right around the quit.
        now = T0.plusMillis(200);
        hub.handle(new Envelope("live", NetworkJoinLeave.CHANNEL, NetworkJoinLeave.body(ALEX, "Alex", "join", false, false), null));

        hub.quitSettled(ALEX, "Alex", T0, false);

        assertThat(shown).isEmpty();
    }

    @Test
    void aLoginArrivalAloneLongBeforeTheQuitIsLeave() {
        // Why the join sends it again: an arrival older than JOIN_GRACE before the quit does not count.
        NetworkJoinLeave hub = server("hub");
        now = T0.minusSeconds(20);
        hub.handle(new Envelope("live", NetworkJoinLeave.CHANNEL, NetworkJoinLeave.body(ALEX, "Alex", "join", false, false), null));

        hub.quitSettled(ALEX, "Alex", T0, false);

        assertThat(shown).containsExactly(NetworkJoinLeave.leaveLine("Alex"));
    }

    @Test
    void networkLeaveIsShownOnEveryServer() {
        NetworkJoinLeave hub = server("hub");
        hub.quitSettled(ALEX, "Alex", T0, false);
        assertThat(shown).containsExactly(NetworkJoinLeave.leaveLine("Alex"));
        assertThat(published).hasSize(1);
        assertThat(published.get(0).get("event").getAsString()).isEqualTo("quit");

        shown.clear();
        server("live").handle(new Envelope("hub", NetworkJoinLeave.CHANNEL, published.get(0), null));
        assertThat(shown).containsExactly(NetworkJoinLeave.leaveLine("Alex"));
    }

    @Test
    void backOnThisServerIsNotLeave() {
        server("hub").quitSettled(ALEX, "Alex", T0, true);
        assertThat(shown).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void ownMessagesAreSkipped() {
        NetworkJoinLeave hub = server("hub");
        hub.handle(new Envelope("hub", NetworkJoinLeave.CHANNEL, NetworkJoinLeave.body(ALEX, "Alex", "join", true, false), null));
        hub.handle(new Envelope("live", NetworkJoinLeave.CHANNEL, new JsonObject(), null)); // bad body
        assertThat(shown).isEmpty();
        // Its own join did not count as a join elsewhere.
        hub.quitSettled(ALEX, "Alex", T0, false);
        assertThat(shown).containsExactly(NetworkJoinLeave.leaveLine("Alex"));
    }
}
