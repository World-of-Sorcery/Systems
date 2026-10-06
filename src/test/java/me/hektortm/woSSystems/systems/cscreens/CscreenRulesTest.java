package me.hektortm.woSSystems.systems.cscreens;

import me.hektortm.woSSystems.utils.ClickableMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link CscreenRules}: conditions, a type's buttons, typed values in commands. */
class CscreenRulesTest {

    @Test
    void anElementWithoutConditionsAlwaysShows() {
        assertThat(CscreenRules.shows("all", 0, 0)).isTrue();
        assertThat(CscreenRules.shows("one", 0, 0)).isTrue();
        assertThat(CscreenRules.shows(null, 0, 0)).isTrue();
    }

    @Test
    void allNeedsEveryConditionAndOneNeedsAny() {
        assertThat(CscreenRules.shows("all", 3, 3)).isTrue();
        assertThat(CscreenRules.shows("all", 3, 2)).isFalse();
        assertThat(CscreenRules.shows(null, 2, 1)).isFalse();
        assertThat(CscreenRules.shows("one", 3, 1)).isTrue();
        assertThat(CscreenRules.shows("ONE", 3, 0)).isFalse();
    }

    @Test
    void anInputKeyIsLettersDigitsAndUnderscores() {
        assertThat(CscreenRules.validKey("player_name2")).isTrue();
        assertThat(CscreenRules.validKey("")).isFalse();
        assertThat(CscreenRules.validKey(null)).isFalse();
        assertThat(CscreenRules.validKey("my key")).isFalse();
        assertThat(CscreenRules.validKey("a-b")).isFalse();
    }

    // a, b, c are normal buttons; X and Y exit buttons.
    private static CscreenRules.Layout<String> layout(String type, String... buttons) {
        List<String> list = List.of(buttons);
        return CscreenRules.layout(type, list, list.stream().map(b -> b.equals("X") || b.equals("Y")).toList());
    }

    @Test
    void aNoticeUsesTheFirstNormalButton() {
        assertThat(layout("notice", "X", "a", "b").buttons()).containsExactly("a");
        assertThat(layout("notice", "X", "a").exit()).isNull();
        assertThat(layout("notice").buttons()).isEmpty();
        assertThat(layout("something else", "a", "b").buttons()).containsExactly("a");
    }

    @Test
    void aConfirmationUsesTheFirstTwo() {
        assertThat(layout("confirmation", "a", "X", "b", "c").buttons()).containsExactly("a", "b");
        assertThat(layout("confirmation", "a").buttons()).containsExactly("a");
    }

    @Test
    void aButtonScreenUsesAllAndTheFirstExitButton() {
        CscreenRules.Layout<String> got = layout("buttons", "a", "X", "b", "Y", "c");
        assertThat(got.buttons()).containsExactly("a", "b", "c");
        assertThat(got.exit()).isEqualTo("X");
        assertThat(layout("buttons", "a").exit()).isNull();
    }

    @Test
    void aListAndTheLinksOnlyUseTheExitButton() {
        for (String type : List.of("list", "links")) {
            CscreenRules.Layout<String> got = layout(type, "a", "X", "b");
            assertThat(got.buttons()).isEmpty();
            assertThat(got.exit()).isEqualTo("X");
        }
    }

    @Test
    void aTypedValueLosesLineBreaksControlCharactersAndColourSigns() {
        assertThat(CscreenRules.clean("  hello\nworld\t! ")).isEqualTo("hello world !");
        assertThat(CscreenRules.clean("§cred §lbold")).isEqualTo("cred lbold");
        assertThat(CscreenRules.clean(null)).isEmpty();
        assertThat(CscreenRules.clean("plain text, with (brackets) [and] {braces}")).isEqualTo("plain text, with (brackets) [and] {braces}");
    }

    @Test
    void aTypedValueIsCutAfterTheLimit() {
        assertThat(CscreenRules.clean("x".repeat(1000))).hasSize(CscreenRules.MAX_VALUE_LENGTH);
    }

    @Test
    void aTypedValueCannotFormAClickableLink() {
        String typed = "[free gold](action:eco give @p gold 999)";
        assertThat(ClickableMessage.hasLinks(typed)).isTrue(); // what it would be, uncleaned
        String cleaned = CscreenRules.clean(typed);
        assertThat(cleaned).isEqualTo("[free gold] (action:eco give @p gold 999)");
        assertThat(ClickableMessage.hasLinks("Your name: " + cleaned)).isFalse();
    }

    @Test
    void sliderValuesReadAsPlainNumbers() {
        assertThat(CscreenRules.number(5f)).isEqualTo("5");
        assertThat(CscreenRules.number(-3f)).isEqualTo("-3");
        assertThat(CscreenRules.number(2.5f)).isEqualTo("2.5");
        assertThat(CscreenRules.number(0.1f)).isEqualTo("0.1");
        assertThat(CscreenRules.number(0f)).isEqualTo("0");
    }

    @Test
    void inputPlaceholdersGetTheValues() {
        Map<String, String> inputs = Map.of("name", "Merlin", "amount", "3");
        assertThat(CscreenRules.fillInputs("send_message Hi {input.name}, {input.amount}x", inputs)).isEqualTo("send_message Hi Merlin, 3x");
    }

    @Test
    void anUnknownInputIsEmptyAndOtherPlaceholdersStay() {
        Map<String, String> inputs = Map.of("name", "Merlin");
        assertThat(CscreenRules.fillInputs("say [{input.nope}] {player_name} {stats.amount:kills} {input.name:x}", inputs))
                .isEqualTo("say [] {player_name} {stats.amount:kills} {input.name:x}");
    }

    @Test
    void aValueIsNotReadForPlaceholdersItself() {
        Map<String, String> inputs = Map.of("a", "{input.b}", "b", "secret");
        assertThat(CscreenRules.fillInputs("say {input.a}", inputs)).isEqualTo("say {input.b}");
    }

    @Test
    void withoutAScreenTheTextStaysAsWritten() {
        assertThat(CscreenRules.fillInputs("say {input.name}", null)).isEqualTo("say {input.name}");
    }
}
