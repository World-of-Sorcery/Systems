package me.hektortm.woSSystems.systems.citems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.systems.magic.MagicAttributes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The citem data's wand flag and magic component (magic spec §6.2, §6.3), as CitemBuilder keeps them on the item. */
class CitemMagicTest {

    static JsonObject data(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void theWandFlag() {
        assertThat(CitemRules.isWand(data("{\"wand\":true}"))).isTrue();
        assertThat(CitemRules.isWand(data("{\"wand\":false}"))).isFalse();
        assertThat(CitemRules.isWand(data("{\"wand\":\"yes\"}"))).isFalse();
        assertThat(CitemRules.isWand(data("{}"))).isFalse();
    }

    @Test
    void theMagicComponentIsKeptAsJson() {
        String json = CitemRules.magicComponent(data("{\"magic\":{\"slot\":\"armor\",\"max_mana\":30}}"));
        assertThat(MagicAttributes.parse(json, "armor")).isEqualTo(new MagicAttributes(30, 0, 0, 0));
        assertThat(CitemRules.magicComponent(data("{\"magic\":null}"))).isNull();
        assertThat(CitemRules.magicComponent(data("{\"magic\":[]}"))).isNull();
        assertThat(CitemRules.magicComponent(data("{}"))).isNull();
    }
}
