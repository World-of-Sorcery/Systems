package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/** The pure maths of the cone, chain, orbit and the area forms (more-nodes spec §4.3). */
final class Geometry {

    private Geometry() { }

    /** Whether {@code p} is within {@code range} of {@code apex} and within half of {@code angleDeg} of {@code axis}. */
    static boolean inCone(Vector apex, Vector axis, double range, double angleDeg, Vector p) {
        Vector to = p.clone().subtract(apex);
        double d = to.length();
        if (d > range) return false;
        if (d < 1.0E-9) return true;
        return Math.toDegrees(axis.angle(to)) <= angleDeg / 2;
    }

    /** A round column of radius {@code r} standing {@code h} high on {@code c}. */
    static boolean inCylinder(Vector c, double r, double h, Vector p) {
        double dy = p.getY() - c.getY();
        if (dy < 0 || dy > h) return false;
        double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
        return dx * dx + dz * dz <= r * r;
    }

    /**
     * A wall standing on {@code c} across {@code facing} (made flat): {@code width} wide,
     * {@code height} high and 1 block thick.
     */
    static boolean inWall(Vector c, Vector facing, double width, double height, Vector p) {
        Vector along = ParticleShapes.horizontal(facing);
        Vector across = new Vector(-along.getZ(), 0, along.getX());
        Vector d = p.clone().subtract(c);
        if (d.getY() < 0 || d.getY() > height) return false;
        return Math.abs(d.dot(along)) <= 0.5 && Math.abs(d.dot(across)) <= width / 2;
    }

    /** Orb {@code i} of {@code n}, evenly around {@code c} at {@code r}, {@code h} up, the whole ring turned {@code angleDeg}. */
    static Vector orbitPoint(Vector c, double r, double h, double angleDeg, int i, int n) {
        double a = Math.toRadians(angleDeg + 360.0 * i / Math.max(1, n));
        return c.clone().add(new Vector(Math.cos(a) * r, h, Math.sin(a) * r));
    }

    /**
     * The blocks of a fake block form, as offsets from the point's block: single; a sphere
     * of {@code r}; a flat ring at {@code r}; a column {@code r} up; a wall {@code 2r + 1}
     * wide across {@code facing} (made flat) and {@code r + 1} high.
     */
    static List<int[]> fakeCells(String form, int r, Vector facing) {
        Map<String, int[]> cells = new LinkedHashMap<>();
        java.util.function.Consumer<int[]> add = c -> cells.putIfAbsent(c[0] + "," + c[1] + "," + c[2], c);
        switch (form) {
            case "sphere" -> {
                for (int x = -r; x <= r; x++)
                    for (int y = -r; y <= r; y++)
                        for (int z = -r; z <= r; z++) if (x * x + y * y + z * z <= r * r) add.accept(new int[]{x, y, z});
            }
            case "ring" -> {
                if (r == 0) add.accept(new int[]{0, 0, 0});
                for (int x = -r; x <= r; x++)
                    for (int z = -r; z <= r; z++) {
                        double d = Math.hypot(x, z);
                        if (r > 0 && d >= r - 0.5 && d < r + 0.5) add.accept(new int[]{x, 0, z});
                    }
            }
            case "column" -> {
                for (int y = 0; y <= r; y++) add.accept(new int[]{0, y, 0});
            }
            case "wall" -> {
                Vector along = ParticleShapes.horizontal(facing);
                double ax = -along.getZ(), az = along.getX();
                for (int s = -r; s <= r; s++)
                    for (int y = 0; y <= r; y++) add.accept(new int[]{(int) Math.round(ax * s), y, (int) Math.round(az * s)});
            }
            default -> add.accept(new int[]{0, 0, 0});
        }
        return new ArrayList<>(cells.values());
    }

    /**
     * The nearest candidate within {@code range} of {@code from} that is not {@code used} and
     * that {@code allowed} accepts (line of sight, PvP …), or -1.
     */
    static int chainNext(Vector from, List<Vector> candidates, double range, Set<Integer> used, IntPredicate allowed) {
        int best = -1;
        double bestD = range * range;
        for (int i = 0; i < candidates.size(); i++) {
            if (used.contains(i)) continue;
            double d = candidates.get(i).distanceSquared(from);
            if (d <= bestD && allowed.test(i)) {
                best = i;
                bestD = d;
            }
        }
        return best;
    }
}
