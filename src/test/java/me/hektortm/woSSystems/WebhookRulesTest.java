package me.hektortm.woSSystems;

import me.hektortm.wosCore.server.ServerIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookRulesTest {

    /** The identity Core builds from the environment alone (no yaml section). */
    private static ServerIdentity fromEnv(Map<String, String> env) {
        return ServerIdentity.from(null, env);
    }

    @Test
    void liveIgnoresCitems() {
        boolean live = WebhookRules.refusesContent(fromEnv(Map.of("WOS_SERVER_TAG", "live")));
        assertThat(live).isTrue();
        assertThat(WebhookRules.accept("citems", live)).isFalse();
        assertThat(WebhookRules.accept("quests", live)).isFalse();
    }

    @Test
    void liveTakesBalance() {
        assertThat(WebhookRules.accept("player_balance", WebhookRules.refusesContent(fromEnv(Map.of("WOS_SERVER_TAG", "live"))))).isTrue();
    }

    @Test
    void yamlLiveIsConfiguredToo() {
        assertThat(WebhookRules.refusesContent(new ServerIdentity("live", "live"))).isTrue();
    }

    @Test
    void stagingTakesCitems() {
        boolean live = WebhookRules.refusesContent(fromEnv(Map.of("WOS_SERVER_TAG", "staging")));
        assertThat(live).isFalse();
        assertThat(WebhookRules.accept("citems", live)).isTrue();
        assertThat(WebhookRules.accept("lang", live)).isTrue();
        assertThat(WebhookRules.accept("player_balance", live)).isTrue();
    }

    @Test
    void liveIgnoresLang() {
        assertThat(WebhookRules.accept("lang", WebhookRules.refusesContent(fromEnv(Map.of("WOS_SERVER_TAG", "live"))))).isFalse();
    }

    @Test
    void unsetTagIsLegacyAndAcceptsCitems() {
        ServerIdentity unset = fromEnv(Map.of());
        assertThat(unset.configured()).isFalse();
        assertThat(WebhookRules.refusesContent(unset)).isFalse();
        assertThat(WebhookRules.accept("citems", WebhookRules.refusesContent(unset))).isTrue();
        // Core's own fallback before enable is the legacy server too.
        assertThat(WebhookRules.refusesContent(ServerIdentity.defaults())).isFalse();
    }

    @Test
    void blankTagIsLegacy() {
        assertThat(WebhookRules.refusesContent(fromEnv(Map.of("WOS_SERVER_TAG", " ")))).isFalse();
        assertThat(WebhookRules.refusesContent(fromEnv(Map.of("WOS_SERVER_TAG", "")))).isFalse();
    }

    @Test
    void playerResetIsAcceptedOnLive() {
        assertThat(WebhookRules.accept("player_reset", true)).isTrue();
        assertThat(WebhookRules.accept("player_reset", false)).isTrue();
    }
}
