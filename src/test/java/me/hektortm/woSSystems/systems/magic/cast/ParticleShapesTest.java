package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.utils.Maths;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ParticleShapesTest {

    private static ParticleShapes.Params plain(String shape, int points, double r, double h, double turns) {
        return ParticleShapes.Params.plain(shape, points, r, h, turns);
    }

    @Test
    void ringPointsLieOnTheRadiusAndFlat() {
        List<double[]> pts = ParticleShapes.draw(plain("ring", 16, 2, 0, 0), 0, 1);
        assertThat(pts).hasSize(16);
        for (double[] q : pts) {
            assertThat(Math.hypot(q[0], q[2])).isCloseTo(2, within(1e-9));
            assertThat(q[1]).isZero();
        }
        assertThat(pts.get(0)[3]).isZero();
        assertThat(pts.get(15)[3]).isEqualTo(1.0);
    }

    @Test
    void cubePointsLieOnItsEdges() {
        List<double[]> pts = ParticleShapes.draw(plain("cube", 48, 2, 0, 0), 0, 1);
        assertThat(pts).hasSize(48);
        for (double[] q : pts) {
            int onFace = 0;
            for (int k = 0; k < 3; k++) {
                assertThat(Math.abs(q[k])).isLessThanOrEqualTo(2 + 1e-9);
                if (Math.abs(Math.abs(q[k]) - 2) < 1e-9) onFace++;
            }
            assertThat(onFace).as("an edge point is on two faces").isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void coneHasABaseRingAndSlopesToTheTip() {
        List<double[]> pts = ParticleShapes.draw(plain("cone", 40, 2, 3, 0), 0, 1);
        assertThat(pts).hasSize(40);
        boolean tip = false;
        for (double[] q : pts) {
            assertThat(q[1]).isBetween(-1e-9, 3 + 1e-9);
            double expected = 2 * (1 - q[1] / 3); // radius shrinks linearly to the tip
            assertThat(Math.hypot(q[0], q[2])).isCloseTo(expected, within(1e-9));
            if (Math.abs(q[1] - 3) < 1e-9) tip = true;
        }
        assertThat(tip).isTrue();
        assertThat(pts.stream().filter(q -> q[1] == 0).count()).isEqualTo(20);
    }

    @Test
    void heartStandsInItsPlaneWithinTheRadius() {
        List<double[]> pts = ParticleShapes.draw(plain("heart", 60, 2, 0, 0), 0, 1);
        assertThat(pts).hasSize(60);
        for (double[] q : pts) {
            assertThat(q[2]).isCloseTo(0, within(1e-12));
            assertThat(Math.abs(q[0])).isLessThanOrEqualTo(2 + 1e-9);
            assertThat(q[1]).isBetween(-2.2, 1.6);
        }
        // the lowest point is the tip, in the middle
        double[] low = pts.stream().min((a, b) -> Double.compare(a[1], b[1])).orElseThrow();
        assertThat(low[0]).isCloseTo(0, within(0.2));
    }

    @Test
    void sphereAndDome() {
        for (double[] q : ParticleShapes.draw(plain("sphere", 50, 3, 0, 0), 0, 1)) {
            assertThat(Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2])).isCloseTo(3, within(1e-9));
        }
        assertThat(ParticleShapes.draw(plain("dome", 50, 3, 0, 0), 0, 1)).allSatisfy(q -> assertThat(q[1]).isPositive());
    }

    @Test
    void helixRisesAlongTheAxisAndStrandsSplitThePoints() {
        var p = new ParticleShapes.Params("helix", 20, 1, 4, 2, 2, 5, null, null, null, 0, "none", false);
        List<double[]> pts = ParticleShapes.draw(p, 0, 1);
        assertThat(pts).hasSize(20);
        // The two strands start opposite each other at the bottom and both reach the top.
        assertThat(pts.get(0)[0]).isCloseTo(1, within(1e-9));
        assertThat(pts.get(1)[0]).isCloseTo(-1, within(1e-9));
        assertThat(pts.get(18)[1]).isCloseTo(4, within(1e-9));
        assertThat(pts.get(19)[1]).isCloseTo(4, within(1e-9));
    }

    @Test
    void starAlternatesOuterAndInnerCorners() {
        var p = new ParticleShapes.Params("star", 10, 1, 0, 0, 1, 5, null, null, null, 0, "none", false);
        List<double[]> pts = ParticleShapes.draw(p, 0, 1);
        assertThat(Math.hypot(pts.get(0)[0], pts.get(0)[2])).isCloseTo(1, within(1e-9));
        assertThat(Math.hypot(pts.get(1)[0], pts.get(1)[2])).isCloseTo(0.4, within(1e-9));
    }

    @Test
    void formulaUsesTheVariablesAndDropsPointsThatFail() {
        Maths.Formula x = Maths.compile("cos(a) * r + t", ParticleShapes.VARIABLES);
        Maths.Formula y = Maths.compile("p * h", ParticleShapes.VARIABLES);
        Maths.Formula z = Maths.compile("1 / i", ParticleShapes.VARIABLES); // i = 0 fails
        var p = new ParticleShapes.Params("formula", 4, 2, 6, 0, 1, 5, x, y, z, 0, "none", false);
        List<double[]> pts = ParticleShapes.draw(p, 1, 1);
        assertThat(pts).hasSize(3);
        assertThat(pts.get(0)[0]).isCloseTo(Math.cos(Math.PI / 2) * 2 + 1, within(1e-9)); // i = 1
        assertThat(pts.get(2)[1]).isCloseTo(6, within(1e-9));                             // i = 3, p = 1
    }

    @Test
    void spinGrowAndTrace() {
        var spin = new ParticleShapes.Params("ring", 4, 1, 0, 0, 1, 5, null, null, null, 90, "none", false);
        double[] first = ParticleShapes.draw(spin, 1, 1).get(0); // a quarter turn after one second
        assertThat(first[0]).isCloseTo(0, within(1e-9));
        assertThat(first[2]).isCloseTo(1, within(1e-9));

        var grow = new ParticleShapes.Params("ring", 4, 2, 0, 0, 1, 5, null, null, null, 0, "out", false);
        assertThat(ParticleShapes.draw(grow, 0, 0.5).get(0)[0]).isCloseTo(1, within(1e-9));

        var trace = new ParticleShapes.Params("ring", 10, 1, 0, 0, 1, 5, null, null, null, 0, "none", true);
        assertThat(ParticleShapes.draw(trace, 0, 0.3)).hasSize(3);
        assertThat(ParticleShapes.draw(trace, 0, 1)).hasSize(10);
    }

    @Test
    void framesAreOrthonormalAndPointTheAxisRight() {
        for (Vector dir : List.of(new Vector(1, 0.3, 0), new Vector(0, 1, 0), new Vector(0, -1, 0), new Vector(0.2, -0.5, -1))) {
            for (boolean look : new boolean[]{false, true}) {
                Vector[] f = ParticleShapes.frame(dir, look);
                for (Vector v : f) assertThat(v.length()).isCloseTo(1, within(1e-9));
                assertThat(f[0].dot(f[1])).isCloseTo(0, within(1e-9));
                assertThat(f[1].dot(f[2])).isCloseTo(0, within(1e-9));
                assertThat(f[0].dot(f[2])).isCloseTo(0, within(1e-9));
                Vector axis = look ? dir.clone().normalize() : new Vector(0, 1, 0);
                assertThat(f[1].dot(axis)).isCloseTo(1, within(1e-9));
            }
        }
    }

    @Test
    void trails() {
        Vector at = new Vector(0, 0, 0), dir = new Vector(0, 0, 1);
        assertThat(ParticleShapes.trail("line", at, dir, 3, 0.5, 0.3, 0.5)).hasSize(1);
        List<Vector> dbl = ParticleShapes.trail("double_helix", at, dir, 3, 0.5, 0.3, 0.5);
        assertThat(dbl).hasSize(2);
        assertThat(dbl.get(0).clone().add(dbl.get(1)).length()).isCloseTo(0, within(1e-9)); // opposite each other
        assertThat(dbl.get(0).length()).isCloseTo(0.3, within(1e-9));
        assertThat(ParticleShapes.trail("rings", at, dir, 3.1, 0.25, 0.3, 0.5)).hasSize(12);  // passed block 3
        assertThat(ParticleShapes.trail("rings", at, dir, 3.6, 0.25, 0.3, 0.5)).isEmpty();
    }
}
