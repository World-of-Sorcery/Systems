package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Push / pull and turn (magic spec §3.3), without the server. */
class MovementTest {

    static final Vector CASTER = new Vector(0, 64, 0);
    static final Vector TARGET = new Vector(4, 64, 0);
    static final Vector POINT = new Vector(0, 64, 4);
    static final Vector CASTER_LOOK = new Vector(0, 0, -1);
    static final Vector TARGET_LOOK = new Vector(-1, 0, 0);
    static final Vector FIXED = new Vector(0, 0, 0.5);

    static Vector push(String direction) {
        return Movement.push(direction, CASTER, TARGET, POINT, CASTER_LOOK, TARGET_LOOK, FIXED, 1, 0);
    }

    static void near(Vector v, double x, double y, double z) {
        assertThat(v.getX()).isCloseTo(x, within(1e-6));
        assertThat(v.getY()).isCloseTo(y, within(1e-6));
        assertThat(v.getZ()).isCloseTo(z, within(1e-6));
    }

    @Test
    void everyDirection() {
        near(push("away_caster"), 0.6, 0, 0);
        near(push("toward_caster"), -0.6, 0, 0);
        near(push("away_point"), 0.6 * 4 / Math.sqrt(32), 0, -0.6 * 4 / Math.sqrt(32));
        near(push("toward_point"), -0.6 * 4 / Math.sqrt(32), 0, 0.6 * 4 / Math.sqrt(32));
        near(push("caster_look"), 0, 0, -0.6);
        near(push("target_look"), -0.6, 0, 0);
        near(push("up"), 0, 0.6, 0);
        near(push("vector"), 0, 0, 0.6); // normalised
    }

    @Test
    void liftIsAddedUpward() {
        near(Movement.push("away_caster", CASTER, TARGET, POINT, CASTER_LOOK, TARGET_LOOK, FIXED, 2, 0.5), 1.2, 0.5, 0);
    }

    @Test
    void noDirectionFallsBackToTheCastersLook() {
        // The caster pushing themself away from themself, or a zero vector.
        near(Movement.push("away_caster", CASTER, CASTER, POINT, CASTER_LOOK, TARGET_LOOK, FIXED, 1, 0), 0, 0, -0.6);
        near(Movement.push("vector", CASTER, TARGET, POINT, CASTER_LOOK, TARGET_LOOK, new Vector(), 1, 0), 0, 0, -0.6);
        near(Movement.push("away_caster", CASTER, CASTER, POINT, new Vector(), TARGET_LOOK, FIXED, 1, 0), 0, 0, 0.6);
    }

    @Test
    void capsSpeed() {
        Vector v = Movement.cap(new Vector(3, 4, 0), 4);
        assertThat(v.length()).isCloseTo(4, within(1e-6));
        near(Movement.cap(new Vector(1, 1, 0), 4), 1, 1, 0);
    }

    @Test
    void turnTargets() {
        Vector eye = new Vector(0, 65.6, 0);
        Vector look = new Vector(0, 0, 1);
        near(Movement.lookPoint("point", eye, look, POINT, CASTER, 0, 0), 0, 64, 4);
        near(Movement.lookPoint("caster", eye, look, POINT, new Vector(1, 65, 1), 0, 0), 1, 65, 1);
        near(Movement.lookPoint("around", eye, look, POINT, CASTER, 0, 0), 0, 65.6, -1);
        near(Movement.lookPoint("set", eye, look, POINT, CASTER, 90, 0), -1, 65.6, 0); // yaw 90 = west
        near(Movement.lookPoint("set", eye, look, POINT, CASTER, 0, -90), 0, 66.6, 0); // pitch -90 = up
    }
}
