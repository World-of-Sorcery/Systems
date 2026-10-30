package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.systems.magic.model.MagicSettings;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MagicRulesTest {

    @Test
    void attributesAddUpAndFollowTheSlot() {
        String json = "{\"slot\":\"main_hand\",\"max_mana\":20,\"mana_regen\":1.5,\"cooldown_reduction\":10,\"spell_damage\":25}";
        MagicAttributes held = MagicAttributes.parse(json, "main_hand");
        assertThat(held).isEqualTo(new MagicAttributes(20, 1.5, 10, 25));
        assertThat(MagicAttributes.parse(json, "armor")).isEqualTo(MagicAttributes.NONE);
        MagicAttributes any = MagicAttributes.parse("{\"slot\":\"any\",\"max_mana\":5}", "off_hand");
        assertThat(held.plus(any)).isEqualTo(new MagicAttributes(25, 1.5, 10, 25));
        assertThat(MagicAttributes.parse("not json", "main_hand")).isEqualTo(MagicAttributes.NONE);
        assertThat(MagicAttributes.parse(null, "main_hand")).isEqualTo(MagicAttributes.NONE);
    }

    @Test
    void manaRules() {
        MagicAttributes a = new MagicAttributes(20, 1, 0, 0);
        assertThat(MagicRules.effectiveMax(100, a)).isEqualTo(120);
        assertThat(MagicRules.effectiveRegen(2, a)).isEqualTo(3);
        assertThat(MagicRules.clampMana(130, 120)).isEqualTo(120);
        assertThat(MagicRules.clampMana(-1, 120)).isZero();
        assertThat(MagicRules.regenStep(118, 120, 3, 1)).isEqualTo(120);
        assertThat(MagicRules.regenStep(50, 120, 3, 1)).isEqualTo(53);
    }

    @Test
    void cooldownAndDamageScale() {
        assertThat(MagicRules.cooldownMillis(2, 0)).isEqualTo(2000);
        assertThat(MagicRules.cooldownMillis(2, 25)).isEqualTo(1500);
        assertThat(MagicRules.cooldownMillis(10, 95)).isEqualTo(2000); // capped at 80 %
        assertThat(MagicRules.damage(4, 50)).isEqualTo(6);
        assertThat(MagicRules.damage(4, -200)).isZero();
    }

    @Test
    void wandColourStaysInsideTheRange() {
        MagicSettings.ColorRange r = new MagicSettings.ColorRange(200, 240, 60, 80, 70, 90);
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            String hex = MagicRules.wandColor(r, random);
            assertThat(hex).matches("#[0-9A-F]{6}");
            Color c = Color.decode(hex);
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            assertThat(hsb[0] * 360).isBetween(199.0f, 241.0f);
            assertThat(hsb[1] * 100).isBetween(59.0f, 81.0f);
            assertThat(hsb[2] * 100).isBetween(69.0f, 91.0f);
        }
        // A hue range that wraps past 360 (reds): min > max is not allowed by the API; equal works.
        assertThat(MagicRules.wandColor(new MagicSettings.ColorRange(0, 0, 100, 100, 100, 100), new Random(1))).isEqualTo("#FF0000");
    }

    @Test
    void loadoutAndKeys() {
        String[] loadout = new String[16];
        java.util.Arrays.fill(loadout, "");
        loadout[0] = "ember";
        assertThat(MagicRules.firstFreeSlot(loadout)).isEqualTo(1);
        java.util.Arrays.fill(loadout, "x");
        assertThat(MagicRules.firstFreeSlot(loadout)).isEqualTo(-1);
        assertThat(MagicRules.keyOf(0, 0)).isEqualTo(2);
        assertThat(MagicRules.keyOf(4, 4)).isEqualTo(1);
        for (int wand = 0; wand < 9; wand++) {
            for (int pos = 0; pos < 8; pos++) {
                assertThat(MagicRules.posOf(MagicRules.keyOf(pos, wand) - 1, wand)).isEqualTo(pos);
            }
            assertThat(MagicRules.posOf(wand, wand)).isEqualTo(-1);
        }
    }

    @Test
    void manaText() {
        assertThat(MagicRules.manaText(80.4)).isEqualTo("80");
        assertThat(MagicRules.seconds(1234)).isEqualTo("1.2");
        assertThat(MagicRules.seconds(12_400)).isEqualTo("12");
        assertThat(1.0).isCloseTo(1.0, within(0.0));
    }
}
