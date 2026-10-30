package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeometryTest {

    private static final Vector O = new Vector(0, 64, 0);

    @Test
    void coneEdge() {
        Vector axis = new Vector(0, 0, 1);
        // 60° full opening: 30° each side; range 6
        Vector inside = O.clone().add(new Vector(Math.sin(Math.toRadians(29)), 0, Math.cos(Math.toRadians(29))).multiply(5));
        Vector outsideAngle = O.clone().add(new Vector(Math.sin(Math.toRadians(31)), 0, Math.cos(Math.toRadians(31))).multiply(5));
        Vector outsideRange = O.clone().add(new Vector(0, 0, 6.1));
        assertThat(Geometry.inCone(O, axis, 6, 60, inside)).isTrue();
        assertThat(Geometry.inCone(O, axis, 6, 60, outsideAngle)).isFalse();
        assertThat(Geometry.inCone(O, axis, 6, 60, outsideRange)).isFalse();
        assertThat(Geometry.inCone(O, axis, 6, 60, O.clone().add(new Vector(0, 0, -1)))).isFalse();
        assertThat(Geometry.inCone(O, axis, 6, 180, O.clone().add(new Vector(5, 0, 0.01)))).isTrue();
    }

    @Test
    void cylinderHeight() {
        assertThat(Geometry.inCylinder(O, 3, 2, O.clone().add(new Vector(2.9, 1.9, 0)))).isTrue();
        assertThat(Geometry.inCylinder(O, 3, 2, O.clone().add(new Vector(0, 2.1, 0)))).isFalse();
        assertThat(Geometry.inCylinder(O, 3, 2, O.clone().add(new Vector(0, -0.1, 0)))).isFalse();
        assertThat(Geometry.inCylinder(O, 3, 2, O.clone().add(new Vector(2.2, 0, 2.2)))).isFalse();
    }

    @Test
    void wallThickness() {
        Vector facing = new Vector(0, -0.5, 1); // flattened to +z
        assertThat(Geometry.inWall(O, facing, 6, 3, O.clone().add(new Vector(2.9, 2.9, 0.4)))).isTrue();
        assertThat(Geometry.inWall(O, facing, 6, 3, O.clone().add(new Vector(0, 1, 0.6)))).isFalse();
        assertThat(Geometry.inWall(O, facing, 6, 3, O.clone().add(new Vector(3.1, 1, 0)))).isFalse();
        assertThat(Geometry.inWall(O, facing, 6, 3, O.clone().add(new Vector(0, 3.1, 0)))).isFalse();
    }

    @Test
    void orbitEvenlySpaced() {
        Vector a = Geometry.orbitPoint(O, 2, 1, 0, 0, 4);
        Vector b = Geometry.orbitPoint(O, 2, 1, 0, 1, 4);
        Vector c = Geometry.orbitPoint(O, 2, 1, 0, 2, 4);
        for (Vector v : List.of(a, b, c)) {
            assertThat(v.getY()).isCloseTo(65.0, within(1e-9));
            assertThat(Math.hypot(v.getX(), v.getZ())).isCloseTo(2.0, within(1e-9));
        }
        assertThat(a.distance(b)).isCloseTo(Math.sqrt(8), within(1e-9));
        assertThat(a.distance(c)).isCloseTo(4.0, within(1e-9));
        Vector turned = Geometry.orbitPoint(O, 2, 1, 90, 0, 4);
        assertThat(turned.distance(b)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void chainNearestUnused() {
        List<Vector> c = List.of(new Vector(1, 0, 0), new Vector(3, 0, 0), new Vector(10, 0, 0));
        Set<Integer> used = new HashSet<>();
        assertThat(Geometry.chainNext(new Vector(0, 0, 0), c, 5, used, i -> true)).isEqualTo(0);
        used.add(0);
        assertThat(Geometry.chainNext(new Vector(0, 0, 0), c, 5, used, i -> true)).isEqualTo(1);
        used.add(1);
        assertThat(Geometry.chainNext(new Vector(0, 0, 0), c, 5, used, i -> true)).isEqualTo(-1);
    }

    @Test
    void fakeBlockForms() {
        Vector north = new Vector(0, 0, 1);
        assertThat(Geometry.fakeCells("single", 3, north)).containsExactly(new int[]{0, 0, 0});
        assertThat(Geometry.fakeCells("sphere", 1, north)).hasSize(7);
        assertThat(Geometry.fakeCells("column", 2, north)).containsExactly(new int[]{0, 0, 0}, new int[]{0, 1, 0}, new int[]{0, 2, 0});
        List<int[]> ring = Geometry.fakeCells("ring", 2, north);
        assertThat(ring).isNotEmpty().allSatisfy(c -> {
            assertThat(c[1]).isZero();
            assertThat(Math.hypot(c[0], c[2])).isBetween(1.5, 2.5);
        });
        List<int[]> wall = Geometry.fakeCells("wall", 1, north); // 3 wide across the facing, 2 high
        assertThat(wall).hasSize(6).allSatisfy(c -> assertThat(c[2]).isZero());
        assertThat(Geometry.fakeCells("sphere", 0, north)).hasSize(1);
    }

    @Test
    void chainSkipsRejected() {
        List<Vector> c = List.of(new Vector(1, 0, 0), new Vector(2, 0, 0));
        assertThat(Geometry.chainNext(new Vector(0, 0, 0), c, 5, Set.of(), i -> i != 0)).isEqualTo(1);
        assertThat(Geometry.chainNext(new Vector(0, 0, 0), c, 5, Set.of(), i -> false)).isEqualTo(-1);
    }
}
