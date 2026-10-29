package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.WandRules;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;

/** Particles and sounds of spells, by the ids staff type in the portal. */
public final class SpellFx {

    private SpellFx() { }

    @Nullable
    static NamespacedKey key(String id) {
        if (id == null || id.isBlank()) return null;
        String s = id.toLowerCase(Locale.ROOT).trim();
        return NamespacedKey.fromString(s.contains(":") ? s : "minecraft:" + s);
    }

    /**
     * Particles at a point. Coloured kinds (dust …) take {@code colorHex}; a particle that
     * needs other data (a block, an item) is not drawn rather than throw.
     */
    public static void particle(Location at, String id, String colorHex, int count, double spread) {
        Brush b = brush(new Look(id, colorHex, "", 1.1f, "", 0));
        if (b != null) b.draw(at, 0, count, spread);
    }

    /**
     * How particles look: the id, a colour (#RRGGBB; "wand" is resolved by the caller), a
     * second colour ("" for none), the dust size, a block / item id for particles showing
     * one, and the drift speed.
     */
    public record Look(String id, String color, String color2, float size, String material, double speed) { }

    /** A {@link Look} resolved once, drawn at many points. */
    public static final class Brush {
        private final Particle particle;
        private final Class<?> type;
        private final Color from;
        @Nullable private final Color to;
        private final float size;
        @Nullable private final Object fixed;
        private final double speed;

        private Brush(Particle particle, Class<?> type, Color from, @Nullable Color to, float size, @Nullable Object fixed, double speed) {
            this.particle = particle;
            this.type = type;
            this.from = from;
            this.to = to;
            this.size = size;
            this.fixed = fixed;
            this.speed = speed;
        }

        /** Draws at {@code at}; {@code along} (0 to 1) is how far the colour has faded to the second one. */
        public void draw(Location at, double along, int count, double spread) {
            if (at.getWorld() == null) return;
            Object data = fixed;
            if (type == Particle.DustOptions.class) {
                data = new Particle.DustOptions(mix(along), size);
            } else if (type == Particle.DustTransition.class) {
                data = new Particle.DustTransition(from, to == null ? from : to, size);
            } else if (type == Color.class) {
                data = mix(along);
            }
            at.getWorld().spawnParticle(particle, at, Math.max(1, count), spread, spread, spread, speed, data);
        }

        private Color mix(double along) {
            if (to == null || along <= 0) return from;
            double f = Math.min(1, along);
            return Color.fromRGB((int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * f),
                    (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * f),
                    (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * f));
        }
    }

    /** The brush of a look, or null when the particle is unknown or needs data the look can't give. */
    @Nullable
    public static Brush brush(Look look) {
        NamespacedKey key = key(look.id());
        Particle p = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (p == null) return null;
        Class<?> type = p.getDataType();
        Color from = Color.fromRGB(WandRules.rgb(look.color()));
        Color to = look.color2() == null || look.color2().isBlank() ? null : Color.fromRGB(WandRules.rgb(look.color2()));
        Object fixed = null;
        if (type == BlockData.class || type == ItemStack.class) {
            NamespacedKey m = key(look.material());
            Material mat = m == null ? null : Material.matchMaterial(m.toString());
            if (mat == null) return null;
            if (type == BlockData.class) {
                if (!mat.isBlock()) return null;
                fixed = mat.createBlockData();
            } else {
                if (!mat.isItem()) return null;
                fixed = new ItemStack(mat);
            }
        } else if (type == Float.class) {
            fixed = 0f;
        } else if (type == Integer.class) {
            fixed = 0;
        } else if (type != Void.class && type != Particle.DustOptions.class && type != Particle.DustTransition.class && type != Color.class) {
            return null;
        }
        return new Brush(p, type, from, to, look.size(), fixed, look.speed());
    }

    public static void sound(Location at, String id, float volume, float pitch) {
        NamespacedKey key = key(id);
        if (key == null || at.getWorld() == null) return;
        at.getWorld().playSound(at, key.toString(), volume, pitch);
    }
}
