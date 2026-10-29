package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.systems.magic.model.WandSkin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WandRulesTest {

    static final WandSkin OAK = new WandSkin("oak", "Oak", "wos:wand_oak", true, "skin_oak");
    static final WandSkin GLASS = new WandSkin("glass", "Glass", "wand_glass", false, null);

    @Test
    void theSkinsModelOnlyWhenTheSkinIsOwned() {
        assertThat(WandRules.model(OAK, true, "wos:wand")).isEqualTo("wos:wand_oak");
        assertThat(WandRules.model(OAK, false, "wos:wand")).isEqualTo("wos:wand");
        assertThat(WandRules.model(null, true, "wos:wand")).isEqualTo("wos:wand");
        assertThat(WandRules.model(GLASS, true, null)).isEqualTo("wos:wand_glass"); // a bare name is in wos
        assertThat(WandRules.model(null, true, null)).isNull(); // keep the item's own
    }

    @Test
    void theColourTintsUnlessTheSkinSaysNo() {
        assertThat(WandRules.tinted(null, true)).isTrue();
        assertThat(WandRules.tinted(OAK, true)).isTrue();
        assertThat(WandRules.tinted(GLASS, true)).isFalse();
        assertThat(WandRules.tinted(GLASS, false)).isTrue(); // not owned: the default model, tinted
    }

    @Test
    void parsesColours() {
        assertThat(WandRules.rgb("#A1B2C3")).isEqualTo(0xA1B2C3);
        assertThat(WandRules.rgb("nope")).isEqualTo(0xFFFFFF);
        assertThat(WandRules.isColor("#00ff00")).isTrue();
        assertThat(WandRules.isColor("00ff00")).isFalse();
    }
}
