package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.interactions.DisplayRules;
import me.hektortm.woSSystems.systems.interactions.DisplaySettings.Vec3;
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
    void horizontalBillboard() {
        assertThat(SpellVisuals.billboard("horizontal")).isEqualTo((byte) 2);
    }

    @Test
    void textStyleBytes() {
        assertThat(SpellVisuals.textFlags(false, false, false, "center")).isEqualTo((byte) 0);
        assertThat(SpellVisuals.textFlags(true, true, true, "left")).isEqualTo((byte) 0x0F);
        assertThat(SpellVisuals.textFlags(false, false, false, "right")).isEqualTo((byte) 0x10);
        assertThat(SpellVisuals.background("", 64)).isEqualTo(0x40000000); // Minecraft's default
        assertThat(SpellVisuals.background("#112233", 255)).isEqualTo(0xFF112233);
        assertThat(SpellVisuals.background("#112233", 0)).isEqualTo(0x00112233);
    }

    @Test
    void brightnessIsWorldOrPacked() {
        assertThat(SpellVisuals.brightness("world", 15, 15)).isEqualTo(-1);
        assertThat(SpellVisuals.brightness("fixed", 15, 15)).isEqualTo(15 << 4 | 15 << 20);
        assertThat(SpellVisuals.brightness("fixed", 99, -3)).isEqualTo(15 << 4);
    }

    @Test
    void spinTurnsAfterTheTilt() {
        var none = new Vec3(0, 0, 0);
        var tilted = look(new Vec3(90, 0, 0), 0, 'y');
        assertThat(SpellVisuals.rotation(tilted, 1)).isEqualTo(DisplayRules.turn('x', 90));
        var spinning = look(none, 90, 'y');
        DisplayRules.Quat q = SpellVisuals.rotation(spinning, 2); // 180° around y after 2 s
        assertThat(q.y()).isCloseTo(1f, within(1e-6f));
        var both = look(new Vec3(90, 0, 0), 90, 'z');
        DisplayRules.Quat want = DisplayRules.multiply(DisplayRules.turn('z', 90), DisplayRules.turn('x', 90));
        assertThat(SpellVisuals.rotation(both, 1)).isEqualTo(want);
    }

    private static SpellVisuals.DisplayLook look(Vec3 tilt, double spin, char axis) {
        Vec3 one = new Vec3(1, 1, 1);
        return new SpellVisuals.DisplayLook("item", "minecraft:stone", "", "none", "", "", SpellVisuals.TextStyle.PLAIN, 1, one, tilt,
                new Vec3(0, 0, 0), 0, spin, axis, "fixed", "none", false, "", -1, 1, 0, 1, 3);
    }

    @Test
    void hexColours() {
        assertThat(SpellVisuals.rgb("#FF8000")).isEqualTo(0xFF8000);
        assertThat(SpellVisuals.rgb("wand")).isEqualTo(-1);
        assertThat(SpellVisuals.rgb("")).isEqualTo(-1);
    }
}
