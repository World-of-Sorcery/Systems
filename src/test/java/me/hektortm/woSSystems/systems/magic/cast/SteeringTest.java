package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SteeringTest {

    @Test
    void turnsAtOnceWhenZero() {
        Vector vel = new Vector(1, 0, 0);
        Vector desired = new Vector(0, 1, 0);
        Vector turned = Steering.turnToward(vel, desired, 0);
        assertThat(turned.getX()).isCloseTo(0.0, within(1e-6));
        assertThat(turned.getY()).isCloseTo(1.0, within(1e-6));
        assertThat(turned.getZ()).isCloseTo(0.0, within(1e-6));
        assertThat(turned.length()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void turnLimitedPerTick() {
        Vector vel = new Vector(2, 0, 0);
        Vector desired = new Vector(0, 2, 0);
        Vector turned = Steering.turnToward(vel, desired, 10);
        assertThat(turned.length()).isCloseTo(2.0, within(1e-6));
        double angle = Math.toDegrees(vel.angle(turned));
        assertThat(angle).isCloseTo(10.0, within(1e-6));
    }

    @Test
    void reflectOffFloor() {
        Vector vel = new Vector(1, -1, 0);
        Vector normal = new Vector(0, 1, 0);
        Vector reflected = Steering.reflect(vel, normal);
        assertThat(reflected.getX()).isCloseTo(1.0, within(1e-6));
        assertThat(reflected.getY()).isCloseTo(1.0, within(1e-6));
        assertThat(reflected.getZ()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void fanIsSymmetric() {
        Vector dir = new Vector(0, 0, 1);
        List<Vector> fan = Steering.spread(dir, 3, 10, "fan", new Random(42));
        assertThat(fan).hasSize(3);
        double midAngle = Math.toDegrees(dir.angle(fan.get(1)));
        assertThat(midAngle).isCloseTo(0.0, within(1e-6));
        double leftAngle = Math.toDegrees(dir.angle(fan.get(0)));
        double rightAngle = Math.toDegrees(dir.angle(fan.get(2)));
        assertThat(leftAngle).isCloseTo(10.0, within(1e-6));
        assertThat(rightAngle).isCloseTo(10.0, within(1e-6));
        assertThat(fan.get(0).getX()).isCloseTo(-fan.get(2).getX(), within(1e-6));
    }

    @Test
    void randomStaysInCone() {
        Vector dir = new Vector(1, 0, 0);
        Random r = new Random(123);
        List<Vector> spread = Steering.spread(dir, 20, 15, "random", r);
        assertThat(spread).hasSize(20);
        for (Vector v : spread) {
            double angle = Math.toDegrees(dir.angle(v));
            assertThat(angle).isLessThanOrEqualTo(15.0 + 1e-6);
        }
    }

    @Test
    void spreadCountMatches() {
        Vector dir = new Vector(0.3, -0.2, 1);
        for (int count = 1; count <= 8; count++) {
            assertThat(Steering.spread(dir, count, 10, "fan", new Random(1))).hasSize(count);
            assertThat(Steering.spread(dir, count, 10, "random", new Random(1))).hasSize(count);
        }
    }

    @Test
    void reflectOffWall() {
        Vector reflected = Steering.reflect(new Vector(1, 0, 0.5), new Vector(-1, 0, 0));
        assertThat(reflected.getX()).isCloseTo(-1.0, within(1e-6));
        assertThat(reflected.getY()).isCloseTo(0.0, within(1e-6));
        assertThat(reflected.getZ()).isCloseTo(0.5, within(1e-6));
    }

    @Test
    void returnHeadsToCaster() {
        Vector vel = new Vector(0, 0, 1.5);
        Vector toCaster = new Vector(0, 1, -10);
        Vector back = Steering.turnToward(vel, toCaster, 0);
        assertThat(back.length()).isCloseTo(1.5, within(1e-6));
        assertThat(Math.toDegrees(back.angle(toCaster))).isCloseTo(0.0, within(1e-4));
    }

    @Test
    void speedEndsAt005() {
        assertThat(Steering.nextSpeed(1.0, 0.1)).isEqualTo(1.1);
        assertThat(Steering.nextSpeed(3.9, 0.2)).isEqualTo(4.0);
        assertThat(Steering.nextSpeed(0.06, -0.02)).isEqualTo(0.0);
        assertThat(Steering.nextSpeed(0.05, 0)).isEqualTo(0.0);
    }
}
