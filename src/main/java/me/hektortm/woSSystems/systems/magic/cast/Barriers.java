package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.World;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Areas and orbits with {@code stops_spells} (more-nodes spec §4.3): every bolt asks on
 * each step whether a barrier of another caster holds its position, so even a fast bolt
 * can't skip a 1-block wall between two ticks. Main thread only.
 */
public final class Barriers {

    /** One stopping zone; {@code inside} may read moving state (an orbit's orbs). */
    public final class Barrier {
        @Nullable
        private final World world;
        private final UUID owner;
        private final Predicate<Vector> inside;

        private Barrier(@Nullable World world, UUID owner, Predicate<Vector> inside) {
            this.world = world;
            this.owner = owner;
            this.inside = inside;
        }

        public void remove() {
            barriers.remove(this);
        }
    }

    private final List<Barrier> barriers = new ArrayList<>();

    public Barrier add(@Nullable World w, UUID owner, Predicate<Vector> inside) {
        Barrier b = new Barrier(w, owner, inside);
        barriers.add(b);
        return b;
    }

    /** Whether a bolt of {@code owner} at {@code p} in {@code w} is stopped by someone else's barrier. */
    public boolean stops(@Nullable World w, UUID owner, Vector p) {
        for (Barrier b : barriers) {
            if (b.world == w && !b.owner.equals(owner) && b.inside.test(p)) return true;
        }
        return false;
    }
}
