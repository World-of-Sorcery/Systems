package me.hektortm.woSSystems.database.dao;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlyphDAOTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void mapsAGlyph() {
        var g = GlyphDAO.map(json("{\"id\":\"bg\",\"kind\":\"background\",\"codepoint\":59648,\"width\":176,\"rows\":6,\"offset_x\":3}"));
        assertThat(g.character()).isEqualTo("");
        assertThat(g.offsetX()).isEqualTo(3);
        assertThat(g.sheet()).isNull();
        assertThat(g.letter()).isNull();
    }

    @Test
    void mapsASheetCell() {
        var g = GlyphDAO.map(json("{\"id\":\"ft_a\",\"kind\":\"icon\",\"codepoint\":59648,\"sheet\":\"ft\",\"letter\":\"A\",\"letters_touch\":true}"));
        assertThat(g.sheet()).isEqualTo("ft");
        assertThat(g.letter()).isEqualTo("A");
        assertThat(g.lettersTouch()).isTrue();
    }

    @Test
    void aGlyphWithoutAUsableCodepointIsRefused() {
        assertThatThrownBy(() -> GlyphDAO.map(json("{\"id\":\"a\"}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GlyphDAO.map(json("{\"id\":\"a\",\"codepoint\":-3}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GlyphDAO.map(json("{\"id\":\"a\",\"codepoint\":1114112}"))).isInstanceOf(IllegalArgumentException.class);
    }
}
