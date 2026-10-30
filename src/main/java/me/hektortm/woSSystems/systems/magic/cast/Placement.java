package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The pure maths of the flow nodes: Move, Random and Check (more-nodes spec §4.2). */
final class Placement {

    private Placement() {}

    /** Where {@code point} ends up after moving along the flat facing; right is the facing turned clockwise seen from above. */
    static Vector offset(Vector point, Vector facing, double forward, double up, double right) {
        double fx = facing.getX();
        double fz = facing.getZ();
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1.0E-6) { // looking straight up or down: no flat direction, use south
            fx = 0;
            fz = 1;
        } else {
            fx /= len;
            fz /= len;
        }
        return point.clone().add(new Vector(fx * forward - fz * right, up, fz * forward + fx * right));
    }

    /** The facing a Move node leaves behind; a mode that needs a missing target keeps the facing. */
    static Vector facing(String mode, Vector keep, Vector casterLook, Vector point, Vector caster,
                         @Nullable Vector target, Random r) {
        return switch (mode) {
            case "caster_look" -> casterLook.clone();
            case "up" -> new Vector(0, 1, 0);
            case "down" -> new Vector(0, -1, 0);
            case "toward_caster" -> unitOr(caster.clone().subtract(point), keep);
            case "away_caster" -> unitOr(point.clone().subtract(caster), keep);
            case "toward_target" -> target == null ? keep.clone() : unitOr(target.clone().subtract(point), keep);
            case "random" -> randomUnit(r);
            default -> keep.clone();
        };
    }

    /** {@code pick} distinct indices out of {@code size}, all of them when {@code pick} is not smaller. */
    static int[] pick(int pick, int size, Random r) {
        List<Integer> all = new ArrayList<>(size);
        for (int i = 0; i < size; i++) all.add(i);
        Collections.shuffle(all, r);
        return all.subList(0, Math.min(pick, size)).stream().mapToInt(Integer::intValue).toArray();
    }

    /** {@code below} / {@code above}: whether {@code value} is on that side of {@code limit}. */
    static boolean compare(String compare, double value, double limit) {
        return "above".equals(compare) ? value > limit : value < limit;
    }

    private static Vector unitOr(Vector v, Vector fallback) {
        return v.lengthSquared() < 1.0E-12 ? fallback.clone() : v.normalize();
    }

    private static Vector randomUnit(Random r) {
        double y = r.nextDouble() * 2 - 1;
        double a = r.nextDouble() * Math.PI * 2;
        double flat = Math.sqrt(1 - y * y);
        return new Vector(flat * Math.cos(a), y, flat * Math.sin(a));
    }
}
