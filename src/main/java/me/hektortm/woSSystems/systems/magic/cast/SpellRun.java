package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.magic.MagicRules;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.systems.magic.model.SpellNode;
import me.hektortm.woSSystems.utils.ActionHandler;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One cast of a spell: runs its node tree (magic spec §3.3). A shape starts where its
 * event happened and runs its outlets' nodes where its own events happen; effects act on
 * the context's target (or the caster) or at its point. Everything stops when the caster
 * leaves. One failing node is logged once and skipped; the rest of the spell runs.
 */
final class SpellRun {

    /**
     * Where a node runs: the point (and look direction), the target entity if the event has
     * one, where the caster and the target were when the event happened (teleports go
     * there, so two of them swap the pair in either order), and the seconds since the
     * shape that made the event started (particle shapes animate by it).
     */
    record Ctx(Location point, Vector dir, @Nullable LivingEntity target, Location casterAt, @Nullable Location targetAt, double age) {
        static Ctx of(Location point, Vector dir, @Nullable LivingEntity target, Player caster, double age) {
            return new Ctx(point, dir, target, caster.getLocation(), target == null ? null : target.getLocation(), age);
        }
    }

    /** A new event at {@code p} with target {@code t}, facing as {@code c} did. */
    private Ctx at(Ctx c, Location p, @Nullable LivingEntity t) {
        return Ctx.of(p, c.dir(), t, caster, 0);
    }

    /** Where a cast's hit and kill XP goes (spec §3.5); NONE for test and free casts. */
    interface XpSink {
        XpSink NONE = new XpSink() {
            public void hit() { }

            public void kill() { }
        };

        void hit();

        void kill();
    }

    private static final double STEP = 0.5;
    private static final double TRAIL_STEP = 0.25;
    private static final int MAX_TICKS = 20 * 60;

    private final WoSSystems plugin;
    private final Player caster;
    private final SpellDef spell;
    private final String wandColor;
    private final double damagePct;
    private final PvpGate pvp;
    private final SpellDeaths deaths;
    private final XpSink xp;
    private final FallGuard falls;
    /** Targets that gave hit / kill XP in this cast: each counts once. */
    private final Set<UUID> hitXp = new HashSet<>();
    private final Set<UUID> killXp = new HashSet<>();
    private int shapes;
    private int areas;
    private int animations;
    private boolean warned;
    /** Each particle shape node read once (its formulas compiled). */
    private final Map<SpellNode, ParticleShapes.Params> shapeParams = new IdentityHashMap<>();

    SpellRun(WoSSystems plugin, Player caster, SpellDef spell, String wandColor, double damagePct, PvpGate pvp, SpellDeaths deaths, XpSink xp,
             FallGuard falls) {
        this.xp = xp;
        this.falls = falls;
        this.plugin = plugin;
        this.caster = caster;
        this.spell = spell;
        this.wandColor = wandColor;
        this.damagePct = damagePct;
        this.pvp = pvp;
        this.deaths = deaths;
    }

    void run(List<SpellNode> nodes, Ctx c) {
        for (SpellNode n : nodes) {
            if (!caster.isOnline()) return;
            try {
                node(n, c);
            } catch (RuntimeException e) {
                if (!warned) {
                    warned = true;
                    plugin.getLogger().log(Level.WARNING, "[Magic] spell " + spell.id() + ": node " + n.type() + " failed", e);
                }
            }
        }
    }

    private void node(SpellNode n, Ctx c) {
        switch (n.type()) {
            case "projectile" -> projectile(n, c);
            case "beam" -> beam(n, c);
            case "area" -> area(n, c);
            case "self" -> run(n.children("then"), at(c, caster.getLocation().add(0, 1, 0), caster));
            case "wait" -> waitThen(n, c);
            case "at_level" -> run(n.children("then"), c); // parsed only into the tiers that reach its level
            default -> effect(n, c);
        }
    }

    /** Runs the wait's nodes later at the same point with the same target; where the caster and target are is read again then. */
    private void waitThen(SpellNode n, Ctx c) {
        long ticks = Math.max(1, Math.round(n.num("seconds") * 20));
        double later = c.age() + ticks / 20.0;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!caster.isOnline()) return;
            run(n.children("then"), Ctx.of(c.point(), c.dir(), c.target(), caster, later));
        }, ticks);
    }

    private String color(String value) {
        return "wand".equals(value) || value.isBlank() ? wandColor : value;
    }

    private boolean startShape(boolean area) {
        var limits = spell.catalog().limits();
        if (++shapes > limits.maxShapes()) return false;
        return !area || ++areas <= limits.maxAreas();
    }

    private static boolean isNpc(Entity e) {
        return e.hasMetadata("NPC");
    }

    private void hitEntity(SpellNode shape, LivingEntity e, Ctx c) {
        Location at = e.getLocation().add(0, e.getHeight() / 2, 0);
        Bukkit.getPluginManager().callEvent(new SpellHitEvent(caster, spell, e, null, at));
        run(shape.children("on_hit_entity"), at(c, at, e));
    }

    private void hitBlock(SpellNode shape, Block b, Location at, Ctx c) {
        Bukkit.getPluginManager().callEvent(new SpellHitEvent(caster, spell, null, b, at));
        run(shape.children("on_hit_block"), at(c, at, null));
    }

    // ── shapes ──

    private void projectile(SpellNode n, Ctx c) {
        if (!startShape(false)) return;
        double range = n.num("range");
        double size = n.num("size");
        int pierce = n.integer("pierce");
        boolean gravity = n.bool("gravity");
        SpellFx.Brush trail = trailBrush(n);
        String pattern = n.str("trail");
        double trailRadius = n.num("trail_radius");
        double trailTurns = n.num("trail_turns");
        List<SpellNode> flight = n.children("on_flight");
        SpellFx.sound(c.point(), n.str("sound"), 1f, 1f);
        Location at = c.point().clone();
        Vector vel = c.dir().clone().normalize().multiply(n.num("speed"));
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            double travelled;
            int ticks;
            int pierced;

            @Override
            public void run() {
                if (!caster.isOnline() || ++ticks > MAX_TICKS || at.getWorld() == null) {
                    cancel();
                    return;
                }
                int steps = Math.max(1, (int) Math.ceil(vel.length() / STEP));
                Vector step = vel.clone().multiply(1.0 / steps);
                for (int i = 0; i < steps; i++) {
                    Vector from = at.toVector();
                    double before = travelled;
                    at.add(step);
                    travelled += step.length();
                    if (travelled > range) {
                        cancel();
                        SpellRun.this.run(n.children("on_end"), at(c, at.clone(), null));
                        return;
                    }
                    Block b = at.getBlock();
                    if (!b.isPassable()) {
                        cancel();
                        hitBlock(n, b, at.clone().subtract(step.clone().multiply(0.5)), c);
                        return;
                    }
                    if (trail != null) drawTrail(trail, pattern, from, step, before, trailRadius, trailTurns, at.getWorld());
                    for (Entity e : at.getWorld().getNearbyEntities(at, size, size, size)) {
                        if (!(e instanceof LivingEntity le) || !hit.add(e.getUniqueId())) continue;
                        if (!HitRules.shapeHits(e == caster, isNpc(e), false)) continue;
                        hitEntity(n, le, c);
                        if (pierced++ >= pierce) {
                            cancel();
                            return;
                        }
                    }
                }
                if (!flight.isEmpty()) SpellRun.this.run(flight, Ctx.of(at.clone(), vel.clone(), null, caster, ticks / 20.0));
                if (gravity) vel.setY(vel.getY() - 0.05);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The brush of a shape's trail (its particle and colour), or null for none. */
    @Nullable
    private SpellFx.Brush trailBrush(SpellNode n) {
        String particle = n.str("particle");
        return particle.isBlank() ? null : SpellFx.brush(new SpellFx.Look(particle, color(n.str("color")), "", 1.1f, "", 0));
    }

    /**
     * The trail over one stretch of a path ({@code from}, moving {@code step}): one particle
     * at its end for a line, every {@link #TRAIL_STEP} blocks for the winding patterns.
     */
    private static void drawTrail(SpellFx.Brush brush, String pattern, Vector from, Vector step, double travelled,
                                  double radius, double turns, World w) {
        double length = step.length();
        int parts = "line".equals(pattern) ? 1 : Math.max(1, (int) Math.ceil(length / TRAIL_STEP));
        for (int j = 1; j <= parts; j++) {
            double f = (double) j / parts;
            Vector p = from.clone().add(step.clone().multiply(f));
            for (Vector v : ParticleShapes.trail(pattern, p, step, travelled + length * f, length / parts, radius, turns)) {
                brush.draw(v.toLocation(w), 0, 1, "line".equals(pattern) ? 0.02 : 0);
            }
        }
    }

    private void beam(SpellNode n, Ctx c) {
        if (!startShape(false)) return;
        World w = c.point().getWorld();
        if (w == null) return;
        double range = n.num("range");
        double width = n.num("width");
        Vector start = c.point().toVector();
        Vector dir = c.dir().clone().normalize();
        RayTraceResult block = w.rayTraceBlocks(c.point(), dir, range, FluidCollisionMode.NEVER, true);
        double length = block == null ? range : block.getHitPosition().distance(start);
        SpellFx.Brush brush = trailBrush(n);
        if (brush != null) {
            String pattern = n.str("trail");
            double step = "line".equals(pattern) ? 0.3 : TRAIL_STEP;
            for (double d = 0; d <= length; d += step) {
                Vector p = start.clone().add(dir.clone().multiply(d));
                for (Vector v : ParticleShapes.trail(pattern, p, dir, d, step, n.num("trail_radius"), n.num("trail_turns"))) {
                    brush.draw(v.toLocation(w), 0, 1, "line".equals(pattern) ? 0.01 : 0);
                }
            }
        }
        Vector end = start.clone().add(dir.clone().multiply(length));
        BoundingBox box = BoundingBox.of(start, end).expand(width + 2);
        List<LivingEntity> hits = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(box)) {
            if (!(e instanceof LivingEntity le) || !HitRules.shapeHits(e == caster, isNpc(e), false)) continue;
            if (e.getBoundingBox().clone().expand(width).rayTrace(start, dir, length) != null) hits.add(le);
        }
        hits.sort(Comparator.comparingDouble(e -> e.getLocation().toVector().distanceSquared(start)));
        for (LivingEntity e : hits) hitEntity(n, e, c);
        if (block != null && block.getHitBlock() != null) {
            hitBlock(n, block.getHitBlock(), block.getHitPosition().toLocation(w), c);
        } else {
            run(n.children("on_end"), at(c, end.toLocation(w), null));
        }
    }

    private void area(SpellNode n, Ctx c) {
        if (!startShape(true)) return;
        boolean followCaster = "caster".equals(n.str("center"));
        double radius = n.num("radius");
        double duration = n.num("duration");
        boolean hitsCaster = n.bool("hits_caster");
        String pattern = n.str("pattern");
        String particle = n.str("particle");
        String color = color(n.str("color"));
        int cap = spell.catalog().limits().areaEntitiesPerTick();
        Location fixed = c.point().clone();
        if (duration <= 0) {
            Location center = followCaster ? caster.getLocation() : fixed;
            draw(center, radius, pattern, particle, color);
            for (LivingEntity e : inside(center, radius, hitsCaster, cap)) run(n.children("on_enter"), at(c, center.clone(), e));
            run(n.children("on_end"), at(c, center.clone(), null));
            return;
        }
        int pulseTicks = Math.max(1, (int) Math.round(n.num("interval") * 20));
        int lifeTicks = (int) Math.round(duration * 20);
        Set<UUID> entered = new HashSet<>();
        new BukkitRunnable() {
            int age;

            @Override
            public void run() {
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }
                Location center = followCaster ? caster.getLocation() : fixed;
                if (age >= lifeTicks) {
                    cancel();
                    SpellRun.this.run(n.children("on_end"), at(c, center.clone(), null));
                    return;
                }
                if (age % 5 == 0) draw(center, radius, pattern, particle, color);
                if (age % pulseTicks == 0) {
                    List<LivingEntity> in = inside(center, radius, hitsCaster, cap);
                    for (LivingEntity e : in) {
                        if (entered.add(e.getUniqueId())) SpellRun.this.run(n.children("on_enter"), at(c, center.clone(), e));
                    }
                    for (LivingEntity e : in) SpellRun.this.run(n.children("on_tick"), at(c, center.clone(), e));
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private List<LivingEntity> inside(Location center, double radius, boolean hitsCaster, int cap) {
        List<LivingEntity> out = new ArrayList<>();
        if (center.getWorld() == null) return out;
        double r2 = radius * radius;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (out.size() >= cap) break;
            if (!(e instanceof LivingEntity le) || e.getLocation().distanceSquared(center) > r2) continue;
            if (HitRules.shapeHits(e == caster, isNpc(e), hitsCaster)) out.add(le);
        }
        return out;
    }

    private static void draw(Location center, double radius, String pattern, String particle, String color) {
        if (particle.isBlank() || "none".equals(pattern)) return;
        SpellFx.Brush brush = SpellFx.brush(new SpellFx.Look(particle, color, "", 1.1f, "", 0));
        if (brush == null) return;
        if ("filled".equals(pattern)) {
            int points = (int) Math.min(40, Math.max(6, radius * radius * 2));
            for (int i = 0; i < points; i++) {
                double a = Math.random() * Math.PI * 2, d = Math.sqrt(Math.random()) * radius;
                brush.draw(center.clone().add(Math.cos(a) * d, 0.1, Math.sin(a) * d), 0, 1, 0);
            }
            return;
        }
        ParticleShapes.Params shape = switch (pattern) {
            case "sphere" -> ParticleShapes.Params.plain("sphere", (int) Math.min(120, Math.max(20, radius * radius * 8)), radius, 0, 0);
            case "dome" -> ParticleShapes.Params.plain("dome", (int) Math.min(80, Math.max(12, radius * radius * 5)), radius, 0, 0);
            case "spiral" -> ParticleShapes.Params.plain("spiral", (int) Math.min(60, Math.max(12, radius * 12)), radius, 0, 3);
            default -> ParticleShapes.Params.plain("ring", (int) Math.min(48, Math.max(12, radius * 8)), radius, 0, 0);
        };
        for (double[] q : ParticleShapes.draw(shape, 0, 1)) brush.draw(center.clone().add(q[0], q[1] + 0.1, q[2]), 0, 1, 0);
    }

    // ── effects ──

    @Nullable
    private LivingEntity target(SpellNode n, Ctx c) {
        String affects = n.str("affects");
        if ("caster".equals(affects)) return caster;
        LivingEntity t = c.target();
        if (t == null || !t.isValid() || !HitRules.affects(affects, t instanceof Player)) return null;
        return t;
    }

    private boolean allowed(String effect, LivingEntity t) {
        boolean isCaster = t == caster;
        boolean isPlayer = t instanceof Player;
        boolean pvpAllows = isPlayer && !isCaster && pvp.mayHurt(caster, (Player) t);
        return HitRules.mayAffect(effect, isCaster, isPlayer, pvpAllows);
    }

    private void effect(SpellNode n, Ctx c) {
        String type = n.type();
        LivingEntity t = null;
        if (n.isEntityEffect()) {
            t = target(n, c);
            if (t == null || !allowed(type, t)) return;
            creditHit(t);
        }
        switch (type) {
            case "damage" -> {
                deaths.remember(t, caster, spell.name());
                t.damage(MagicRules.damage(n.num("amount"), damagePct),
                        DamageSource.builder(DamageType.MAGIC).withCausingEntity(caster).withDirectEntity(caster).build());
                if (t.isDead() && HitRules.givesXp(t == caster, spell.casterOnly()) && killXp.add(t.getUniqueId())) xp.kill();
            }
            case "heal" -> {
                AttributeInstance max = t.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20 : max.getValue();
                t.setHealth(Math.min(cap, t.getHealth() + n.num("amount")));
            }
            case "push" -> push(n, c, t);
            case "turn" -> turn(n, c, t);
            case "potion" -> {
                NamespacedKey key = SpellFx.key(n.str("effect"));
                PotionEffectType pt = key == null ? null : Registry.EFFECT.get(key);
                if (pt != null) t.addPotionEffect(new PotionEffect(pt, (int) Math.round(n.num("seconds") * 20), Math.max(0, n.integer("level") - 1)));
            }
            case "fire" -> t.setFireTicks(Math.max(t.getFireTicks(), (int) Math.round(n.num("seconds") * 20)));
            case "teleport" -> teleport(n, c);
            case "particles" -> {
                SpellFx.Brush brush = SpellFx.brush(look(n));
                if (brush != null) brush.draw(c.point(), 0, n.integer("count"), n.num("spread"));
            }
            case "particle_shape" -> particleShape(n, c);
            case "sound" -> SpellFx.sound(c.point(), n.str("sound"), (float) n.num("volume"), (float) n.num("pitch"));
            case "run_actions" -> runActions(n, c);
            default -> { }
        }
    }

    /** Hit XP for an entity an effect acts on: once per cast, never the caster unless the spell only acts on the caster. */
    private void creditHit(LivingEntity t) {
        if (HitRules.givesXp(t == caster, spell.casterOnly()) && hitXp.add(t.getUniqueId())) xp.hit();
    }

    /**
     * Push / pull (spec §3.3): through the target's motion, capped at max_velocity. A mob's
     * knockback resistance counts; anything riding is not moved.
     */
    private void push(SpellNode n, Ctx c, LivingEntity t) {
        if (t.isInsideVehicle()) return;
        Vector fixed = new Vector(n.num("x"), n.num("y"), n.num("z"));
        Vector v = Movement.push(n.str("direction"), caster.getLocation().toVector(), t.getLocation().toVector(), c.point().toVector(),
                caster.getEyeLocation().getDirection(), t.getEyeLocation().getDirection(), fixed, n.num("strength"), n.num("lift"));
        if (!(t instanceof Player)) {
            AttributeInstance resist = t.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            double keep = resist == null ? 1 : Math.max(0, 1 - resist.getValue());
            v.setX(v.getX() * keep).setZ(v.getZ() * keep);
        }
        Vector next = n.bool("replace") ? v : t.getVelocity().add(v);
        t.setVelocity(Movement.cap(next, spell.catalog().limits().maxVelocity()));
        double noFall = n.num("no_fall");
        if (noFall > 0) falls.protect(t, noFall);
    }

    /** Turn (spec §3.3): where the target looks, kept in place with its motion. */
    private void turn(SpellNode n, Ctx c, LivingEntity t) {
        Location eye = t.getEyeLocation();
        Vector at = Movement.lookPoint(n.str("to"), eye.toVector(), eye.getDirection(), c.point().toVector(),
                caster.getEyeLocation().toVector(), n.num("yaw"), n.num("pitch"));
        t.lookAt(at.getX(), at.getY(), at.getZ(), io.papermc.paper.entity.LookAnchor.EYES);
    }

    private void teleport(SpellNode n, Ctx c) {
        LivingEntity who = "target".equals(n.str("who")) ? c.target() : caster;
        if (who == null || !allowed("teleport", who)) return;
        if (who != caster) creditHit(who);
        // Where the caster / target were when the event happened, not where they are now:
        // an earlier teleport of this event must not move the destination (a swap).
        Location to;
        switch (n.str("to")) {
            case "caster" -> to = c.casterAt().clone();
            case "target" -> {
                if (c.targetAt() == null) return;
                to = c.targetAt().clone();
            }
            default -> to = c.point().clone();
        }
        to.setYaw(who.getLocation().getYaw());
        to.setPitch(who.getLocation().getPitch());
        who.teleport(to);
    }

    /** The look of a particles / particle_shape node; "wand" colours become the caster's. */
    private SpellFx.Look look(SpellNode n) {
        String second = n.str("color2");
        return new SpellFx.Look(n.str("particle"), color(n.str("color")), second.isBlank() ? "" : color(second),
                (float) n.num("size"), n.str("material"), n.num("speed"));
    }

    /**
     * A particle shape at the event's point: drawn once, or (with a duration) redrawn every
     * interval while it animates. At most {@code max_animations} of a cast run at once.
     */
    private void particleShape(SpellNode n, Ctx c) {
        SpellFx.Brush brush = SpellFx.brush(look(n));
        if (brush == null || c.point().getWorld() == null) return;
        ParticleShapes.Params params = shapeParams.get(n);
        if (params == null) {
            params = ParticleShapes.Params.of(n);
            shapeParams.put(n, params);
        }
        Vector[] frame = ParticleShapes.frame(c.dir(), "look".equals(n.str("facing")));
        Vector origin = c.point().toVector().add(ParticleShapes.horizontal(c.dir()).multiply(n.num("forward"))).add(new Vector(0, n.num("up"), 0));
        double jitter = n.num("jitter");
        double duration = n.num("duration");
        ParticleShapes.Params shape = params;
        World w = c.point().getWorld();
        if (duration <= 0) {
            drawShape(shape, brush, w, origin, frame, jitter, c.age(), 1);
            return;
        }
        if (animations >= spell.catalog().limits().maxAnimations()) return;
        animations++;
        int every = Math.max(1, (int) Math.round(n.num("interval") * 20));
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                double elapsed = ticks / 20.0;
                if (!caster.isOnline() || elapsed > duration) {
                    cancel();
                    animations--;
                    return;
                }
                drawShape(shape, brush, w, origin, frame, jitter, c.age() + elapsed, Math.min(1, elapsed / duration));
                ticks += every;
            }
        }.runTaskTimer(plugin, 0L, every);
    }

    private static void drawShape(ParticleShapes.Params shape, SpellFx.Brush brush, World w, Vector origin, Vector[] frame,
                                  double jitter, double t, double life) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (double[] q : ParticleShapes.draw(shape, t, life)) {
            Vector at = ParticleShapes.place(origin, frame, q[0], q[1], q[2]);
            if (jitter > 0) at.add(new Vector(rnd.nextDouble(-jitter, jitter), rnd.nextDouble(-jitter, jitter), rnd.nextDouble(-jitter, jitter)));
            brush.draw(at.toLocation(w), q[3], 1, 0);
        }
    }

    private void runActions(SpellNode n, Ctx c) {
        Location p = c.point();
        String target = c.target() == null ? "" : c.target().getName();
        List<String> lines = new ArrayList<>();
        for (String line : n.strings("commands")) {
            lines.add(line.replace("{caster}", caster.getName()).replace("{target}", target)
                    .replace("{x}", fmt(p.getX())).replace("{y}", fmt(p.getY())).replace("{z}", fmt(p.getZ()))
                    .replace("{world}", p.getWorld() == null ? "" : p.getWorld().getName()));
        }
        plugin.getActionHandler().executeActions(caster, lines, ActionHandler.SourceType.SPELL, spell.id(), null);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
