package me.hektortm.woSSystems.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompareTest {

    @Test
    void comparesNumbersAsNumbers() {
        assertThat(Compare.test("compare_greater_than", "10", "9")).isTrue();
        assertThat(Compare.test("compare_greater_than", "10", "10")).isFalse();
        assertThat(Compare.test("compare_at_least", "10", "10")).isTrue();
        assertThat(Compare.test("compare_less_than", "2.5", "3")).isTrue();
        assertThat(Compare.test("compare_at_most", "3", "2.5")).isFalse();
        assertThat(Compare.test("compare_equal_to", " 10 ", "10.0")).isTrue();
        assertThat(Compare.test("compare_not_equal_to", "-1", "1")).isTrue();
        assertThat(Compare.test("COMPARE_GREATER_THAN", "100", "99")).isTrue(); // 100 > 99, not as text
    }

    @Test
    void comparesTextsWithoutColoursAndCase() {
        assertThat(Compare.test("compare_equal_to", "&aYes", "yes")).isTrue();
        assertThat(Compare.test("compare_equal_to", "§#ff0000Red", "red")).isTrue();
        assertThat(Compare.test("compare_not_equal_to", "fire", "water")).isTrue();
        assertThat(Compare.test("compare_equal_to", "", "")).isTrue();
        assertThat(Compare.test("compare_greater_than", "b", "a")).isFalse();
        assertThat(Compare.test("compare_less_than", "5", "five")).isFalse();
    }

    @Test
    void knowsItsNames() {
        assertThat(Compare.is("compare_at_least")).isTrue();
        assertThat(Compare.is("has_stats_equal_to")).isFalse();
        assertThat(Compare.is(null)).isFalse();
        assertThat(Compare.test("compare_sideways", "1", "1")).isFalse();
    }
}
