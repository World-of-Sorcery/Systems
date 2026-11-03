package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.MagicAttributes;
import org.bukkit.potion.PotionEffectTypeCategory;

import javax.annotation.Nullable;
import java.util.Locale;

/** The pure decisions of the combat effects (more-nodes spec §4.5, Attribute: 2026-11-03 spec §3). */
final class EffectRules {

    private EffectRules() { }

    /** What lifesteal heals: {@code pct} percent of the health (and absorption) really lost, {@code before} → {@code after}. */
    static double lifesteal(double pct, double before, double after) {
        return Math.max(0, before - after) * pct / 100;
    }

    /**
     * An Attribute effect's bonus on a magic attribute ({@code max_mana}, {@code mana_regen},
     * {@code cooldown}, {@code spell_damage}: always added, in their own units); null for a
     * Minecraft attribute.
     */
    @Nullable
    static MagicAttributes magicBonus(String attribute, double amount) {
        return switch (attribute) {
            case "max_mana" -> new MagicAttributes(amount, 0, 0, 0);
            case "mana_regen" -> new MagicAttributes(0, amount, 0, 0);
            case "cooldown" -> new MagicAttributes(0, 0, amount, 0);
            case "spell_damage" -> new MagicAttributes(0, 0, 0, amount);
            default -> null;
        };
    }

    /**
     * The key of a spell's change of one attribute: the same spell and attribute share it, so
     * a new change replaces the old one; other spells' changes add up.
     */
    static String attributeKey(String spellId, String attribute) {
        return "spell_attr_" + spellId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_") + "_" + attribute;
    }

    /** The modifier amount: {@code add} as written; {@code percent} as a share (multiplies the value by 1 + amount / 100). */
    static double modifierAmount(String mode, double amount) {
        return "percent".equals(mode) ? amount / 100 : amount;
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
