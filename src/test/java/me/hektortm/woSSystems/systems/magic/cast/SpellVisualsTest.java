package me.hektortm.woSSystems.systems.magic.cast;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpellVisualsTest {

    @Test
    void growScalesOverTheLife() {
        assertThat(SpellVisuals.growScale("none", 0.3)).isEqualTo(1.0);
        assertThat(SpellVisuals.growScale("out", 0)).isZero();
        assertThat(SpellVisuals.growScale("out", 0.5)).isCloseTo(0.5, within(1e-9));
        assertThat(SpellVisuals.growScale("in", 1)).isZero();
        assertThat(SpellVisuals.growScale("in", 0.25)).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void billboardBytes() {
        assertThat(SpellVisuals.billboard("fixed")).isEqualTo((byte) 0);
        assertThat(SpellVisuals.billboard("vertical")).isEqualTo((byte) 1);
        assertThat(SpellVisuals.billboard("center")).isEqualTo((byte) 3);
    }

    @Test
    void hexColours() {
        assertThat(SpellVisuals.rgb("#FF8000")).isEqualTo(0xFF8000);
        assertThat(SpellVisuals.rgb("wand")).isEqualTo(-1);
        assertThat(SpellVisuals.rgb("")).isEqualTo(-1);
    }
}
