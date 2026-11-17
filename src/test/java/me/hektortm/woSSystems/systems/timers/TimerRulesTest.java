package me.hektortm.woSSystems.systems.timers;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimerRulesTest {
    private static final Instant START = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void expiresOnlyPastTheMaxDuration() {
        assertThat(TimerRules.expired(START, 60, START.plusSeconds(60))).isFalse();
        assertThat(TimerRules.expired(START, 60, START.plusSeconds(61))).isTrue();
    }

    @Test
    void elapsedIsInMilliseconds() {
        assertThat(TimerRules.elapsedMillis(START, START.plusMillis(83459))).isEqualTo(83459);
    }

    @Test
    void onlyAFasterTimeIsANewBest() {
        assertThat(TimerRules.isNewBest(null, 5)).isTrue();
        assertThat(TimerRules.isNewBest(9000L, 9000)).isFalse();
        assertThat(TimerRules.isNewBest(9000L, 8999)).isTrue();
    }
}
