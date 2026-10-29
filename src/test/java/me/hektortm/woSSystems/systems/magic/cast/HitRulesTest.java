package me.hektortm.woSSystems.systems.magic.cast;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Who a spell may hit and harm (magic spec §2, §8). */
class HitRulesTest {

    @Test
    void shapesNeverHitNpcsAndHitTheCasterOnlyFromAnAreaThatSaysSo() {
        assertThat(HitRules.shapeHits(false, true, false)).isFalse();       // an NPC
        assertThat(HitRules.shapeHits(true, false, false)).isFalse();       // the caster, a projectile / beam
        assertThat(HitRules.shapeHits(true, false, true)).isTrue();         // the caster inside their own area
        assertThat(HitRules.shapeHits(false, false, false)).isTrue();       // anyone else
    }

    @Test
    void affectsFilters() {
        assertThat(HitRules.affects("all", true)).isTrue();
        assertThat(HitRules.affects("all", false)).isTrue();
        assertThat(HitRules.affects("players", true)).isTrue();
        assertThat(HitRules.affects("players", false)).isFalse();
        assertThat(HitRules.affects("mobs", false)).isTrue();
        assertThat(HitRules.affects("mobs", true)).isFalse();
        assertThat(HitRules.affects("odd", true)).isTrue(); // an unknown filter acts like all
    }

    @Test
    void harmToOtherPlayersNeedsPvp() {
        // effect, target is the caster, target is a player, PvP allows it
        assertThat(HitRules.mayAffect("damage", true, true, false)).isTrue();    // your own area hurts you
        assertThat(HitRules.mayAffect("damage", false, true, false)).isFalse();  // another player, PvP off
        assertThat(HitRules.mayAffect("damage", false, true, true)).isTrue();
        assertThat(HitRules.mayAffect("damage", false, false, false)).isTrue();  // a mob
        assertThat(HitRules.mayAffect("push", false, true, false)).isFalse();
        assertThat(HitRules.mayAffect("turn", false, true, false)).isFalse();
        assertThat(HitRules.mayAffect("push", true, true, false)).isTrue();     // a dash on yourself
        assertThat(HitRules.mayAffect("potion", false, true, false)).isFalse();
        assertThat(HitRules.mayAffect("heal", false, true, false)).isTrue();     // healing a friend needs no PvP
    }

    @Test
    void xpForHittingYourselfOnlyFromCasterOnlySpells() {
        // target is the caster, the spell only acts on the caster
        assertThat(HitRules.givesXp(false, false)).isTrue();
        assertThat(HitRules.givesXp(true, false)).isFalse();
        assertThat(HitRules.givesXp(true, true)).isTrue();
    }
}
