package me.hektortm.woSSystems.systems.interactions;

import me.hektortm.woSSystems.systems.cscreens.CscreenRules;
import me.hektortm.woSSystems.utils.model.Condition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link SpellHits}: which rows are spell rows, which spell matches, the hit's values. */
class SpellHitsTest {

    @Test
    void aRowWithASpellConditionIsASpellRow() {
        assertThat(SpellHits.isSpellRow(List.of(new Condition("has_permission", "x", null), new Condition("hit_by_spell", "fireball", null)))).isTrue();
        assertThat(SpellHits.isSpellRow(List.of(new Condition("NOT_HIT_BY_SPELL", "frost", null)))).isTrue();
        assertThat(SpellHits.isSpellRow(List.of(new Condition("has_spell", "fireball", null)))).isFalse();
        assertThat(SpellHits.isSpellRow(List.of())).isFalse();
    }

    @Test
    void anEmptyValueOrStarMatchesAnySpell() {
        assertThat(SpellHits.matches("", "fireball")).isTrue();
        assertThat(SpellHits.matches("*", "fireball")).isTrue();
        assertThat(SpellHits.matches(null, "fireball")).isTrue();
        assertThat(SpellHits.matches(" Fireball ", "fireball")).isTrue();
        assertThat(SpellHits.matches("frost", "fireball")).isFalse();
    }

    @Test
    void withoutASpellHitNothingMatches() {
        assertThat(SpellHits.matches("", null)).isFalse();
        assertThat(SpellHits.spell(null)).isNull();
    }

    @Test
    void theHitsValuesFillTheHitPlaceholders() {
        var values = SpellHits.values("fireball", "Fireball", 3);
        assertThat(SpellHits.spell(values)).isEqualTo("fireball");
        assertThat(CscreenRules.fillInputs("say {hit.spell_name} ({hit.spell}) level {hit.level}, {hit.nope} {input.x}", values))
                .isEqualTo("say Fireball (fireball) level 3, {hit.nope} ");
    }
}
