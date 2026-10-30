package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.systems.magic.model.MagicSettings;

import java.awt.Color;
import java.util.Random;

/** The server-free rules of magic (mana, attributes, cooldowns, wand colour, ring keys). */
public final class MagicRules {

    /** Ring slots per page and pages. */
    public static final int SLOTS = 8;
    public static final int PAGES = 2;
    /** Cooldown reduction never goes beyond this (%). */
    static final double MAX_COOLDOWN_REDUCTION = 80;

    private MagicRules() { }

    public static double effectiveMax(double baseMax, MagicAttributes a) {
        return Math.max(0, baseMax + a.maxMana());
    }

    public static double effectiveRegen(double baseRegen, MagicAttributes a) {
        return Math.max(0, baseRegen + a.regen());
    }

    public static double clampMana(double mana, double max) {
        return Math.max(0, Math.min(mana, max));
    }

    public static double regenStep(double mana, double max, double regen, double seconds) {
        return clampMana(mana + regen * seconds, max);
    }

    /** A cooldown of {@code seconds}, shortened by the cooldown attribute (capped at 80 %). */
    public static long cooldownMillis(double seconds, double reductionPct) {
        double r = Math.max(0, Math.min(reductionPct, MAX_COOLDOWN_REDUCTION));
        return Math.round(seconds * 1000 * (1 - r / 100));
    }

    /** Damage raised (or lowered) by the spell damage attribute; never negative. */
    public static double damage(double amount, double damagePct) {
        return Math.max(0, amount * (1 + damagePct / 100));
    }

    /** A random colour inside the settings' HSB range, as {@code #RRGGBB}. */
    public static String wandColor(MagicSettings.ColorRange r, Random random) {
        float h = (float) (between(r.hueMin(), r.hueMax(), random) / 360.0);
        float s = (float) (between(r.satMin(), r.satMax(), random) / 100.0);
        float b = (float) (between(r.briMin(), r.briMax(), random) / 100.0);
        return String.format("#%06X", Color.HSBtoRGB(h, s, b) & 0xFFFFFF);
    }

    private static double between(double lo, double hi, Random random) {
        return hi <= lo ? lo : lo + random.nextDouble() * (hi - lo);
    }

    /** The first empty loadout slot (page 1 first), or -1 when all are taken. */
    public static int firstFreeSlot(String[] loadout) {
        for (int i = 0; i < loadout.length; i++) {
            if (loadout[i] == null || loadout[i].isEmpty()) return i;
        }
        return -1;
    }

    /** The number key (1..9) of ring position {@code pos} (0 = top, clockwise) with the wand in hotbar slot {@code wand} (0..8). */
    public static int keyOf(int pos, int wand) {
        return (wand + 1 + pos) % 9 + 1;
    }

    /** The ring position a hotbar slot (0..8) stands for, or -1 for the wand's own slot. */
    public static int posOf(int slot, int wand) {
        int p = Math.floorMod(slot - wand - 1, 9);
        return p == SLOTS ? -1 : p;
    }

    /** Mana as players see it: whole points. */
    public static String manaText(double mana) {
        return String.valueOf((long) Math.floor(mana));
    }

    /** Seconds left: one decimal below 10 s, whole seconds above. */
    public static String seconds(long millis) {
        double s = millis / 1000.0;
        return s < 10 ? String.format(java.util.Locale.ROOT, "%.1f", s) : String.valueOf((long) Math.ceil(s - 0.5));
    }
}
