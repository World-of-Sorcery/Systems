package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;

/**
 * Reads where a caster aims: a ray trace from their eyes along their look direction,
 * stopping at the first block or living entity (ignoring caster and NPCs).
 */
public final class Aim {

    public record Hit(Location point, @Nullable LivingEntity entity) { }

    private Aim() { }

    public static Hit of(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) return new Hit(eye.clone(), null);
        Vector dir = eye.getDirection().normalize();
        RayTraceResult result = world.rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.2,
                e -> e instanceof LivingEntity && e != caster && !e.hasMetadata("NPC"));
        if (result != null) {
            Location point = result.getHitPosition().toLocation(world);
            Entity hitEntity = result.getHitEntity();
            LivingEntity target = (hitEntity instanceof LivingEntity le && hitEntity != caster && !hitEntity.hasMetadata("NPC")) ? le : null;
            return new Hit(point, target);
        }
        return new Hit(eye.clone().add(dir.multiply(range)), null);
    }
}
