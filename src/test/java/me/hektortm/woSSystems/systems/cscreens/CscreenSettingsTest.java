package me.hektortm.woSSystems.systems.cscreens;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link CscreenSettings}: defaults, limits, and settings that can't be read. */
class CscreenSettingsTest {

    @Test
    void aScreenWithoutSettingsGetsTheDefaults() {
        for (String json : new String[]{null, "", "not json", "[1,2]"}) {
            CscreenSettings.Screen s = CscreenSettings.screen(json);
            assertThat(s.externalTitle()).isEmpty();
            assertThat(s.canCloseWithEscape()).isTrue();
            assertThat(s.afterAction()).isEqualTo("close");
            assertThat(s.columns()).isEqualTo(2);
            assertThat(s.buttonWidth()).isEqualTo(150);
            assertThat(s.screens()).isEmpty();
        }
    }

    @Test
    void aScreenReadsItsSettings() {
        CscreenSettings.Screen s = CscreenSettings.screen(
                "{\"external_title\":\"Shop\",\"can_close_with_escape\":false,\"after_action\":\"wait_for_response\",\"columns\":3,"
                        + "\"button_width\":200,\"screens\":[\"a\",\"b\"]}");
        assertThat(s.externalTitle()).isEqualTo("Shop");
        assertThat(s.canCloseWithEscape()).isFalse();
        assertThat(s.afterAction()).isEqualTo("wait_for_response");
        assertThat(s.columns()).isEqualTo(3);
        assertThat(s.buttonWidth()).isEqualTo(200);
        assertThat(s.screens()).containsExactly("a", "b");
    }

    @Test
    void numbersStayInsideWhatMinecraftAccepts() {
        CscreenSettings.Screen s = CscreenSettings.screen("{\"columns\":0,\"button_width\":99999,\"after_action\":\"explode\"}");
        assertThat(s.columns()).isEqualTo(1);
        assertThat(s.buttonWidth()).isEqualTo(1024);
        assertThat(s.afterAction()).isEqualTo("close");
        assertThat(CscreenSettings.body("{\"width\":-5}").width()).isEqualTo(1);
        assertThat(CscreenSettings.button("{\"width\":\"300\"}").width()).isEqualTo(300); // a number saved as text
    }

    @Test
    void aTextBodyAndAnItemBody() {
        CscreenSettings.Body text = CscreenSettings.body("{\"kind\":\"text\",\"text\":\"Hello\"}");
        assertThat(text.item()).isFalse();
        assertThat(text.text()).isEqualTo("Hello");
        assertThat(text.width()).isEqualTo(200);

        CscreenSettings.Body item = CscreenSettings.body("{\"kind\":\"item\",\"item_kind\":\"citem\",\"citem\":\"wand\",\"amount\":500,\"show_tooltip\":false}");
        assertThat(item.item()).isTrue();
        assertThat(item.citem()).isTrue();
        assertThat(item.citemId()).isEqualTo("wand");
        assertThat(item.amount()).isEqualTo(99);
        assertThat(item.showTooltip()).isFalse();
        assertThat(item.showDecorations()).isTrue();
        assertThat(item.width()).isEqualTo(16);
        assertThat(item.height()).isEqualTo(16);
    }

    @Test
    void aTextInputsStartValueFitsItsLength() {
        CscreenSettings.Input s = CscreenSettings.input("{\"kind\":\"text\",\"key\":\" name \",\"initial\":\"abcdefgh\",\"max_length\":4,\"multiline\":true}");
        assertThat(s.kind()).isEqualTo("text");
        assertThat(s.key()).isEqualTo("name");
        assertThat(s.initial()).isEqualTo("abcd");
        assertThat(s.maxLength()).isEqualTo(4);
        assertThat(s.multiline()).isTrue();
        assertThat(s.labelVisible()).isTrue();
    }

    @Test
    void aCheckboxReadsItsStartAndItsTexts() {
        CscreenSettings.Input on = CscreenSettings.input("{\"kind\":\"boolean\",\"key\":\"agree\",\"initial\":true,\"on_true\":\"yes\"}");
        assertThat(on.initialOn()).isTrue();
        assertThat(on.onTrue()).isEqualTo("yes");
        assertThat(on.onFalse()).isEqualTo("false");
        assertThat(CscreenSettings.input("{\"kind\":\"boolean\",\"key\":\"agree\"}").initialOn()).isFalse();
    }

    @Test
    void aDropdownHasExactlyOnePreselectedOption() {
        CscreenSettings.Input none = CscreenSettings.input(
                "{\"kind\":\"option\",\"key\":\"k\",\"options\":[{\"id\":\"a\",\"label\":\"A\"},{\"id\":\"\"},{\"id\":\"a\"},{\"id\":\"b\"}]}");
        assertThat(none.options()).extracting(CscreenSettings.Option::id).containsExactly("a", "b"); // no id and a repeated id are dropped
        assertThat(none.options()).extracting(CscreenSettings.Option::initial).containsExactly(true, false);
        assertThat(none.options().get(1).label()).isEqualTo("b"); // no label: the id

        CscreenSettings.Input two = CscreenSettings.input(
                "{\"kind\":\"option\",\"key\":\"k\",\"options\":[{\"id\":\"a\"},{\"id\":\"b\",\"initial\":true},{\"id\":\"c\",\"initial\":true}]}");
        assertThat(two.options()).extracting(CscreenSettings.Option::initial).containsExactly(false, true, false);
    }

    @Test
    void aSlidersRangeIsOrderedAndItsStartInside() {
        CscreenSettings.Input s = CscreenSettings.input("{\"kind\":\"number\",\"key\":\"n\",\"start\":10,\"end\":1,\"initial\":50,\"step\":-2}");
        assertThat(s.start()).isEqualTo(1f);
        assertThat(s.end()).isEqualTo(10f);
        assertThat(s.initialNumber()).isEqualTo(10f);
        assertThat(s.step()).isEqualTo(0f); // no step: any value
        assertThat(CscreenSettings.input("{\"kind\":\"number\",\"key\":\"n\"}").initialNumber()).isEqualTo(0f);
    }

    @Test
    void aButtonReadsItsRoleAndAction() {
        CscreenSettings.Button plain = CscreenSettings.button(null);
        assertThat(plain.exit()).isFalse();
        assertThat(plain.action()).isEqualTo("commands");
        assertThat(plain.commands()).isEmpty();
        assertThat(plain.width()).isEqualTo(150);

        CscreenSettings.Button exit = CscreenSettings.button(
                "{\"role\":\"exit\",\"label\":\"Back\",\"action\":\"screen\",\"target\":\" menu \",\"commands\":[\"a\",\"b\"]}");
        assertThat(exit.exit()).isTrue();
        assertThat(exit.label()).isEqualTo("Back");
        assertThat(exit.action()).isEqualTo("screen");
        assertThat(exit.target()).isEqualTo("menu");
        assertThat(exit.commands()).containsExactly("a", "b");
        assertThat(CscreenSettings.button("{\"action\":\"launch\"}").action()).isEqualTo("commands");
    }
}
