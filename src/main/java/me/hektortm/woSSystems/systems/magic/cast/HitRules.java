package me.hektortm.woSSystems.systems.magic.cast;

import java.util.Set;

/**
 * Who a spell may hit and harm (magic spec §2, §3.4, §8), without the server:
 * <ul>
 *   <li>shapes never hit NPCs; projectiles and beams never their caster; an area hits its
 *       caster when its {@code hits_caster} is on (your own fire zone hurts you);</li>
 *   <li>an effect's {@code affects} filters players / mobs;</li>
 *   <li>a harmful effect on another player needs PvP (the gate); the caster may always be
 *       harmed by their own area.</li>
 * </ul>
 */
public final class HitRules {

    /** Effects that harm their target (heal, particles, sound, run_actions don't); push and turn obey PvP too (spec §3.3). */
    static final Set<String> HARMFUL = Set.of("damage", "push", "turn", "potion", "fire", "teleport");

    private HitRules() { }

    /** Whether a shape hits an entity at all (its outlet runs for it). */
    public static boolean shapeHits(boolean isCaster, boolean isNpc, boolean mayHitCaster) {
        if (isNpc) return false;
        return !isCaster || mayHitCaster;
    }

    /** An effect's {@code affects} filter ({@code caster} is resolved before: the target is the caster). */
    public static boolean affects(String filter, boolean isPlayer) {
        if ("players".equals(filter)) return isPlayer;
        if ("mobs".equals(filter)) return !isPlayer;
        return true;
    }

    /**
     * Whether acting on a target earns hit / kill XP (spec §3.5): never for the caster, except
     * in a spell whose every effect is on the caster.
     */
    public static boolean givesXp(boolean targetIsCaster, boolean casterOnly) {
        return !targetIsCaster || casterOnly;
    }

    /** Whether an effect may act on its target. */
    public static boolean mayAffect(String effect, boolean targetIsCaster, boolean targetIsPlayer, boolean pvpAllows) {
        if (targetIsCaster || !targetIsPlayer || !HARMFUL.contains(effect)) return true;
        return pvpAllows;
    }
}
