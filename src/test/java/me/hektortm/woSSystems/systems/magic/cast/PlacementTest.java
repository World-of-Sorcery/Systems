package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The maths of Move, Random and Check (more-nodes spec §4.2). */
class PlacementTest {

    private static final double E = 1.0E-9;

    @Test
    void offsetFlat() {
        // a tilted facing still moves forward along z; right of south is west
        Vector v = Placement.offset(new Vector(0, 64, 0), new Vector(0, -1, 1), 3, 2, 1);
        assertThat(v.getX()).isCloseTo(-1, within(E));
        assertThat(v.getY()).isCloseTo(66, within(E));
        assertThat(v.getZ()).isCloseTo(3, within(E));
        // looking straight down: falls back to south instead of a zero vector
        Vector d = Placement.offset(new Vector(0, 0, 0), new Vector(0, -1, 0), 2, 0, 0);
        assertThat(d.getZ()).isCloseTo(2, within(E));
    }

    @Test
    void facingEveryMode() {
        Vector keep = new Vector(1, 0, 0);
        Vector look = new Vector(0, 0, 1);
        Vector point = new Vector(0, 0, 0);
        Vector caster = new Vector(0, 0, 10);
        Vector target = new Vector(-5, 0, 0);
        Random r = new Random(1);
        assertThat(Placement.facing("keep", keep, look, point, caster, target, r)).isEqualTo(keep);
        assertThat(Placement.facing("caster_look", keep, look, point, caster, target, r)).isEqualTo(look);
        assertThat(Placement.facing("up", keep, look, point, caster, target, r)).isEqualTo(new Vector(0, 1, 0));
        assertThat(Placement.facing("down", keep, look, point, caster, target, r)).isEqualTo(new Vector(0, -1, 0));
        assertThat(Placement.facing("toward_caster", keep, look, point, caster, target, r)).isEqualTo(new Vector(0, 0, 1));
        assertThat(Placement.facing("away_caster", keep, look, point, caster, target, r)).isEqualTo(new Vector(0, 0, -1));
        assertThat(Placement.facing("toward_target", keep, look, point, caster, target, r)).isEqualTo(new Vector(-1, 0, 0));
        assertThat(Placement.facing("toward_target", keep, look, point, caster, null, r)).isEqualTo(keep);
        assertThat(Placement.facing("random", keep, look, point, caster, target, r).length()).isCloseTo(1, within(E));
    }

    @Test
    void pickDistinct() {
        int[] got = Placement.pick(3, 5, new Random(7));
        assertThat(got).hasSize(3).doesNotHaveDuplicates();
        assertThat(Arrays.stream(got).allMatch(i -> i >= 0 && i < 5)).isTrue();
    }

    @Test
    void pickMoreThanSize() {
        int[] got = Placement.pick(5, 2, new Random(7));
        assertThat(Arrays.stream(got).sorted().toArray()).containsExactly(0, 1);
    }

    @Test
    void compareBelowAbove() {
        assertThat(Placement.compare("below", 4, 5)).isTrue();
        assertThat(Placement.compare("below", 5, 5)).isFalse();
        assertThat(Placement.compare("above", 6, 5)).isTrue();
        assertThat(Placement.compare("above", 5, 5)).isFalse();
    }
}
