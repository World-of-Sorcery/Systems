package me.hektortm.woSSystems.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerConnectActionTest {

    @Test
    void parsesServerConnect() {
        ServerConnectAction a = ServerConnectAction.parse("server connect Steve live");
        assertThat(a).isNotNull();
        assertThat(a.player()).isEqualTo("Steve");
        assertThat(a.server()).isEqualTo("live");
        for (String s : new String[]{"hub", "backend", "staging", "live"}) {
            assertThat(ServerConnectAction.parse("server connect @p " + s)).isNotNull();
        }
        assertThat(ServerConnectAction.isServerAction("server connect @p live")).isTrue();
        assertThat(ServerConnectAction.isServerAction("serverinfo")).isFalse();
    }

    @Test
    void rejectsUnknownServer() {
        assertThat(ServerConnectAction.parse("server connect @p lobby")).isNull();
        assertThat(ServerConnectAction.parse("server connect @p")).isNull();
        assertThat(ServerConnectAction.parse("server list @p live")).isNull();
        assertThat(ServerConnectAction.parse("server connect @p live now")).isNull();
    }
}
