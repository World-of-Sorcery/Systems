package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * State of one projectile bolt in flight.
 */
public final class Bolt {
    private final Location at;
    private Vector vel;
    private double travelled;
    private int ticks;
    private int pierced;
    private int redirectsLeft;
    private int bouncesLeft;
    private boolean returning;
    private final Set<UUID> hit = new HashSet<>();
    private boolean active = true;
    @Nullable
    private Vector aimAt;

    public Bolt(Location at, Vector vel, int redirects, int bounces) {
        this.at = at.clone();
        this.vel = vel.clone();
        this.redirectsLeft = redirects;
        this.bouncesLeft = bounces;
    }

    public Location at() {
        return at;
    }

    public Vector vel() {
        return vel;
    }

    public void setVel(Vector vel) {
        this.vel = vel;
    }

    public double travelled() {
        return travelled;
    }

    public void addTravelled(double dist) {
        this.travelled += dist;
    }

    public int ticks() {
        return ticks;
    }

    public int incTicks() {
        return ++ticks;
    }

    public int pierced() {
        return pierced;
    }

    public int incPierced() {
        return ++pierced;
    }

    public int redirectsLeft() {
        return redirectsLeft;
    }

    public Set<UUID> hit() {
        return hit;
    }

    public boolean isActive() {
        return active;
    }

    public void end() {
        this.active = false;
    }

    public boolean canRedirect() {
        return active && redirectsLeft > 0;
    }

    public void consumeRedirect() {
        this.travelled = 0;
        this.redirectsLeft--;
    }

    public void redirect(Vector toward) {
        double speed = vel.length();
        if (toward.lengthSquared() > 1e-9) {
            this.vel = toward.clone().normalize().multiply(speed);
        }
        this.travelled = 0;
        this.redirectsLeft--;
    }

    /** Where a redirect click aimed while a limited turn rate still turns it there; null: none. */
    @Nullable
    public Vector aimAt() {
        return aimAt;
    }

    public void aimAt(@Nullable Vector point) {
        this.aimAt = point;
    }

    /** Uses up one bounce; false when none are left (the block ends it). */
    public boolean bounce() {
        if (bouncesLeft <= 0) return false;
        bouncesLeft--;
        return true;
    }

    public boolean returning() {
        return returning;
    }

    /**
     * Turns it into a returning bolt: a fresh range for the way back, each entity may be hit
     * once more, and it no longer obeys redirect clicks.
     */
    public void startReturn() {
        this.returning = true;
        this.travelled = 0;
        this.redirectsLeft = 0;
        this.hit.clear();
    }

    // ── what Change bolt may change (2026-11-03 spec §6); set at launch ──

    private double range = 16;
    private double size = 0.4;
    private int pierce;
    private boolean gravity;
    private double acceleration;
    @Nullable
    private SpellFx.Look trailLook;
    @Nullable
    private SpellFx.Brush trail;
    /** Launched with {@code steer: redirect}: only such a bolt gets more redirects. */
    private boolean redirectable;

    public double range() { return range; }

    public double size() { return size; }

    public int pierce() { return pierce; }

    public boolean gravity() { return gravity; }

    public double acceleration() { return acceleration; }

    @Nullable
    public SpellFx.Brush trail() { return trail; }

    /** The look its trail brush was made from (null: no trail). */
    @Nullable
    public SpellFx.Look trailLook() { return trailLook; }

    public Bolt trail(@Nullable SpellFx.Look look, @Nullable SpellFx.Brush brush) {
        this.trailLook = look;
        this.trail = brush;
        return this;
    }

    /** The flight it was launched with. */
    public Bolt flight(double range, double size, int pierce, boolean gravity, double acceleration) {
        this.range = range;
        this.size = size;
        this.pierce = pierce;
        this.gravity = gravity;
        this.acceleration = acceleration;
        return this;
    }

    /**
     * Change bolt: {@code speed} > 0 sets the speed, then {@code factor} multiplies it (kept
     * within 0.05–4, the direction stays); null keeps acceleration / gravity; range
     * {@code reset} starts the distance flown again, {@code extend} adds {@code extraRange};
     * {@code size} > 0 sets the hit radius; pierce and redirects are added (redirects only on a
     * bolt that obeys clicks and isn't on its way back).
     */
    public void change(double speed, double factor, @Nullable Double acceleration, @Nullable Boolean gravity, String rangeMode, double extraRange,
                       double size, int morePierce, int moreRedirects) {
        double now = vel.length();
        if (now > 1.0E-9) {
            double next = speed > 0 ? speed : now;
            next = Math.max(MIN_SPEED, Math.min(MAX_SPEED, next * factor));
            this.vel = vel.clone().multiply(next / now);
        }
        if (acceleration != null) this.acceleration = acceleration;
        if (gravity != null) this.gravity = gravity;
        switch (rangeMode) {
            case "reset" -> this.travelled = 0;
            case "extend" -> this.range += extraRange;
            default -> { }
        }
        if (size > 0) this.size = size;
        this.pierce += Math.max(0, morePierce);
        if (moreRedirects > 0 && !returning && redirectable) this.redirectsLeft += moreRedirects;
    }

    public Bolt redirectable(boolean yes) {
        this.redirectable = yes;
        return this;
    }

    static final double MIN_SPEED = 0.05, MAX_SPEED = 4;
}
