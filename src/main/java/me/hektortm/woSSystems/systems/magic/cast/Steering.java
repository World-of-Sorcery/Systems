package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Steering {

    private Steering() { }

    /**
     * Rotates {@code vel} toward {@code desired} by at most {@code maxDegrees}, preserving the speed of {@code vel}.
     * If {@code maxDegrees <= 0}, turns all the way at once.
     */
    public static Vector turnToward(Vector vel, Vector desired, double maxDegrees) {
        double speed = vel.length();
        if (speed < 1e-9 || desired.lengthSquared() < 1e-9) {
            return vel.clone();
        }
        Vector v = vel.clone().normalize();
        Vector d = desired.clone().normalize();
        double angleRad = v.angle(d);
        double angleDeg = Math.toDegrees(angleRad);
        if (maxDegrees <= 0 || angleDeg <= maxDegrees) {
            return d.multiply(speed);
        }
        Vector axis = v.clone().crossProduct(d);
        if (axis.lengthSquared() < 1e-9) {
            if (v.dot(d) < 0) {
                axis = Math.abs(v.getX()) < 0.9 ? new Vector(1, 0, 0).crossProduct(v) : new Vector(0, 1, 0).crossProduct(v);
            } else {
                return vel.clone();
            }
        }
        axis.normalize();
        double th = Math.toRadians(maxDegrees);
        Vector kCrossV = axis.clone().crossProduct(v);
        Vector rot = v.clone().multiply(Math.cos(th)).add(kCrossV.multiply(Math.sin(th)));
        return rot.normalize().multiply(speed);
    }

    /**
     * Reflects {@code vel} off a surface with given {@code normal}.
     */
    public static Vector reflect(Vector vel, Vector normal) {
        if (normal.lengthSquared() < 1e-9) return vel.clone();
        Vector n = normal.clone().normalize();
        return vel.clone().subtract(n.multiply(2.0 * vel.dot(n)));
    }

    /**
     * Calculates the new speed with acceleration applied. Clamped to at most 4.0.
     * Speed <= 0.05 means it ends (returns 0.0).
     */
    public static double nextSpeed(double speed, double acc) {
        double s = speed + acc;
        if (s <= 0.05) return 0.0;
        return Math.min(4.0, s);
    }

    /**
     * Spreads {@code dir} into {@code count} directions according to {@code pattern} (fan or random).
     */
    public static List<Vector> spread(Vector dir, int count, double degrees, String pattern, Random r) {
        if (count <= 0) return List.of();
        Vector base = dir.clone().normalize();
        if (count == 1) return List.of(base);

        List<Vector> out = new ArrayList<>(count);
        if ("random".equalsIgnoreCase(pattern)) {
            Vector arbitrary = Math.abs(base.getX()) < 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
            Vector u = base.clone().crossProduct(arbitrary).normalize();
            Vector v = base.clone().crossProduct(u).normalize();
            for (int i = 0; i < count; i++) {
                double theta = r.nextDouble() * Math.toRadians(degrees);
                double phi = r.nextDouble() * 2 * Math.PI;
                Vector coneDir = base.clone().multiply(Math.cos(theta))
                        .add(u.clone().multiply(Math.sin(theta) * Math.cos(phi)))
                        .add(v.clone().multiply(Math.sin(theta) * Math.sin(phi)));
                out.add(coneDir.normalize());
            }
            return out;
        }

        // Fan: symmetric around dir in the horizontal plane of the look
        for (int i = 0; i < count; i++) {
            double offsetDeg = (i - (count - 1) / 2.0) * degrees;
            Vector rotated;
            if (Math.abs(base.getY()) < 0.99) {
                rotated = base.clone().rotateAroundY(Math.toRadians(offsetDeg));
            } else {
                rotated = base.clone().rotateAroundZ(Math.toRadians(offsetDeg));
            }
            out.add(rotated.normalize());
        }
        return out;
    }
}
