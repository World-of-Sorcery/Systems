package me.hektortm.woSSystems.systems.resourcepack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftTest {

    @Test
    void negativeShiftsUseTheLargestStepsFirst() {
        assertThat(Shift.of(-169)).isEqualTo("");
        assertThat(Shift.of(-8)).isEqualTo("");
    }

    @Test
    void positiveShiftsUseTheOtherRange() {
        assertThat(Shift.of(5)).isEqualTo("");
    }

    @Test
    void zeroIsEmptyAndTheLimitIs1023() {
        assertThat(Shift.of(0)).isEmpty();
        assertThat(Shift.of(1023)).hasSize(10);
        assertThat(Shift.of(1024)).isNull();
        assertThat(Shift.of(-1024)).isNull();
    }
}
