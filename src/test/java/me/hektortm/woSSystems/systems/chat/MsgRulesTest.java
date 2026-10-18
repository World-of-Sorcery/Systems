package me.hektortm.woSSystems.systems.chat;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.net.Envelope;
import me.hektortm.wosCore.presence.NetworkPlayer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** /msg and /r between servers. */
class MsgRulesTest {
    private static final UUID ALEX = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID SAM = UUID.fromString("28c63f65-52cc-47f0-8246-b16f2176da23");
    private static final UUID KIM = UUID.fromString("0a0a0a0a-0000-0000-0000-000000000001");

    /** A player that remembers what it was sent. */
    private static final class Viewer implements Audience {
        final List<Component> got = new ArrayList<>();

        @Override
        public void sendMessage(Component message) {
            got.add(message);
        }
    }

    private static Component fromLine(String name, String text) {
        return Component.text(name + ": " + text);
    }

    @Test
    void msgFindsRemotePlayer() {
        List<NetworkPlayer> online = List.of(new NetworkPlayer(ALEX, "Alex", "hub"), new NetworkPlayer(SAM, "Sam", "live"));

        assertThat(MsgRules.findRemote("alex", "live", online)).contains(new NetworkPlayer(ALEX, "Alex", "hub"));
        assertThat(MsgRules.findRemote("Nobody", "live", online)).isEmpty();
        // Listed on this server but not here any more: gone, not elsewhere.
        assertThat(MsgRules.findRemote("Sam", "live", online)).isEmpty();
    }

    @Test
    void replyRemembersLastPartner() {
        MsgRules.ReplyBook book = new MsgRules.ReplyBook();
        assertThat(book.partnerOf(SAM)).isEmpty();

        book.remember(SAM, ALEX, "Alex");
        assertThat(book.partnerOf(SAM)).contains(new MsgRules.Partner(ALEX, "Alex"));

        book.remember(SAM, KIM, "Kim"); // the latest one wins
        assertThat(book.partnerOf(SAM)).contains(new MsgRules.Partner(KIM, "Kim"));

        book.forget(SAM);
        assertThat(book.partnerOf(SAM)).isEmpty();
    }

    @Test
    void remoteMessageReachesTheLocalPlayerWhoCanReply() {
        Viewer sam = new Viewer();
        MsgRules.ReplyBook book = new MsgRules.ReplyBook();
        NetworkMsg msg = new NetworkMsg("live", Runnable::run, Map.of(SAM, sam)::get, book, MsgRulesTest::fromLine);

        msg.handle(new Envelope("hub", MsgRules.CHANNEL, MsgRules.body(ALEX, "Alex", SAM, "Sam", "hi there"), null));

        assertThat(sam.got).containsExactly(Component.text("Alex: hi there"));
        assertThat(book.partnerOf(SAM)).contains(new MsgRules.Partner(ALEX, "Alex"));
    }

    @Test
    void ownServerAndAbsentPlayersAreSkipped() {
        Viewer sam = new Viewer();
        MsgRules.ReplyBook book = new MsgRules.ReplyBook();
        NetworkMsg msg = new NetworkMsg("live", Runnable::run, Map.of(SAM, sam)::get, book, MsgRulesTest::fromLine);

        msg.handle(new Envelope("live", MsgRules.CHANNEL, MsgRules.body(ALEX, "Alex", SAM, "Sam", "own"), null));
        msg.handle(new Envelope("hub", MsgRules.CHANNEL, MsgRules.body(ALEX, "Alex", KIM, "Kim", "not here"), null));
        msg.handle(new Envelope("hub", MsgRules.CHANNEL, new JsonObject(), null)); // bad body

        assertThat(sam.got).isEmpty();
        assertThat(book.partnerOf(KIM)).isEmpty();
    }

    @Test
    void aMessageFromTheServerIsShownButCannotBeAnswered() {
        Viewer sam = new Viewer();
        MsgRules.ReplyBook book = new MsgRules.ReplyBook();
        book.remember(SAM, ALEX, "Alex");
        NetworkMsg msg = new NetworkMsg("live", Runnable::run, Map.of(SAM, sam)::get, book, MsgRulesTest::fromLine);

        msg.handle(new Envelope("hub", MsgRules.CHANNEL,
                MsgRules.body(MsgRules.SERVER_UUID, MsgRules.SERVER_NAME, SAM, "Sam", "restart in 5"), null));

        assertThat(sam.got).containsExactly(Component.text("Server: restart in 5"));
        // /r still answers the last player, never "Server"
        assertThat(book.partnerOf(SAM)).contains(new MsgRules.Partner(ALEX, "Alex"));
    }

    @Test
    void onlyTheNilUuidIsTheServer() {
        assertThat(MsgRules.isServer(MsgRules.SERVER_UUID)).isTrue();
        assertThat(MsgRules.isServer(ALEX)).isFalse();
    }
}
