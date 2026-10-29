package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.model.SpellNode;
import me.hektortm.woSSystems.utils.Maths;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of spell particles (wos-api {@code spellgraph}: the {@code particle_shape}
 * effect, the trails of projectiles and beams, the area patterns). Pure: points come out in
 * a local frame where y runs along the shape's axis and x / z across it; {@link #frame}
 * turns that into the world.
 */
public final class ParticleShapes {

    private ParticleShapes() { }

    private static final double GOLDEN = Math.PI * (3 - Math.sqrt(5));

    /** The variables of a particle formula, in the order {@link #point} passes them. */
    static final String[] VARIABLES = {"i", "n", "p", "a", "t", "life", "r", "h"};

    /** What one particle shape draws; formulas only for the shape "formula". */
    record Params(String shape, int points, double radius, double height, double turns, int strands, int sides,
                  @Nullable Maths.Formula x, @Nullable Maths.Formula y, @Nullable Maths.Formula z,
                  double spinDegrees, String grow, boolean trace) {

        /** @throws IllegalArgumentException when a formula can't be read (the API checks them, so only a bad hand edit) */
        static Params of(SpellNode n) {
            String shape = n.str("shape");
            boolean formula = "formula".equals(shape);
            return new Params(shape, Math.max(1, n.integer("points")), n.num("radius"), n.num("height"), n.num("turns"),
                    Math.max(1, n.integer("strands")), Math.max(3, n.integer("sides")),
                    formula ? Maths.compile(n.str("x"), VARIABLES) : null,
                    formula ? Maths.compile(n.str("y"), VARIABLES) : null,
                    formula ? Maths.compile(n.str("z"), VARIABLES) : null,
                    n.num("spin"), n.str("grow"), "trace".equals(n.str("reveal")));
        }

        /** A plain shape without animation (the area patterns). */
        static Params plain(String shape, int points, double radius, double height, double turns) {
            return new Params(shape, points, radius, height, turns, 1, 5, null, null, null, 0, "none", false);
        }
    }

    /**
     * The points of a shape at {@code t} seconds, {@code life} (0 to 1) through its
     * duration: each {x, y, z, p} with p its place along the shape (0 to 1, for colour
     * fades). Spin, grow and trace are applied; a point whose formula fails is left out.
     */
    static List<double[]> draw(Params s, double t, double life) {
        int n = s.points();
        int shown = s.trace() ? (int) Math.ceil(n * Math.max(0, Math.min(1, life))) : n;
        double scale = switch (s.grow()) {
            case "out" -> life;
            case "in" -> 1 - life;
            case "pulse" -> 0.75 + 0.25 * Math.sin(2 * Math.PI * t);
            default -> 1;
        };
        double spin = Math.toRadians(s.spinDegrees() * t);
        double cos = Math.cos(spin), sin = Math.sin(spin);
        List<double[]> out = new ArrayList<>(shown);
        for (int i = 0; i < shown; i++) {
            double[] q = point(s, i, t, life);
            if (q == null) continue;
            double x = q[0] * scale, y = q[1] * scale, z = q[2] * scale;
            out.add(new double[]{x * cos - z * sin, y, x * sin + z * cos, along(i, n)});
        }
        return out;
    }

    private static double along(int i, int n) {
        return n > 1 ? (double) i / (n - 1) : 0;
    }

    /** Point i of n in the local frame, or null when a formula can't be calculated. */
    @Nullable
    static double[] point(Params s, int i, double t, double life) {
        int n = s.points();
        double p = along(i, n);
        double a = 2 * Math.PI * i / n;
        double r = s.radius(), h = s.height();
        switch (s.shape()) {
            case "point" -> {
                return new double[]{0, 0, 0};
            }
            case "ring" -> {
                return new double[]{Math.cos(a) * r, 0, Math.sin(a) * r};
            }
            case "circle" -> {
                double d = r * Math.sqrt((i + 0.5) / n), g = i * GOLDEN;
                return new double[]{Math.cos(g) * d, 0, Math.sin(g) * d};
            }
            case "sphere", "dome" -> {
                double y = "sphere".equals(s.shape()) ? 1 - 2 * (i + 0.5) / n : (i + 0.5) / n;
                double w = Math.sqrt(Math.max(0, 1 - y * y)), g = i * GOLDEN;
                return new double[]{Math.cos(g) * w * r, y * r, Math.sin(g) * w * r};
            }
            case "helix", "vortex" -> {
                int strands = s.strands();
                int strand = i % strands;
                int per = (n + strands - 1) / strands;
                double q = per > 1 ? (double) (i / strands) / (per - 1) : 0;
                double ang = 2 * Math.PI * (s.turns() * q + (double) strand / strands);
                double rr = "vortex".equals(s.shape()) ? r * q : r;
                return new double[]{Math.cos(ang) * rr, h * q, Math.sin(ang) * rr};
            }
            case "spiral" -> {
                double ang = 2 * Math.PI * s.turns() * p;
                return new double[]{Math.cos(ang) * r * p, 0, Math.sin(ang) * r * p};
            }
            case "line" -> {
                return new double[]{0, h * p, 0};
            }
            case "wave" -> {
                return new double[]{Math.sin(2 * Math.PI * s.turns() * p) * r, h * p, 0};
            }
            case "polygon", "star" -> {
                return outline(s, i, n);
            }
            case "formula" -> {
                if (s.x() == null || s.y() == null || s.z() == null) return null;
                double[] vars = {i, n, p, a, t, life, r, h};
                try {
                    double x = s.x().eval(vars), y = s.y().eval(vars), z = s.z().eval(vars);
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
                    return new double[]{x, y, z};
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            default -> {
                return null;
            }
        }
    }

    /** A polygon (sides corners) or star (sides tips, inner corners at 40 %), the points spread along its outline. */
    private static double[] outline(Params s, int i, int n) {
        boolean star = "star".equals(s.shape());
        int corners = star ? s.sides() * 2 : s.sides();
        double u = (double) i / n * corners;
        int c = (int) Math.floor(u);
        double f = u - c;
        double[] from = corner(s, c, corners, star), to = corner(s, c + 1, corners, star);
        return new double[]{from[0] + (to[0] - from[0]) * f, 0, from[1] + (to[1] - from[1]) * f};
    }

    private static double[] corner(Params s, int c, int corners, boolean star) {
        double r = star && c % 2 == 1 ? s.radius() * 0.4 : s.radius();
        double ang = 2 * Math.PI * c / corners - Math.PI / 2;
        return new double[]{Math.cos(ang) * r, Math.sin(ang) * r};
    }

    /**
     * The world directions of the local x, y (the axis) and z. Facing up: y is straight up
     * and z the horizontal look direction; facing the look: y is the look direction and z
     * as close to up as it can be.
     */
    static Vector[] frame(Vector dir, boolean look) {
        Vector up = new Vector(0, 1, 0);
        Vector d = dir.lengthSquared() < 1e-8 ? new Vector(0, 0, 1) : dir.clone().normalize();
        if (!look) {
            Vector z = horizontal(d);
            return new Vector[]{z.clone().crossProduct(up).normalize(), up, z};
        }
        Vector z = up.clone().subtract(d.clone().multiply(up.dot(d)));
        if (z.lengthSquared() < 1e-6) z = new Vector(0, 0, 1).subtract(d.clone().multiply(d.getZ()));
        z.normalize();
        return new Vector[]{d.clone().crossProduct(z).normalize(), d, z};
    }

    /** The look direction flattened onto the ground (north when looking straight up or down). */
    static Vector horizontal(Vector dir) {
        Vector h = dir.clone().setY(0);
        return h.lengthSquared() < 1e-8 ? new Vector(0, 0, 1) : h.normalize();
    }

    /** Local point q placed at origin in the frame f. */
    static Vector place(Vector origin, Vector[] f, double x, double y, double z) {
        return origin.clone().add(f[0].clone().multiply(x)).add(f[1].clone().multiply(y)).add(f[2].clone().multiply(z));
    }

    /**
     * The trail particles at {@code at}, {@code distance} blocks along a path flying
     * {@code dir}: line one point, helix / double_helix winding around the path, rings a
     * ring of 12 when the path has just passed a whole block ({@code step}: the distance
     * since the last call).
     */
    static List<Vector> trail(String pattern, Vector at, Vector dir, double distance, double step, double radius, double turnsPerBlock) {
        List<Vector> out = new ArrayList<>();
        switch (pattern) {
            case "helix", "double_helix" -> {
                Vector[] f = frame(dir, true);
                double ang = 2 * Math.PI * turnsPerBlock * distance;
                int strands = "double_helix".equals(pattern) ? 2 : 1;
                for (int k = 0; k < strands; k++) {
                    double a = ang + Math.PI * k;
                    out.add(place(at, f, Math.cos(a) * radius, 0, Math.sin(a) * radius));
                }
            }
            case "rings" -> {
                if (Math.floor(distance) == Math.floor(distance - step) && distance > step) break;
                Vector[] f = frame(dir, true);
                for (int k = 0; k < 12; k++) {
                    double a = 2 * Math.PI * k / 12;
                    out.add(place(at, f, Math.cos(a) * radius, 0, Math.sin(a) * radius));
                }
            }
            default -> out.add(at.clone());
        }
        return out;
    }
}
