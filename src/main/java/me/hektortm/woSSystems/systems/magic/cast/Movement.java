package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;

/** Push / pull and turn (magic spec §3.3), without the server. */
public final class Movement {

    /** Blocks per tick of motion per point of strength. */
    static final double PER_STRENGTH = 0.6;

    private Movement() { }

    /**
     * The motion a push gives: {@code strength} along the direction, plus {@code lift} up.
     * Away from / toward the caster or the point are flat (the lift raises them); the look
     * directions and the vector point anywhere. A direction of no length (pushing yourself
     * away from yourself) falls back to the caster's look, then south.
     */
    public static Vector push(String direction, Vector casterAt, Vector targetAt, Vector point, Vector casterLook,
                              Vector targetLook, Vector fixed, double strength, double lift) {
        Vector dir = switch (direction) {
            case "toward_caster" -> casterAt.clone().subtract(targetAt).setY(0);
            case "away_point" -> targetAt.clone().subtract(point).setY(0);
            case "toward_point" -> point.clone().subtract(targetAt).setY(0);
            case "caster_look" -> casterLook.clone();
            case "target_look" -> targetLook.clone();
            case "up" -> new Vector(0, 1, 0);
            case "vector" -> fixed.clone();
            default -> targetAt.clone().subtract(casterAt).setY(0); // away_caster
        };
        if (dir.lengthSquared() < 1e-6) dir = casterLook.clone();
        if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
        return dir.normalize().multiply(strength * PER_STRENGTH).add(new Vector(0, lift, 0));
    }

    /** {@code v} no faster than {@code max} blocks per tick. */
    public static Vector cap(Vector v, double max) {
        double length = v.length();
        return length > max ? v.clone().multiply(max / length) : v;
    }

    /**
     * The point a turned entity looks at: the event's point, the caster's eyes, straight
     * behind it ({@code around}), or the direction of {@code yaw} / {@code pitch} ({@code set};
     * Minecraft's angles: yaw 0 south, 90 west; pitch −90 up).
     */
    public static Vector lookPoint(String to, Vector eye, Vector look, Vector point, Vector casterEye, double yaw, double pitch) {
        return switch (to) {
            case "caster" -> casterEye.clone();
            case "around" -> eye.clone().subtract(look.clone().normalize());
            case "set" -> {
                double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                yield eye.clone().add(new Vector(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)));
            }
            default -> point.clone();
        };
    }
}
