package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.systems.magic.model.SpellDef;

import java.util.ArrayList;
import java.util.List;

/**
 * XP and levels of a spell (magic spec §3.5), without the server. A player's progress is
 * their level and the XP gathered towards the next one. XP that crosses several levels
 * runs each level-up in turn; XP at the max level is not kept. A saved level above the
 * spell's max (staff removed rows) stays saved and counts as the max.
 */
public final class SpellLevels {

    /** The progress after a change, and every level reached on the way (in order). */
    public record Gain(int level, double xp, List<Integer> reached) { }

    private SpellLevels() { }

    public static Gain add(int level, double xp, double amount, SpellDef spell) {
        int max = spell.maxLevel();
        if (level >= max) return new Gain(level, 0, List.of());
        double left = xp + Math.max(0, amount);
        List<Integer> reached = new ArrayList<>();
        while (level < max && left >= spell.xpToNext(level)) {
            left -= spell.xpToNext(level);
            level++;
            reached.add(level);
        }
        return new Gain(level, level >= max ? 0 : left, List.copyOf(reached));
    }

    /** Takes XP towards the next level; never below 0, never a level. */
    public static Gain take(int level, double xp, double amount) {
        return new Gain(level, Math.max(0, xp - Math.max(0, amount)), List.of());
    }

    public static boolean validLevel(int level, SpellDef spell) {
        return level >= 1 && level <= spell.maxLevel();
    }
}
