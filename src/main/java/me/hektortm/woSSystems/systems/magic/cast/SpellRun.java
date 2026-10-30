package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicRules;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.systems.magic.model.SpellNode;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.wosCore.pvp.PvpCheck;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

    /**
     * What a cast needs from the rest of magic: the click router, the litter service, the
     * barriers of stops_spells, the timed effects (freeze, shield …), the looks-only effects, the spell locks, the tier of a published
     * spell for this caster's level (null: no such spell) for Cast spell, and a player's magic
     * (null: none) for mana.
     */
    record Services(ClickRouter clicks, SpellLitter litter, Barriers barriers, TimedStates states, SpellVisuals visuals, SpellLocks locks,
                    Function<String, SpellDef> spells, Function<Player, MagicData> magic) { }

    private static final double STEP = 0.5;
    private static final double TRAIL_STEP = 0.25;
    private static final int MAX_TICKS = 20 * 60;
    /** Half the opening of the cone a {@code home_on: nearest} bolt looks in. */
    private static final double HOME_CONE_DEGREES = 45;

    private final WoSSystems plugin;
    private final Player caster;
    private final SpellDef spell;
    /** The spell the player cast: hit events and death messages name it, also inside Cast spell. */
    private final SpellDef reported;
    private final String wandColor;
    private final double damagePct;
    private final PvpCheck pvp;
    private final SpellDeaths deaths;
    private final XpSink xp;
    private final FallGuard falls;
    private final Services services;
    private final CastBudget budget;
    /** How many Cast spell nodes deep this run is (0: the spell the player cast). */
    private final int depth;
    /** Targets that gave hit / kill XP in this cast: each counts once. */
    private final Set<UUID> hitXp;
    private final Set<UUID> killXp;
    /** The spell locks that reacted in this cast (their keys): each reacts once. */
    private final Set<String> lockHits;
    private int shapes;
    private int areas;
    private int animations;
    private boolean warned;
    /** Each particle shape node read once (its formulas compiled). */
    private final Map<SpellNode, ParticleShapes.Params> shapeParams = new IdentityHashMap<>();

    SpellRun(WoSSystems plugin, Player caster, SpellDef spell, String wandColor, double damagePct, PvpCheck pvp, SpellDeaths deaths, XpSink xp,
             FallGuard falls, Services services) {
        this.xp = xp;
        this.falls = falls;
        this.plugin = plugin;
        this.caster = caster;
        this.spell = spell;
        this.reported = spell;
        this.wandColor = wandColor;
        this.damagePct = damagePct;
        this.pvp = pvp;
        this.deaths = deaths;
        this.services = services;
        this.budget = new CastBudget(spell.catalog().limits());
        this.depth = 0;
        this.hitXp = new HashSet<>();
        this.killXp = new HashSet<>();
        this.lockHits = new HashSet<>();
    }

    /** A spell cast by a Cast spell node of {@code outer}: same caster, budget and hit events; no XP. */
    private SpellRun(SpellRun outer, SpellDef spell) {
        this.plugin = outer.plugin;
        this.caster = outer.caster;
        this.spell = spell;
        this.reported = outer.reported;
        this.wandColor = outer.wandColor;
        this.damagePct = outer.damagePct;
        this.pvp = outer.pvp;
        this.deaths = outer.deaths;
        this.xp = XpSink.NONE;
        this.falls = outer.falls;
        this.services = outer.services;
        this.budget = outer.budget;
        this.depth = outer.depth + 1;
        this.hitXp = outer.hitXp;
        this.killXp = outer.killXp;
        this.lockHits = outer.lockHits;
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
        if (!budget.run()) {
            overBudget("node runs");
            return;
        }
        switch (n.type()) {
            case "projectile" -> projectile(n, c);
            case "beam" -> beam(n, c);
            case "area" -> area(n, c);
            case "cone" -> cone(n, c);
            case "chain" -> chain(n, c);
            case "orbit" -> orbit(n, c);
            case "self" -> run(n.children("then"), at(c, caster.getLocation().add(0, 1, 0), caster));
            case "wait" -> waitThen(n, c);
            case "at_level" -> run(n.children("then"), c); // parsed only into the tiers that reach its level
            case "await_click" -> awaitClick(n, c);
            case "repeat" -> repeat(n, c);
            case "chance" -> run(n.children(ThreadLocalRandom.current().nextDouble() * 100 < n.num("percent") ? "then" : "else"), c);
            case "random" -> {
                List<SpellNode> then = n.children("then");
                for (int i : Placement.pick(n.integer("pick"), then.size(), ThreadLocalRandom.current())) run(List.of(then.get(i)), c);
            }
            case "check" -> run(n.children(check(n, c) ? "pass" : "fail"), c);
            case "move_point" -> movePoint(n, c);
            case "cast_spell" -> castSpell(n, c);
            default -> effect(n, c);
        }
    }

    /** Logs once per cast that it ran out of something (more-nodes spec §3.4). */
    private void overBudget(String what) {
        if (budget.firstOver()) plugin.getLogger().warning("[Magic] spell " + reported.id() + " cast by " + caster.getName() + ": over its " + what + " budget, the rest is skipped");
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

    /**
     * A spell lock the shape touched reacts (spec §9, once per cast). Shapes never hit NPCs,
     * so an NPC is only touched; blocks a bolt or beam passes through are touched too.
     */
    private void touch(@Nullable SpellLocks.Lock lock) {
        services.locks().hit(lock, caster, reported, lockHits);
    }

    /** The NPC's spell lock reacts, if it has one. */
    private void touchNpc(Entity e) {
        if (isNpc(e)) touch(services.locks().of(e));
    }

    /** The locks inside an area: bound blocks and placed items (by their centre) and NPCs (by their feet). */
    private void touchLocksIn(Location center, Predicate<Vector> in, double reach) {
        for (SpellLocks.Lock lock : services.locks().near(center, reach)) {
            if (in.test(lock.where().clone().add(0.5, 0.5, 0.5).toVector())) touch(lock);
        }
        if (center.getWorld() == null) return;
        for (Entity e : center.getWorld().getNearbyEntities(center, reach, reach, reach)) {
            if (isNpc(e) && in.test(e.getLocation().toVector())) touchNpc(e);
        }
    }

    private void hitEntity(SpellNode shape, LivingEntity e, Ctx c) {
        Location at = e.getLocation().add(0, e.getHeight() / 2, 0);
        Bukkit.getPluginManager().callEvent(new SpellHitEvent(caster, reported, e, null, at));
        run(shape.children("on_hit_entity"), at(c, at, e));
    }

    private void hitBlock(SpellNode shape, Block b, Location at, Ctx c) {
        Bukkit.getPluginManager().callEvent(new SpellHitEvent(caster, reported, null, b, at));
        touch(services.locks().at(b));
        run(shape.children("on_hit_block"), at(c, at, null));
    }

    // ── shapes ──

    /**
     * Projectile (spec §3.3, more-nodes §4.1): {@code count} bolts spread around the look,
     * each a shape of its own. With {@code steer: redirect} one click turns all of them.
     */
    private void projectile(SpellNode n, Ctx c) {
        int count = Math.max(1, n.integer("count"));
        List<Bolt> launched = new ArrayList<>();
        for (Vector dir : Steering.spread(c.dir(), count, n.num("spread"), n.str("spread_pattern"), ThreadLocalRandom.current())) {
            if (!startShape(false)) break;
            launched.add(launch(n, c, dir));
        }
        if (launched.isEmpty()) return;
        SpellFx.sound(c.point(), n.str("sound"), 1f, 1f);
        if ("redirect".equals(n.str("steer"))) redirectOnClick(n, launched);
    }

    /**
     * One click listener for the bolts of one projectile node: a click turns each that still
     * obeys toward the aim point (at once, or by turn_rate each tick) and runs on_redirect there.
     */
    private void redirectOnClick(SpellNode n, List<Bolt> bolts) {
        double aimRange = n.num("aim_range");
        boolean atOnce = n.num("turn_rate") <= 0;
        services.clicks().add(caster.getUniqueId(), new ClickRouter.Listener() {
            public boolean live() { return caster.isOnline() && bolts.stream().anyMatch(Bolt::canRedirect); }

            public boolean click() {
                Vector aim = Aim.of(caster, aimRange).point().toVector();
                for (Bolt b : bolts) {
                    if (!b.canRedirect()) continue;
                    Vector toward = aim.clone().subtract(b.at().toVector());
                    if (atOnce) {
                        b.redirect(toward);
                    } else {
                        b.consumeRedirect();
                        b.aimAt(aim);
                    }
                    SpellRun.this.run(n.children("on_redirect"), Ctx.of(b.at().clone(), toward.lengthSquared() < 1.0E-9 ? b.vel().clone() : toward,
                            null, caster, b.ticks() / 20.0));
                }
                return bolts.stream().noneMatch(Bolt::canRedirect);
            }
        });
    }

    /** One bolt flying from the context's point along {@code dir}. */
    private Bolt launch(SpellNode n, Ctx c, Vector dir) {
        double range = n.num("range");
        double size = n.num("size");
        int pierce = n.integer("pierce");
        boolean gravity = n.bool("gravity");
        boolean comesBack = n.bool("return");
        boolean throughBlocks = n.bool("through_blocks");
        double acceleration = n.num("acceleration");
        SpellFx.Brush trail = trailBrush(n);
        String pattern = n.str("trail");
        double trailRadius = n.num("trail_radius");
        double trailTurns = n.num("trail_turns");
        List<SpellNode> flight = n.children("on_flight");
        String steer = n.str("steer");
        double turnRate = n.num("turn_rate");
        double aimRange = n.num("aim_range");
        Vector vel = dir.clone().normalize().multiply(n.num("speed"));
        Bolt bolt = new Bolt(c.point(), vel, "redirect".equals(steer) ? n.integer("redirects") : 0, n.integer("bounces"));
        LivingEntity homing = "home".equals(steer) ? homeTarget(n.str("home_on"), bolt.at(), vel, range) : null;
        Barriers barriers = services.barriers();
        UUID owner = caster.getUniqueId();
        new BukkitRunnable() {
            /** Stops the bolt without running anything. */
            private void stop() {
                bolt.end();
                cancel();
            }

            /** Stops the bolt and runs on_end where it is. */
            private void ends() {
                stop();
                SpellRun.this.run(n.children("on_end"), at(c, bolt.at().clone(), null));
            }

            /** Where a returning bolt flies to: the caster's middle. */
            private Vector home() {
                return caster.getLocation().add(0, caster.getHeight() / 2, 0).toVector();
            }

            @Override
            public void run() {
                World w = bolt.at().getWorld();
                if (!caster.isOnline() || bolt.incTicks() > MAX_TICKS || w == null) {
                    stop();
                    return;
                }
                if (bolt.returning() && caster.getWorld() != w) {
                    ends();
                    return;
                }
                if (acceleration != 0 && bolt.vel().lengthSquared() > 1.0E-12) {
                    double speed = Steering.nextSpeed(bolt.vel().length(), acceleration);
                    if (speed <= 0) {
                        ends();
                        return;
                    }
                    bolt.setVel(bolt.vel().clone().normalize().multiply(speed));
                }
                Vector desired = bolt.returning() ? home().subtract(bolt.at().toVector()) : switch (steer) {
                    case "redirect" -> bolt.aimAt() == null ? null : bolt.aimAt().clone().subtract(bolt.at().toVector());
                    case "follow" -> Aim.of(caster, aimRange).point().toVector().subtract(bolt.at().toVector());
                    case "home" -> homing == null || !homing.isValid() || homing.getWorld() != w ? null
                            : homing.getLocation().add(0, homing.getHeight() / 2, 0).toVector().subtract(bolt.at().toVector());
                    default -> null;
                };
                if (desired != null) {
                    bolt.setVel(Steering.turnToward(bolt.vel(), desired, bolt.returning() ? 0 : turnRate));
                    if (bolt.aimAt() != null && bolt.vel().angle(desired) < 0.01) bolt.aimAt(null); // faces it now
                }
                int steps = Math.max(1, (int) Math.ceil(bolt.vel().length() / STEP));
                Vector step = bolt.vel().clone().multiply(1.0 / steps);
                for (int i = 0; i < steps; i++) {
                    Vector from = bolt.at().toVector();
                    double before = bolt.travelled();
                    bolt.at().add(step);
                    bolt.addTravelled(step.length());
                    if (barriers.stops(w, owner, bolt.at().toVector())) { // another caster's barrier: a fizzle, nothing runs
                        SpellFx.sound(bolt.at(), "minecraft:block.fire.extinguish", 0.6f, 1.4f);
                        SpellFx.particle(bolt.at(), "minecraft:smoke", "", 6, 0.1);
                        stop();
                        return;
                    }
                    if (bolt.returning() && bolt.at().toVector().distanceSquared(home()) <= 1) {
                        ends();
                        return;
                    }
                    if (bolt.travelled() > range) {
                        if (comesBack && !bolt.returning()) {
                            bolt.startReturn(); // turns back next tick
                            break;
                        }
                        ends();
                        return;
                    }
                    Block b = bolt.at().getBlock();
                    if (!throughBlocks && !b.isPassable()) {
                        Location hitAt = bolt.at().clone().subtract(step.clone().multiply(0.5));
                        if (bolt.bounce()) {
                            touch(services.locks().at(b));
                            bolt.at().setX(from.getX());
                            bolt.at().setY(from.getY());
                            bolt.at().setZ(from.getZ());
                            bolt.setVel(Steering.reflect(bolt.vel(), faceNormal(w, from, step)));
                            SpellRun.this.run(n.children("on_bounce"), Ctx.of(hitAt, bolt.vel().clone(), null, caster, bolt.ticks() / 20.0));
                            break;
                        }
                        stop();
                        hitBlock(n, b, hitAt, c);
                        return;
                    }
                    if (!b.getType().isAir()) touch(services.locks().at(b)); // a button, a torch …: it flies on
                    if (trail != null) drawTrail(trail, pattern, from, step, before, trailRadius, trailTurns, w);
                    for (Entity e : w.getNearbyEntities(bolt.at(), size, size, size)) {
                        if (!(e instanceof LivingEntity le) || !bolt.hit().add(e.getUniqueId())) continue;
                        touchNpc(e);
                        if (!HitRules.shapeHits(e == caster, isNpc(e), false)) continue;
                        hitEntity(n, le, c);
                        if (bolt.incPierced() > pierce) {
                            stop();
                            return;
                        }
                    }
                }
                if (!flight.isEmpty()) SpellRun.this.run(flight, Ctx.of(bolt.at().clone(), bolt.vel().clone(), null, caster, bolt.ticks() / 20.0));
                if (gravity) bolt.vel().setY(bolt.vel().getY() - 0.05);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return bolt;
    }

    /** The outward face of the block a step from {@code from} ran into; straight back when it can't be read. */
    private static Vector faceNormal(World w, Vector from, Vector step) {
        RayTraceResult r = w.rayTraceBlocks(from.toLocation(w), step, step.length() + 0.5, FluidCollisionMode.NEVER, true);
        BlockFace face = r == null ? null : r.getHitBlockFace();
        return face == null ? step.clone().multiply(-1) : face.getDirection();
    }

    /** Whether a bolt may lock onto (or a chain jump to) {@code e}: never the caster, an NPC, or a player the caster may not hurt. */
    private boolean mayLockOn(LivingEntity e) {
        if (e == caster || isNpc(e) || e.isDead()) return false;
        return !(e instanceof Player p) || pvp.mayHurt(caster, p);
    }

    /** The entity a homing bolt locks onto at launch, or null (it flies straight). */
    @Nullable
    private LivingEntity homeTarget(String on, Location from, Vector dir, double range) {
        if ("aimed".equals(on)) {
            LivingEntity e = Aim.of(caster, range).entity();
            return e != null && mayLockOn(e) ? e : null;
        }
        World w = from.getWorld();
        if (w == null) return null;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : w.getNearbyEntities(from, range, range, range)) {
            if (!(e instanceof LivingEntity le) || !mayLockOn(le)) continue;
            Vector to = le.getLocation().add(0, le.getHeight() / 2, 0).toVector().subtract(from.toVector());
            double d = to.length();
            if (d > range || d < 1.0E-6 || Math.toDegrees(dir.angle(to)) > HOME_CONE_DEGREES) continue;
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    // ── flow ──

    /**
     * Wait for click (more-nodes spec §4.2): each wand click within {@code seconds} runs
     * on_click where the caster aims; with clicks left when the time ends, on_timeout runs here.
     */
    private void awaitClick(SpellNode n, Ctx c) {
        double range = n.num("range");
        int[] left = {Math.max(1, n.integer("clicks"))};
        boolean[] over = {false};
        services.clicks().add(caster.getUniqueId(), new ClickRouter.Listener() {
            public boolean live() { return !over[0] && caster.isOnline(); }

            public boolean click() {
                Aim.Hit h = Aim.of(caster, range);
                if (--left[0] <= 0) over[0] = true;
                SpellRun.this.run(n.children("on_click"), Ctx.of(h.point(), caster.getEyeLocation().getDirection(), h.entity(), caster, c.age()));
                return over[0];
            }
        });
        long ticks = Math.max(1, Math.round(n.num("seconds") * 20));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (over[0]) return;
            over[0] = true;
            if (caster.isOnline()) run(n.children("on_timeout"), Ctx.of(c.point(), c.dir(), c.target(), caster, c.age() + ticks / 20.0));
        }, ticks);
    }

    /** Repeat: runs its nodes now, then every {@code every} seconds until it ran {@code times} times. */
    private void repeat(SpellNode n, Ctx c) {
        List<SpellNode> then = n.children("then");
        int times = n.integer("times");
        long every = Math.max(1, Math.round(n.num("every") * 20));
        run(then, c);
        if (times <= 1) return;
        new BukkitRunnable() {
            int done = 1;

            @Override
            public void run() {
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }
                SpellRun.this.run(then, Ctx.of(c.point(), c.dir(), c.target(), caster, c.age() + done * every / 20.0));
                if (++done >= times) cancel();
            }
        }.runTaskTimer(plugin, every, every);
    }

    /** Check: the node's test; a test that needs a target fails without one. */
    private boolean check(SpellNode n, Ctx c) {
        LivingEntity t = c.target() != null && c.target().isValid() ? c.target() : null;
        return switch (n.str("test")) {
            case "target_player" -> t instanceof Player;
            case "target_mob" -> t != null && !(t instanceof Player);
            case "target_type" -> t != null && java.util.Arrays.stream(n.str("entities").split(","))
                    .map(s -> s.trim().toLowerCase(java.util.Locale.ROOT))
                    .anyMatch(s -> s.equals(t.getType().getKey().toString()) || s.equals(t.getType().getKey().getKey()));
            case "target_health" -> t != null && Placement.compare(n.str("compare"), healthPct(t), n.num("percent"));
            case "caster_health" -> Placement.compare(n.str("compare"), healthPct(caster), n.num("percent"));
            case "caster_mana" -> Placement.compare(n.str("compare"), casterMana(), n.num("mana"));
            case "caster_sneaking" -> caster.isSneaking();
            case "caster_on_ground" -> ((Entity) caster).isOnGround();
            case "target_burning" -> t != null && t.getFireTicks() > 0;
            case "conditions" -> {
                List<Condition> conds = QuestDef.conditions(n.array("conditions"));
                if (conds.isEmpty()) yield true;
                if ("one".equals(n.str("condition_match"))) yield conds.stream().anyMatch(k -> plugin.getConditionHandler().evaluate(caster, k, null));
                yield plugin.getConditionHandler().checkConditions(caster, conds, null);
            }
            default -> false;
        };
    }

    private static double healthPct(LivingEntity e) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        return cap <= 0 ? 0 : e.getHealth() / cap * 100;
    }

    /** Move: a new place, facing and target for its nodes. */
    private void movePoint(SpellNode n, Ctx c) {
        World w = c.point().getWorld();
        if (w == null) return;
        double range = n.num("range");
        Aim.Hit aim = "aim".equals(n.str("to")) || "aimed".equals(n.str("target")) ? Aim.of(caster, range) : null;
        LivingEntity old = c.target() != null && c.target().isValid() ? c.target() : null;
        Vector point = switch (n.str("to")) {
            case "caster" -> caster.getLocation().toVector();
            case "caster_eye" -> caster.getEyeLocation().toVector();
            case "target" -> old == null ? c.point().toVector() : old.getLocation().add(0, old.getHeight() / 2, 0).toVector();
            case "aim" -> aim.point().toVector();
            case "ground" -> {
                RayTraceResult down = w.rayTraceBlocks(c.point(), new Vector(0, -1, 0), 16, FluidCollisionMode.NEVER, true);
                yield down == null ? c.point().toVector() : down.getHitPosition();
            }
            default -> c.point().toVector();
        };
        Vector targetAt = old == null ? null : old.getLocation().add(0, old.getHeight() / 2, 0).toVector();
        Vector facing = Placement.facing(n.str("facing"), c.dir(), caster.getEyeLocation().getDirection(), point,
                caster.getEyeLocation().toVector(), targetAt, ThreadLocalRandom.current());
        boolean flat = Math.abs(facing.getX()) > 1.0E-6 || Math.abs(facing.getZ()) > 1.0E-6;
        Vector moved = Placement.offset(point, flat ? facing : c.dir(), n.num("forward"), n.num("up"), n.num("right"));
        LivingEntity target = switch (n.str("target")) {
            case "caster" -> caster;
            case "aimed" -> aim.entity();
            case "none" -> null;
            default -> old;
        };
        run(n.children("then"), Ctx.of(moved.toLocation(w), facing, target, caster, c.age()));
    }

    /**
     * Cast spell: the named published spell runs here at the caster's level of it, with this
     * cast's budget, no costs and no XP. At most {@code max_cast_depth} deep.
     */
    private void castSpell(SpellNode n, Ctx c) {
        if (depth >= spell.catalog().limits().maxCastDepth()) {
            overBudget("cast_spell depth");
            return;
        }
        SpellDef inner = services.spells().apply(n.str("spell"));
        if (inner == null) return;
        new SpellRun(this, inner).run(inner.roots(), c);
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
            if (!(e instanceof LivingEntity le) || e.getBoundingBox().clone().expand(width).rayTrace(start, dir, length) == null) continue;
            touchNpc(e);
            if (HitRules.shapeHits(e == caster, isNpc(e), false)) hits.add(le);
        }
        for (double d = 0; d < length; d += STEP) {
            Block b = start.clone().add(dir.clone().multiply(d)).toLocation(w).getBlock();
            if (!b.getType().isAir()) touch(services.locks().at(b));
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
        World w = c.point().getWorld();
        if (w == null) return;
        boolean followCaster = "caster".equals(n.str("center"));
        String form = n.str("form");
        double radius = n.num("radius");
        double height = n.num("height");
        double width = n.num("width");
        double duration = n.num("duration");
        boolean hitsCaster = n.bool("hits_caster");
        String pattern = n.str("pattern");
        String particle = n.str("particle");
        String color = color(n.str("color"));
        int cap = spell.catalog().limits().areaEntitiesPerTick();
        Location fixed = c.point().clone();
        // Where it is and which way a wall stands: around the caster it follows them and their look.
        Supplier<Location> centerNow = () -> followCaster ? caster.getLocation() : fixed;
        Supplier<Vector> facingNow = () -> followCaster ? caster.getLocation().getDirection() : c.dir();
        Predicate<Vector> in = p -> inForm(form, centerNow.get().toVector(), facingNow.get(), radius, height, width, p);
        double reach = "sphere".equals(form) ? radius : Math.max(radius, Math.max(width / 2, height)) + 1;
        Barriers.Barrier barrier = n.bool("stops_spells") ? services.barriers().add(w, caster.getUniqueId(), in) : null;
        if (duration <= 0) {
            Location center = centerNow.get();
            drawForm(form, center, facingNow.get(), radius, height, width, pattern, particle, color);
            touchLocksIn(center, in, reach);
            for (LivingEntity e : inside(center, in, reach, hitsCaster, cap)) run(n.children("on_enter"), at(c, center.clone(), e));
            run(n.children("on_end"), at(c, center.clone(), null));
            if (barrier != null) Bukkit.getScheduler().runTaskLater(plugin, barrier::remove, 1L); // an instant area stops bolts for one tick
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
                    if (barrier != null) barrier.remove();
                    return;
                }
                Location center = centerNow.get();
                if (age >= lifeTicks) {
                    cancel();
                    if (barrier != null) barrier.remove();
                    SpellRun.this.run(n.children("on_end"), at(c, center.clone(), null));
                    return;
                }
                if (age % 5 == 0) drawForm(form, center, facingNow.get(), radius, height, width, pattern, particle, color);
                if (age % pulseTicks == 0) {
                    touchLocksIn(center, in, reach);
                    List<LivingEntity> inNow = inside(center, in, reach, hitsCaster, cap);
                    for (LivingEntity e : inNow) {
                        if (entered.add(e.getUniqueId())) SpellRun.this.run(n.children("on_enter"), at(c, center.clone(), e));
                    }
                    for (LivingEntity e : inNow) SpellRun.this.run(n.children("on_tick"), at(c, center.clone(), e));
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Whether {@code p} is inside an area of this form standing on {@code center}. */
    private static boolean inForm(String form, Vector center, Vector facing, double radius, double height, double width, Vector p) {
        return switch (form) {
            case "cylinder" -> Geometry.inCylinder(center, radius, height, p);
            case "wall" -> Geometry.inWall(center, facing, width, height, p);
            default -> p.distanceSquared(center) <= radius * radius;
        };
    }

    /** The living entities whose feet are {@code in} the area (at most {@code cap}); {@code reach}: blocks around the centre to look. */
    private List<LivingEntity> inside(Location center, Predicate<Vector> in, double reach, boolean hitsCaster, int cap) {
        List<LivingEntity> out = new ArrayList<>();
        if (center.getWorld() == null) return out;
        for (Entity e : center.getWorld().getNearbyEntities(center, reach, reach, reach)) {
            if (out.size() >= cap) break;
            if (!(e instanceof LivingEntity le) || !in.test(e.getLocation().toVector())) continue;
            if (HitRules.shapeHits(e == caster, isNpc(e), hitsCaster)) out.add(le);
        }
        return out;
    }

    /** A sphere is drawn by its pattern; a cylinder as its two rings and a few posts, a wall as a grid of its face (pattern none: nothing). */
    private static void drawForm(String form, Location center, Vector facing, double radius, double height, double width,
                                 String pattern, String particle, String color) {
        if ("sphere".equals(form)) {
            draw(center, radius, pattern, particle, color);
            return;
        }
        if (particle.isBlank() || "none".equals(pattern)) return;
        SpellFx.Brush brush = SpellFx.brush(new SpellFx.Look(particle, color, "", 1.1f, "", 0));
        if (brush == null) return;
        if ("cylinder".equals(form)) {
            int points = (int) Math.min(48, Math.max(12, radius * 8));
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                brush.draw(center.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius), 0, 1, 0);
                brush.draw(center.clone().add(Math.cos(a) * radius, height, Math.sin(a) * radius), 0, 1, 0);
            }
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                for (double y = 0.5; y < height; y += 0.5) brush.draw(center.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius), 0, 1, 0);
            }
            return;
        }
        Vector along = ParticleShapes.horizontal(facing);
        Vector across = new Vector(-along.getZ(), 0, along.getX());
        double step = Math.max(0.5, Math.sqrt(width * height / 120));
        for (double s = -width / 2; s <= width / 2 + 1.0E-9; s += step) {
            for (double y = 0; y <= height + 1.0E-9; y += step) {
                brush.draw(center.clone().add(across.clone().multiply(s)).add(0, y, 0), 0, 1, 0);
            }
        }
    }

    /**
     * Cone (more-nodes §4.3): at once, every entity inside it that its tip can see, nearest
     * first; then on_end at the middle of its far end.
     */
    private void cone(SpellNode n, Ctx c) {
        if (!startShape(false)) return;
        World w = c.point().getWorld();
        if (w == null) return;
        double range = n.num("range");
        double angle = n.num("angle");
        Vector apex = c.point().toVector();
        Vector axis = c.dir().clone().normalize();
        SpellFx.Brush brush = trailBrush(n);
        if (brush != null) {
            double half = Math.toRadians(angle / 2);
            Vector[] frame = ParticleShapes.frame(axis, true);
            for (double d = 1; d <= range + 1.0E-9; d += Math.max(1, range / 8)) {
                double r = d * Math.sin(half), y = d * Math.cos(half);
                int points = (int) Math.min(24, Math.max(4, Math.round(r * 6)));
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    brush.draw(ParticleShapes.place(apex, frame, Math.cos(a) * r, y, Math.sin(a) * r).toLocation(w), 0, 1, 0);
                }
            }
        }
        List<LivingEntity> hits = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(c.point(), range, range, range)) {
            if (!(e instanceof LivingEntity le)) continue;
            Vector mid = middle(le);
            if (!Geometry.inCone(apex, axis, range, angle, mid) || !sees(w, apex, mid)) continue;
            touchNpc(e);
            if (HitRules.shapeHits(e == caster, isNpc(e), false)) hits.add(le);
        }
        hits.sort(Comparator.comparingDouble(e -> middle(e).distanceSquared(apex)));
        for (LivingEntity e : hits) hitEntity(n, e, c);
        run(n.children("on_end"), at(c, apex.clone().add(axis.clone().multiply(range)).toLocation(w), null));
    }

    /**
     * Chain (more-nodes §4.3): from the target (or the entity nearest the point) to the
     * nearest unused entity within jump range it can see, {@code jumps} times; the links are
     * picked at once and hit one after another, {@code delay} apart.
     */
    private void chain(SpellNode n, Ctx c) {
        if (!startShape(false)) return;
        World w = c.point().getWorld();
        if (w == null) return;
        int jumps = n.integer("jumps");
        double jumpRange = n.num("jump_range");
        String affects = n.str("affects");
        double reach = Math.min(64, jumpRange * (jumps + 1));
        List<LivingEntity> found = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(c.point(), reach, reach, reach)) {
            if (e instanceof LivingEntity le && mayLockOn(le) && HitRules.affects(affects, le instanceof Player)) found.add(le);
        }
        List<Vector> mids = found.stream().map(SpellRun::middle).toList();
        Set<Integer> used = new HashSet<>();
        List<LivingEntity> links = new ArrayList<>();
        Vector start = c.point().toVector();
        int next = c.target() == null ? -1 : found.indexOf(c.target());
        if (next < 0) next = Geometry.chainNext(start, mids, jumpRange, used, i -> sees(w, start, mids.get(i)));
        while (next >= 0) {
            used.add(next);
            links.add(found.get(next));
            if (links.size() > jumps) break;
            Vector here = mids.get(next);
            next = Geometry.chainNext(here, mids, jumpRange, used, i -> sees(w, here, mids.get(i)));
        }
        chainLink(n, c, w, links, 0, start, Math.round(n.num("delay") * 20), trailBrush(n));
    }

    /** Link {@code i} of a chain, coming from {@code from}; after the last, on_end there. */
    private void chainLink(SpellNode n, Ctx c, World w, List<LivingEntity> links, int i, Vector from, long delay, @Nullable SpellFx.Brush brush) {
        if (!caster.isOnline()) return;
        if (i >= links.size()) {
            run(n.children("on_end"), at(c, from.toLocation(w), null));
            return;
        }
        LivingEntity e = links.get(i);
        boolean there = e.isValid() && e.getWorld() == w;
        Vector to = there ? middle(e) : from;
        if (there) {
            if (brush != null) {
                Vector d = to.clone().subtract(from);
                int parts = (int) Math.min(60, Math.ceil(d.length() / 0.3));
                for (int k = 1; k <= parts; k++) brush.draw(from.clone().add(d.clone().multiply((double) k / parts)).toLocation(w), 0, 1, 0);
            }
            hitEntity(n, e, c);
        }
        Runnable rest = () -> chainLink(n, c, w, links, i + 1, to, delay, brush);
        if (delay <= 0) rest.run();
        else Bukkit.getScheduler().runTaskLater(plugin, rest, delay);
    }

    /**
     * Orbit (more-nodes §4.3): orbs circling the caster or the target, each hitting what it
     * touches (again after {@code rehit} seconds, or once); with stops_spells they end other
     * casters' bolts. on_end at the middle when it ends.
     */
    private void orbit(SpellNode n, Ctx c) {
        if (!startShape(false)) return;
        World w = c.point().getWorld();
        if (w == null) return;
        int orbs = Math.max(1, n.integer("orbs"));
        double radius = n.num("radius");
        double height = n.num("height");
        double speed = n.num("speed");
        double size = n.num("size");
        int life = (int) Math.round(n.num("duration") * 20);
        long rehit = Math.round(n.num("rehit") * 20);
        LivingEntity around = "target".equals(n.str("around")) ? c.target() : caster;
        SpellFx.Brush brush = trailBrush(n);
        Vector[] center = {c.point().toVector()};
        Vector[] points = new Vector[orbs];
        Map<UUID, Integer> lastHit = new HashMap<>();
        double stopR2 = (size + 0.3) * (size + 0.3);
        Barriers.Barrier barrier = !n.bool("stops_spells") ? null : services.barriers().add(w, caster.getUniqueId(), p -> {
            for (Vector o : points) if (o != null && o.distanceSquared(p) <= stopR2) return true;
            return false;
        });
        new BukkitRunnable() {
            int age;

            @Override
            public void run() {
                if (!caster.isOnline() || age > life) {
                    cancel();
                    if (barrier != null) barrier.remove();
                    if (caster.isOnline()) SpellRun.this.run(n.children("on_end"), at(c, center[0].toLocation(w), null));
                    return;
                }
                if (around != null && around.isValid() && around.getWorld() == w) center[0] = around.getLocation().toVector();
                double angle = speed * age / 20.0;
                for (int i = 0; i < orbs; i++) {
                    points[i] = Geometry.orbitPoint(center[0], radius, height, angle, i, orbs);
                    Location at = points[i].toLocation(w);
                    if (brush != null) brush.draw(at, 0, 1, 0);
                    for (Entity e : w.getNearbyEntities(at, size, size, size)) {
                        if (!(e instanceof LivingEntity le)) continue;
                        touchNpc(e);
                        if (!HitRules.shapeHits(e == caster, isNpc(e), false)) continue;
                        Integer last = lastHit.get(e.getUniqueId());
                        if (last != null && (rehit <= 0 || age - last < rehit)) continue;
                        lastHit.put(e.getUniqueId(), age);
                        hitEntity(n, le, c);
                    }
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static Vector middle(LivingEntity e) {
        return e.getLocation().add(0, e.getHeight() / 2, 0).toVector();
    }

    /** Whether nothing solid is between {@code a} and {@code b}. */
    private static boolean sees(World w, Vector a, Vector b) {
        Vector d = b.clone().subtract(a);
        double len = d.length();
        return len < 1.0E-6 || w.rayTraceBlocks(a.toLocation(w), d, len, FluidCollisionMode.NEVER, true) == null;
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

    /** Ignore PvP on a harmful node: it acts on players with PvP off and in PvP-denied regions too. */
    private static boolean ignoresPvp(SpellNode n) {
        return n.bool("ignore_pvp");
    }

    /**
     * Spell damage comes from the caster, so Paper credits the kill. With Ignore PvP on another
     * player that hit would be cancelled again by the PvP plugin and WorldGuard, so it has no
     * source entity, and the caster is made the killer by hand.
     */
    private DamageSource damageSource(SpellNode n, LivingEntity t) {
        if (ignoresPvp(n) && t instanceof Player victim && victim != caster) {
            victim.setKiller(caster);
            return DamageSource.builder(DamageType.MAGIC).build();
        }
        return DamageSource.builder(DamageType.MAGIC).withCausingEntity(caster).withDirectEntity(caster).build();
    }

    private boolean allowed(SpellNode n, LivingEntity t) {
        boolean isCaster = t == caster;
        boolean isPlayer = t instanceof Player;
        boolean pvpAllows = isPlayer && !isCaster && (ignoresPvp(n) || pvp.mayHurt(caster, (Player) t));
        return HitRules.mayAffect(n.kind().harmful(n), isCaster, isPlayer, pvpAllows);
    }

    private void effect(SpellNode n, Ctx c) {
        String type = n.type();
        LivingEntity t = null;
        if (n.isEntityEffect()) {
            t = target(n, c);
            if (t == null || !allowed(n, t)) return;
            creditHit(t);
        }
        switch (type) {
            case "damage" -> {
                deaths.remember(t, caster, reported.name());
                double before = t.getHealth() + t.getAbsorptionAmount();
                t.damage(MagicRules.damage(n.num("amount"), damagePct), damageSource(n, t));
                double after = t.isDead() ? 0 : t.getHealth() + t.getAbsorptionAmount();
                if (t.isDead() && HitRules.givesXp(t == caster, spell.casterOnly()) && killXp.add(t.getUniqueId())) xp.kill();
                double steal = EffectRules.lifesteal(n.num("lifesteal"), before, after);
                if (steal > 0 && !caster.isDead()) heal(caster, steal);
            }
            case "heal" -> heal(t, n.num("amount"));
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
            case "drop_items" -> {
                int amount = budget.itemsTaken(n.integer("amount"));
                if (amount == 0) overBudget("items");
                else services.litter().dropItems(c.point(), n.str("item"), amount, n.num("spread"), n.num("lift"), n.integer("seconds"),
                        "vanish".equals(n.str("pickup")), n.str("vanish_particle"), n.str("vanish_sound"), n.bool("glow"));
            }
            case "falling_blocks" -> {
                Material m = Material.matchMaterial(n.str("block"));
                if (m == null || !m.isBlock()) return;
                int amount = budget.itemsTaken(n.integer("amount"));
                if (amount == 0) overBudget("items");
                else services.litter().fallingBlocks(c.point(), m.createBlockData(), amount, n.num("spread"), n.num("lift"),
                        "shatter".equals(n.str("on_land")), n.integer("seconds"));
            }
            case "freeze" -> services.states().freeze(t, n.num("seconds"));
            case "shield" -> services.states().shield(t, n.num("amount"), n.num("seconds"));
            case "cleanse", "dispel" -> {
                NamespacedKey key = SpellFx.key(n.str("effect"));
                PotionEffectType named = key == null ? null : Registry.EFFECT.get(key);
                for (PotionEffect pe : List.copyOf(t.getActivePotionEffects())) {
                    if (EffectRules.removes(n.str("remove"), pe.getType().getCategory(), pe.getType().equals(named))) t.removePotionEffect(pe.getType());
                }
                if ("cleanse".equals(type) && n.bool("extinguish")) t.setFireTicks(0);
            }
            case "mana" -> mana(n, t);
            case "firework" -> {
                String fade = n.str("color2");
                services.visuals().firework(c.point(), color(n.str("color")), fade.isBlank() ? "" : color(fade), n.str("style"),
                        n.bool("flicker"), n.bool("trail"), n.num("height"));
            }
            case "lightning" -> services.visuals().lightning(c.point());
            case "vanish" -> services.states().vanish(caster, n.num("seconds"), n.bool("ends_on_cast"), n.bool("ends_on_damage"));
            case "fly" -> {
                if (t instanceof Player p) services.states().fly(p, n.num("seconds"), n.num("speed"));
            }
            case "display" -> display(n, c);
            case "fake_blocks" -> {
                Material m = Material.matchMaterial(n.str("block"));
                if (m == null || !m.isBlock()) return;
                List<int[]> cells = Geometry.fakeCells(n.str("form"), (int) Math.round(n.num("radius")), c.dir());
                int allowed = budget.fakeBlocks(cells.size());
                if (allowed < cells.size()) overBudget("fake blocks");
                if (allowed > 0) services.visuals().fakeBlocks(c.point(), m.createBlockData(), cells.subList(0, allowed), n.num("seconds"));
            }
            case "aggro" -> {
                if (t instanceof Mob m) m.setTarget("calm".equals(n.str("mode")) ? null : caster);
            }
            default -> { }
        }
    }

    /** Display (more-nodes §4.4): at the point, or kept on the target / caster while it lasts. */
    private void display(SpellNode n, Ctx c) {
        if (!budget.display()) {
            overBudget("displays");
            return;
        }
        LivingEntity followed = switch (n.str("follow")) {
            case "target" -> c.target() != null && c.target().isValid() ? c.target() : null;
            case "caster" -> caster;
            default -> null;
        };
        Supplier<Location> follow = followed == null ? null
                : () -> followed.isValid() ? followed.getLocation().add(0, followed.getHeight() / 2, 0) : null;
        Location at = follow == null ? c.point() : follow.get();
        String target = c.target() == null ? "" : c.target().getName();
        String glow = n.str("glow_color");
        services.visuals().display(at, new SpellVisuals.DisplayLook(n.str("kind"), n.str("item"), n.str("block"),
                n.str("text").replace("{caster}", caster.getName()).replace("{target}", target), n.num("scale"), n.num("spin"),
                n.str("billboard"), n.bool("glow"), glow.isBlank() ? "" : color(glow), n.num("seconds"), n.num("up"), n.str("grow")), follow);
    }

    private static void heal(LivingEntity t, double amount) {
        AttributeInstance max = t.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        t.setHealth(Math.min(cap, t.getHealth() + amount));
    }

    /** Mana (more-nodes §4.5): players with magic only; drain gives the caster what it took. */
    private void mana(SpellNode n, LivingEntity t) {
        if (!(t instanceof Player p)) return;
        MagicData d = services.magic().apply(p);
        if (d == null) return;
        double amount = n.num("amount");
        if ("give".equals(n.str("mode"))) {
            d.setMana(d.mana() + amount);
            return;
        }
        double taken = EffectRules.manaTaken(d.mana(), amount);
        d.setMana(d.mana() - taken);
        MagicData mine = "drain".equals(n.str("mode")) ? services.magic().apply(caster) : null;
        if (mine != null) mine.setMana(mine.mana() + taken);
    }

    private double casterMana() {
        MagicData d = services.magic().apply(caster);
        return d == null ? 0 : d.mana();
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
        if (who == null || !allowed(n, who)) return;
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

    /**
     * The node's command lines, run for the caster or (Run for: target) for the target player:
     * then {@code @p}, {@code send_message}, {@code sudo}, {@code server connect} … act on the
     * target. For the target it needs a player target and obeys PvP like teleport.
     */
    private void runActions(SpellNode n, Ctx c) {
        Player forWhom = caster;
        if ("target".equals(n.str("who"))) {
            if (!(c.target() instanceof Player t) || !t.isValid() || !allowed(n, t)) return;
            if (t != caster) creditHit(t);
            forWhom = t;
        }
        Location p = c.point();
        String target = c.target() == null ? "" : c.target().getName();
        List<String> lines = new ArrayList<>();
        for (String line : n.strings("commands")) {
            lines.add(line.replace("{caster}", caster.getName()).replace("{target}", target)
                    .replace("{x}", fmt(p.getX())).replace("{y}", fmt(p.getY())).replace("{z}", fmt(p.getZ()))
                    .replace("{world}", p.getWorld() == null ? "" : p.getWorld().getName()));
        }
        plugin.getActionHandler().executeActions(forWhom, lines, ActionHandler.SourceType.SPELL, spell.id(), null);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
