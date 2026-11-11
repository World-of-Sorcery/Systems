package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.utils.ColorText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Magic messages ({@code MagicText.text}) turn hex colours with {@code ColorText.hex} before
 * their usual {@code &} codes, so a spell name with hex colours put into {@code %spell%}
 * shows coloured, not raw (spec 2026-11-11 §7). A plain "&" stays as it is.
 */
class MagicTextHexTest {

    @Test
    void hexTurnsOnlyTheHexForms() {
        assertThat(ColorText.hex("#FF0000a&cb")).isEqualTo("§x§F§F§0§0§0§0a&cb");
        assertThat(ColorText.hex("<#00FF00>x")).isEqualTo("§x§0§0§F§F§0§0x");
        assertThat(ColorText.hex("&#0000FFy")).isEqualTo("§x§0§0§0§0§F§Fy");
        assertThat(ColorText.hex("Tom & Jerry #12")).isEqualTo("Tom & Jerry #12");
    }
}
