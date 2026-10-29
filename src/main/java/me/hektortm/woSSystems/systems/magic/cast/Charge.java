package me.hektortm.woSSystems.systems.magic.cast;

/**
 * One spell charging (magic spec §3.2), without the server: it fires on its own at
 * {@code endMs}, unless something cancels it first (at no cost). Wand and slot only matter
 * for a cast from the wand; the no-spell region not for {@code /spell test}. Damage, death,
 * logout, teleports and the ring are events, so {@link ChargeService} cancels for them.
 *
 * @param slot  the held hotbar slot at the click
 * @param world the caster's world at the click
 */
public record Charge(String spellId, CastService.Mode mode, int level, long startMs, long endMs, int slot, String world) {

    /** What the caster is doing now. */
    public record View(boolean online, boolean holdsWand, int slot, String world, boolean castAllowed) { }

    public enum Step { WAIT, FIRE, OFFLINE, NO_WAND, SLOT, WORLD, REGION }

    public Step tick(long now, View v) {
        if (!v.online()) return Step.OFFLINE;
        if (mode == CastService.Mode.PLAYER) {
            if (!v.holdsWand()) return Step.NO_WAND;
            if (v.slot() != slot) return Step.SLOT;
        }
        if (!world.equals(v.world())) return Step.WORLD;
        if (mode != CastService.Mode.TEST && !v.castAllowed()) return Step.REGION;
        return now >= endMs ? Step.FIRE : Step.WAIT;
    }

    /** How far it is, 0 .. 1. */
    public double done(long now) {
        if (endMs <= startMs) return 1;
        return Math.max(0, Math.min(1, (double) (now - startMs) / (endMs - startMs)));
    }

    /** The charge bar: {@code cells} cells, the done ones lit (& colours). */
    public static String bar(double done, int cells) {
        int lit = (int) Math.round(Math.max(0, Math.min(1, done)) * cells);
        return (lit > 0 ? "&e" + "▮".repeat(lit) : "") + "&7" + "▮".repeat(cells - lit);
    }
}
