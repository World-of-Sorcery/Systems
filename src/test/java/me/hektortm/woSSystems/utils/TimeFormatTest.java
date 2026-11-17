package me.hektortm.woSSystems.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeFormatTest {

    @Test
    void formatsMinutesSecondsAndHundredths() {
        assertThat(TimeFormat.millis(0)).isEqualTo("0:00.00");
        assertThat(TimeFormat.millis(5070)).isEqualTo("0:05.07");
        assertThat(TimeFormat.millis(83459)).isEqualTo("1:23.45");
    }

    @Test
    void addsHoursFromOneHour() {
        assertThat(TimeFormat.millis(3723400)).isEqualTo("1:02:03.40");
    }
}
