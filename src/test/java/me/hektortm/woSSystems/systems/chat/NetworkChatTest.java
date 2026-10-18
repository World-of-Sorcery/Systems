package me.hektortm.woSSystems.systems.chat;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.net.Envelope;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkChatTest {

    /** A viewer that remembers what it was sent. */
    private static final class Viewer implements Audience {
        final List<Component> got = new ArrayList<>();

        @Override
        public void sendMessage(Component message) {
            got.add(message);
        }
    }

    private static Envelope chat(String from, String channel, Component line) {
        JsonObject body = NetworkChat.body(channel, line, "Alice");
        return new Envelope(from, "wos:chat", body, null);
    }

    @Test
    void skipsOwnServer() {
        Viewer v = new Viewer();
        NetworkChat chat = new NetworkChat("live-1", Runnable::run, () -> List.of(v), (a, c) -> true);

        chat.handle(chat("live-1", "global", Component.text("hi")));

        assertThat(v.got).isEmpty();
    }

    @Test
    void stripsCallbacks() {
        Component line = Component.text("Alice")
                .clickEvent(ClickEvent.custom(Key.key("paper", "click_callback/x"), BinaryTagHolder.binaryTagHolder("{}")))
                .hoverEvent(HoverEvent.showText(Component.text("Click to view profile")))
                .append(Component.text(" [sword]", NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showItem(Key.key("minecraft", "diamond_sword"), 1)));

        Component stripped = NetworkChat.stripCallbacks(line);

        assertThat(stripped.clickEvent()).isNull();
        assertThat(stripped.hoverEvent()).isEqualTo(HoverEvent.showText(Component.text("Click to view profile")));
        Component child = stripped.children().get(0);
        assertThat(child.hoverEvent().action()).isEqualTo(HoverEvent.Action.SHOW_TEXT);
        assertThat(child.color()).isEqualTo(NamedTextColor.GRAY);
    }

    @Test
    void dropsEveryClickActionEverywhere() {
        Component line = Component.text("free gold", NamedTextColor.GOLD)
                .clickEvent(ClickEvent.runCommand("/op Mallory"))
                .append(Component.text(" here").clickEvent(ClickEvent.suggestCommand("/pay Mallory 1000"))
                        .append(Component.text(" now").clickEvent(ClickEvent.openUrl("https://example.invalid"))))
                .append(Component.translatable("chat.type.text",
                        Component.text("a").clickEvent(ClickEvent.copyToClipboard("x")),
                        Component.text("b").clickEvent(ClickEvent.changePage(2))));

        Component stripped = NetworkChat.stripCallbacks(line);

        assertThat(stripped.clickEvent()).isNull();
        assertThat(stripped.color()).isEqualTo(NamedTextColor.GOLD);
        Component here = stripped.children().get(0);
        assertThat(here.clickEvent()).isNull();
        assertThat(here.children().get(0).clickEvent()).isNull();
        var translated = (net.kyori.adventure.text.TranslatableComponent) stripped.children().get(1);
        assertThat(translated.arguments()).hasSize(2)
                .allSatisfy(a -> assertThat(((Component) a.value()).clickEvent()).isNull());
        assertThat(GsonComponentSerializer.gson().serialize(stripped)).doesNotContain("click");
    }

    @Test
    void aReceivedLineLosesARunCommandClick() {
        Viewer v = new Viewer();
        NetworkChat chat = new NetworkChat("live-1", Runnable::run, () -> List.of(v), (a, c) -> true);
        JsonObject body = new JsonObject();
        body.addProperty("channel", "global");
        body.addProperty("line", GsonComponentSerializer.gson().serialize(
                Component.text("click me").clickEvent(ClickEvent.runCommand("/stop"))));
        body.addProperty("sender", "Mallory");

        chat.handle(new Envelope("backend", "wos:chat", body, null));

        assertThat(v.got).hasSize(1);
        assertThat(v.got.get(0).clickEvent()).isNull();
    }

    @Test
    void onlyChannelMembers() {
        Viewer member = new Viewer();
        Viewer other = new Viewer();
        Map<Viewer, String> channels = Map.of(member, "staff", other, "global");
        NetworkChat chat = new NetworkChat("live-1", Runnable::run, () -> List.of(member, other),
                (a, c) -> c.equals(channels.get(a)));
        Component line = Component.text("secret");

        chat.handle(chat("staff-1", "staff", line));

        assertThat(member.got).containsExactly(line);
        assertThat(other.got).isEmpty();
        // The wire format round-trips.
        assertThat(GsonComponentSerializer.gson().deserialize(
                NetworkChat.body("staff", line, "Alice").get("line").getAsString())).isEqualTo(line);
    }
}
