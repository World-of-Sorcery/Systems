package me.hektortm.woSSystems.systems.resourcepack;

import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackRulesTest {

    private static final Status[] FAILURES = {Status.FAILED_DOWNLOAD, Status.INVALID_URL, Status.FAILED_RELOAD, Status.DISCARDED};

    @Test
    void declinedKicksAsDeclined() {
        assertThat(PackRules.onStatus(Status.DECLINED, true)).isEqualTo(PackRules.Outcome.KICK_DECLINED);
        assertThat(PackRules.onStatus(Status.DECLINED, false)).isEqualTo(PackRules.Outcome.KICK_DECLINED);
    }

    @Test
    void failuresOfTheJoinRequestKickAsFailed() {
        for (Status s : FAILURES) {
            assertThat(PackRules.onStatus(s, true)).as(s.name()).isEqualTo(PackRules.Outcome.KICK_FAILED);
        }
    }

    @Test
    void failuresOfARefreshAreOnlyLogged() {
        for (Status s : FAILURES) {
            assertThat(PackRules.onStatus(s, false)).as(s.name()).isEqualTo(PackRules.Outcome.LOG_FAILED);
        }
    }

    @Test
    void otherStatusesDoNothing() {
        for (Status s : new Status[]{Status.ACCEPTED, Status.DOWNLOADED, Status.SUCCESSFULLY_LOADED}) {
            assertThat(PackRules.onStatus(s, true)).as(s.name()).isEqualTo(PackRules.Outcome.NONE);
            assertThat(PackRules.onStatus(s, false)).as(s.name()).isEqualTo(PackRules.Outcome.NONE);
        }
        assertThat(PackRules.onStatus(null, true)).isEqualTo(PackRules.Outcome.NONE);
    }

    @Test
    void tooOldBelow772Only() {
        assertThat(PackRules.tooOld(771)).isTrue();
        assertThat(PackRules.tooOld(772)).isFalse();
        assertThat(PackRules.tooOld(-1)).isFalse();
        assertThat(PackRules.tooOld(0)).isFalse();
    }
}
