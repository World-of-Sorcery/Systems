package me.hektortm.woSSystems;

import me.hektortm.wosCore.server.ServerIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobRulesTest {

    @Test
    void liveRuns() {
        assertThat(JobRules.runsScheduled(new ServerIdentity("live", "live"))).isTrue();
    }

    @Test
    void stagingRuns() {
        assertThat(JobRules.runsScheduled(new ServerIdentity("staging", "staging"))).isTrue();
    }

    @Test
    void hubDoesNot() {
        assertThat(JobRules.runsScheduled(new ServerIdentity("hub", "hub"))).isFalse();
    }

    @Test
    void backendDoesNot() {
        assertThat(JobRules.runsScheduled(new ServerIdentity("backend", "backend"))).isFalse();
    }

    @Test
    void unconfiguredLegacyRuns() {
        ServerIdentity legacy = ServerIdentity.defaults();
        assertThat(legacy.configured()).isFalse();
        assertThat(JobRules.runsScheduled(legacy)).isTrue();
    }
}
