package me.hektortm.woSSystems.systems.timers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimerPlaceholdersTest {

    @Test
    void showsTheRunningTime() {
        assertThat(TimerPlaceholders.value("duration", 83459L, null, null)).isEqualTo("1:23.45");
        assertThat(TimerPlaceholders.value("seconds", 83459L, null, null)).isEqualTo("83");
    }

    @Test
    void isEmptyWhenThereIsNothingToShow() {
        assertThat(TimerPlaceholders.value("duration", null, null, null)).isEmpty();
        assertThat(TimerPlaceholders.value("seconds", null, null, null)).isEmpty();
        assertThat(TimerPlaceholders.value("best", null, null, null)).isEmpty();
        assertThat(TimerPlaceholders.value("last", null, null, null)).isEmpty();
    }

    @Test
    void showsBestAndLast() {
        assertThat(TimerPlaceholders.value("best", null, 9000L, null)).isEqualTo("0:09.00");
        assertThat(TimerPlaceholders.value("last", null, null, 12340L)).isEqualTo("0:12.34");
    }

    @Test
    void anUnknownKeyIsNoValue() {
        assertThat(TimerPlaceholders.value("nope", 1L, 1L, 1L)).isNull();
    }
}
