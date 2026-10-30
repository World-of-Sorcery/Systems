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
}
