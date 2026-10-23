package me.hektortm.woSSystems.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholdersTest {

    private static final Map<String, String> VALUES = Map.of(
            "player_name", "Hektor",
            "stats.amount:kills", "12",
            "citems.lore:wand", "&7A wand\n&7Of oak",
            "empty", "");

    private static final Function<Placeholders.Token, String> LOOKUP = token -> VALUES.get(token.raw());

    @Test
    void parsesTheThreeShapes() {
        assertThat(Placeholders.parse("server_name")).isEqualTo(new Placeholders.Token("server_name", "server_name", null, null));
        assertThat(Placeholders.parse("player_cosmetic.prefix")).isEqualTo(new Placeholders.Token("player_cosmetic.prefix", "player_cosmetic", "prefix", null));
        assertThat(Placeholders.parse("cooldowns.duration:daily-chest")).isEqualTo(new Placeholders.Token("cooldowns.duration:daily-chest", "cooldowns", "duration", "daily-chest"));
    }

    @Test
    void rejectsWhatIsNotAToken() {
        assertThat(Placeholders.parse("")).isNull();
        assertThat(Placeholders.parse("\"text\":\"hi\"")).isNull();
        assertThat(Placeholders.parse("two words")).isNull();
    }

    @Test
    void replacesKnownTokens() {
        assertThat(Placeholders.replace("&aHi {player_name}, kills: {stats.amount:kills}", LOOKUP)).isEqualTo("&aHi Hektor, kills: 12");
        assertThat(Placeholders.replace("[{empty}]", LOOKUP)).isEqualTo("[]");
    }

    @Test
    void leavesUnknownTokensAsWritten() {
        assertThat(Placeholders.replace("{typo} and {stats.amount:deaths}", LOOKUP)).isEqualTo("{typo} and {stats.amount:deaths}");
    }

    @Test
    void leavesJsonAlone() {
        String json = "tellraw @p {\"text\":\"{player_name}\",\"extra\":[{\"text\":\"!\"}]}";
        assertThat(Placeholders.replace(json, LOOKUP)).isEqualTo("tellraw @p {\"text\":\"Hektor\",\"extra\":[{\"text\":\"!\"}]}");
    }

    @Test
    void handlesUnclosedAndEmptyInput() {
        assertThat(Placeholders.replace("{player_name", LOOKUP)).isEqualTo("{player_name");
        assertThat(Placeholders.replace("a { b {player_name}", LOOKUP)).isEqualTo("a { b Hektor");
        assertThat(Placeholders.replace("no tokens", LOOKUP)).isEqualTo("no tokens");
        assertThat(Placeholders.replace(null, LOOKUP)).isNull();
    }

    @Test
    void splitsMultiLineValuesIntoLines() {
        assertThat(Placeholders.replaceLines(List.of("&6Wand", "{citems.lore:wand}", "{player_name}"), LOOKUP))
                .containsExactly("&6Wand", "&7A wand", "&7Of oak", "Hektor");
    }

    @Test
    void glyphAndShiftTokens() {
        Placeholders.Token glyph = Placeholders.parse("glyph:coin");
        assertThat(glyph.namespace()).isEqualTo("glyph");
        assertThat(glyph.id()).isEqualTo("coin");
        assertThat(glyph.isBare()).isFalse();
        assertThat(Placeholders.parse("shift:-8").id()).isEqualTo("-8");
        assertThat(Placeholders.parse("glyph:")).isNull();
    }

    @Test
    void oldTokensUnchanged() {
        assertThat(Placeholders.parse("player_name")).isEqualTo(new Placeholders.Token("player_name", "player_name", null, null));
        assertThat(Placeholders.parse("stats.amount:x")).isEqualTo(new Placeholders.Token("stats.amount:x", "stats", "amount", "x"));
        assertThat(Placeholders.parse("citems.name:wos:x")).isEqualTo(new Placeholders.Token("citems.name:wos:x", "citems", "name", "wos:x"));
        assertThat(Placeholders.parse("coin")).isEqualTo(new Placeholders.Token("coin", "coin", null, null));
    }

    @Test
    void jsonLeftAlone() {
        assertThat(Placeholders.replace("{\"text\":\"a\"}", token -> "X")).isEqualTo("{\"text\":\"a\"}");
    }
}
