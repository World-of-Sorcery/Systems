package me.hektortm.woSSystems.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MathsTest {

    @Test
    void calculatesWithTheUsualOrder() {
        assertThat(Maths.calculate("5 + 3")).isEqualTo("8");
        assertThat(Maths.calculate("2 + 3 * 4")).isEqualTo("14");
        assertThat(Maths.calculate("(2 + 3) * 4")).isEqualTo("20");
        assertThat(Maths.calculate("10 - 2 - 3")).isEqualTo("5");
        assertThat(Maths.calculate("17 % 5")).isEqualTo("2");
        assertThat(Maths.calculate("-5 + 2")).isEqualTo("-3");
        assertThat(Maths.calculate("3 - -2")).isEqualTo("5");
    }

    @Test
    void showsDecimalsOnlyWhenNeeded() {
        assertThat(Maths.calculate("10 / 4")).isEqualTo("2.5");
        assertThat(Maths.calculate("10 / 2")).isEqualTo("5");
        assertThat(Maths.calculate("10 / 3")).isEqualTo("3.33");
        assertThat(Maths.calculate("0.1 + 0.2")).isEqualTo("0.3");
        assertThat(Maths.calculate("2.5 * 2")).isEqualTo("5");
        assertThat(Maths.calculate("1 - 1.001")).isEqualTo("0");
    }

    @Test
    void knowsItsFunctions() {
        assertThat(Maths.calculate("floor(10 / 4)")).isEqualTo("2");
        assertThat(Maths.calculate("ceil(10 / 4)")).isEqualTo("3");
        assertThat(Maths.calculate("round(2.5)")).isEqualTo("3");
        assertThat(Maths.calculate("abs(3 - 10)")).isEqualTo("7");
        assertThat(Maths.calculate("min(4, 9, 2)")).isEqualTo("2");
        assertThat(Maths.calculate("MAX(4, 9) * 2")).isEqualTo("18");
    }

    @Test
    void leavesWhatIsNoCalculation() {
        assertThat(Maths.calculate("5")).isNull(); // no operator
        assertThat(Maths.calculate("player_name")).isNull();
        assertThat(Maths.calculate("1 / 0")).isNull();
        assertThat(Maths.calculate("5 % 0")).isNull();
        assertThat(Maths.calculate("2 +")).isNull();
        assertThat(Maths.calculate("(2 + 3")).isNull();
        assertThat(Maths.calculate("2 + 3)")).isNull();
        assertThat(Maths.calculate("min(4)")).isNull();
        assertThat(Maths.calculate("pow(2, 3)")).isNull();
        assertThat(Maths.calculate("\"text\":\"a-b\"")).isNull();
        assertThat(Maths.calculate("1.2.3 + 1")).isNull();
        assertThat(Maths.calculate("")).isNull();
    }

    @Test
    void fillsCalculationsInsideTexts() {
        assertThat(Maths.fill("You have {5 + 3} gold")).isEqualTo("You have 8 gold");
        assertThat(Maths.fill("{{5 + 3} * 2}")).isEqualTo("16");
        assertThat(Maths.fill("eco give @p gold {floor(25 / 2)}")).isEqualTo("eco give @p gold 12");
        assertThat(Maths.fill("{a} {5} {1/0}")).isEqualTo("{a} {5} {1/0}");
        assertThat(Maths.fill("tellraw @p {\"text\":\"a-b\"}")).isEqualTo("tellraw @p {\"text\":\"a-b\"}");
        assertThat(Maths.fill("{{input.amount} * 2}")).isEqualTo("{{input.amount} * 2}"); // not filled in yet
        assertThat(Maths.fill("} {2+2")).isEqualTo("} {2+2");
        assertThat(Maths.fill(null)).isNull();
    }
}
