package me.hektortm.woSSystems.systems.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountdownFormatTest {

    @Test
    void underAnHourIsMinutesAndSeconds() {
        assertEquals("00:00", CountdownFormat.format(Duration.ZERO));
        assertEquals("00:59", CountdownFormat.format(Duration.ofSeconds(59)));
        assertEquals("12:34", CountdownFormat.format(Duration.ofMinutes(12).plusSeconds(34)));
        assertEquals("59:59", CountdownFormat.format(Duration.ofMinutes(59).plusSeconds(59)));
    }

    @Test
    void fromAnHourUpIsHoursAndMinutes() {
        assertEquals("1h 0m", CountdownFormat.format(Duration.ofHours(1)));
        assertEquals("1h 23m", CountdownFormat.format(Duration.ofHours(1).plusMinutes(23).plusSeconds(59)));
        assertEquals("23h 59m", CountdownFormat.format(Duration.ofHours(23).plusMinutes(59)));
    }

    @Test
    void fromADayUpIsDaysAndHours() {
        assertEquals("1d 0h", CountdownFormat.format(Duration.ofHours(24)));
        assertEquals("2d 5h", CountdownFormat.format(Duration.ofDays(2).plusHours(5).plusMinutes(30)));
    }

    @Test
    void aPartOfASecondCountsAsAWholeOneAndThePastAsZero() {
        // The last second shows 00:01 until it is over, not 00:00 for a second.
        assertEquals("00:01", CountdownFormat.format(Duration.ofMillis(400)));
        assertEquals("00:00", CountdownFormat.format(Duration.ofSeconds(-5)));
    }
}
