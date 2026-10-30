package me.hektortm.woSSystems.systems.interactions;

import me.hektortm.woSSystems.utils.model.Condition;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Spell locks (magic spec §9): an interaction row with a {@code hit_by_spell} or
 * {@code not_hit_by_spell} condition is a <em>spell row</em>. It runs only when a spell
 * hits the interaction's block, NPC or placed item, never on a click or any other
 * trigger; the other rows never run on a spell hit. While the row runs, the spell is
 * known as {@code {hit.spell}}, {@code {hit.spell_name}} and {@code {hit.level}}.
 */
public final class SpellHits {

    public static final String HIT_BY = "hit_by_spell";
    public static final String NOT_HIT_BY = "not_hit_by_spell";

    /** Keys of the run's local values (filled like a custom screen's inputs). */
    public static final String SPELL = "hit.spell";
    public static final String SPELL_NAME = "hit.spell_name";
    public static final String LEVEL = "hit.level";

    private SpellHits() { }

    /** The local values of a run started by that spell hitting the interaction. */
    public static Map<String, String> values(String spellId, String spellName, int level) {
        return Map.of(SPELL, spellId, SPELL_NAME, spellName, LEVEL, String.valueOf(level));
    }

    /** The spell that hit, if the run's values are a spell hit's. */
    @Nullable
    public static String spell(@Nullable Map<String, String> values) {
        return values == null ? null : values.get(SPELL);
    }

    /** Whether a row with these conditions is a spell row. */
    public static boolean isSpellRow(List<Condition> conditions) {
        for (Condition c : conditions) {
            String name = c.getName() == null ? "" : c.getName().toLowerCase();
            if (HIT_BY.equals(name) || NOT_HIT_BY.equals(name)) return true;
        }
        return false;
    }

    /**
     * Whether the spell that hit ({@code hit}, null: no spell hit) is the one the condition
     * names: an empty value or {@code *} means any spell.
     */
    public static boolean matches(@Nullable String wanted, @Nullable String hit) {
        if (hit == null) return false;
        String w = wanted == null ? "" : wanted.trim();
        return w.isEmpty() || "*".equals(w) || w.equalsIgnoreCase(hit);
    }
}
