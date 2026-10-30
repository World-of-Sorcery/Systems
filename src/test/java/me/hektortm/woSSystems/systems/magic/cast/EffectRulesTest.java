package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.potion.PotionEffectTypeCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EffectRulesTest {

    @Test
    void lifestealIsAShareOfWhatWasReallyLost() {
        assertThat(EffectRules.lifesteal(50, 20, 14)).isCloseTo(3.0, within(1e-9));
        assertThat(EffectRules.lifesteal(100, 5, 0)).isCloseTo(5.0, within(1e-9)); // overkill: only what it had
        assertThat(EffectRules.lifesteal(50, 10, 10)).isZero();                    // blocked
        assertThat(EffectRules.lifesteal(0, 20, 10)).isZero();
    }

    @Test
    void takeAndDrainTakeAtMostWhatTheTargetHas() {
        assertThat(EffectRules.manaTaken(30, 10)).isEqualTo(10);
        assertThat(EffectRules.manaTaken(4, 10)).isEqualTo(4);
        assertThat(EffectRules.manaTaken(0, 10)).isZero();
    }

    @Test
    void cleanseAndDispelPickByCategory() {
        assertThat(EffectRules.removes("harmful", PotionEffectTypeCategory.HARMFUL, false)).isTrue();
        assertThat(EffectRules.removes("harmful", PotionEffectTypeCategory.BENEFICIAL, false)).isFalse();
        assertThat(EffectRules.removes("harmful", PotionEffectTypeCategory.NEUTRAL, false)).isFalse();
        assertThat(EffectRules.removes("helpful", PotionEffectTypeCategory.BENEFICIAL, false)).isTrue();
        assertThat(EffectRules.removes("helpful", PotionEffectTypeCategory.HARMFUL, false)).isFalse();
        assertThat(EffectRules.removes("all", PotionEffectTypeCategory.NEUTRAL, false)).isTrue();
        assertThat(EffectRules.removes("one", PotionEffectTypeCategory.HARMFUL, false)).isFalse();
        assertThat(EffectRules.removes("one", PotionEffectTypeCategory.BENEFICIAL, true)).isTrue();
    }
}
