package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.potion.PotionEffectTypeCategory;

/** The pure decisions of the combat effects (more-nodes spec §4.5). */
final class EffectRules {

    private EffectRules() { }

    /** What lifesteal heals: {@code pct} percent of the health (and absorption) really lost, {@code before} → {@code after}. */
    static double lifesteal(double pct, double before, double after) {
        return Math.max(0, before - after) * pct / 100;
    }

    /** Mana a take or drain removes: at most what the target has. */
    static double manaTaken(double has, double amount) {
        return Math.max(0, Math.min(has, amount));
    }

    /**
     * Whether cleanse / dispel removes an effect of {@code category}: harmful / helpful by
     * category, all of them, or (one) only the named effect.
     */
    static boolean removes(String mode, PotionEffectTypeCategory category, boolean isNamed) {
        return switch (mode) {
            case "harmful" -> category == PotionEffectTypeCategory.HARMFUL;
            case "helpful" -> category == PotionEffectTypeCategory.BENEFICIAL;
            case "all" -> true;
            default -> isNamed;
        };
    }
}
