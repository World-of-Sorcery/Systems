package me.hektortm.woSSystems.systems.magic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.systems.magic.model.MagicSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MagicDataTest {

    static final MagicSettings START = MagicSettings.parse(JsonParser.parseString(
            "{\"start_mana\":40,\"start_max_mana\":120,\"start_mana_regen\":3}").getAsJsonObject());

    @Test
    void aNewPlayerStartsFromTheSettings() {
        MagicData d = MagicData.fromSession(null, List.of(), START);
        assertThat(d.mana()).isEqualTo(40);
        assertThat(d.baseMax()).isEqualTo(120);
        assertThat(d.baseRegen()).isEqualTo(3);
        assertThat(d.wand()).isNull();
        assertThat(d.loadout()).hasSize(16).containsOnly("");
        assertThat(d.isDirty()).isFalse();
    }

    @Test
    void readsTheSession() {
        JsonObject magic = JsonParser.parseString("{\"mana\":5.5,\"max_mana\":100,\"mana_regen\":2,"
                + "\"wand\":{\"color\":\"#A1B2C3\",\"skin\":\"oak\"},\"loadout\":[\"ember\",\"\",\"frost\"]}").getAsJsonObject();
        MagicData d = MagicData.fromSession(magic, List.of("ember", "frost"), START);
        assertThat(d.mana()).isEqualTo(5.5);
        assertThat(d.wand()).isEqualTo(new MagicData.Wand("#A1B2C3", "oak"));
        assertThat(d.loadout()[2]).isEqualTo("frost");
        assertThat(d.loadout()[15]).isEmpty();
        assertThat(d.spells()).containsExactly("ember", "frost");
    }

    @Test
    void learningFillsTheFirstFreeSlotAndForgettingClearsIt() {
        MagicData d = MagicData.fromSession(null, List.of(), START);
        assertThat(d.learn("ember")).isTrue();
        assertThat(d.learn("ember")).isFalse();
        assertThat(d.learn("frost")).isTrue();
        assertThat(d.loadout()[0]).isEqualTo("ember");
        assertThat(d.loadout()[1]).isEqualTo("frost");
        d.select(0, 1);
        assertThat(d.selected()).isEqualTo("frost");
        assertThat(d.forget("frost")).isTrue();
        assertThat(d.loadout()[1]).isEmpty();
        assertThat(d.selected()).isNull();
        assertThat(d.isDirty()).isTrue();
    }

    @Test
    void theBodyIsWhatTheApiTakes() {
        MagicData d = MagicData.fromSession(null, List.of(), START);
        d.learn("ember");
        String json = new Gson().toJson(d.body());
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        assertThat(o.get("mana").getAsDouble()).isEqualTo(40);
        assertThat(o.get("max_mana").getAsDouble()).isEqualTo(120);
        assertThat(o.get("mana_regen").getAsDouble()).isEqualTo(3);
        // Gson leaves a null out; wos-api reads a missing wand as "no wand".
        assertThat(o.get("wand") == null || o.get("wand").isJsonNull()).isTrue();
        assertThat(o.getAsJsonArray("loadout")).hasSize(16);
        d.setWand(new MagicData.Wand("#010203", ""));
        o = JsonParser.parseString(new Gson().toJson(d.body())).getAsJsonObject();
        assertThat(o.getAsJsonObject("wand").get("color").getAsString()).isEqualTo("#010203");
    }

    @Test
    void manaFollowsTheAttributes() {
        MagicData d = MagicData.fromSession(null, List.of(), START);
        d.setMana(120);
        d.setAttributes(new MagicAttributes(-50, 1, 0, 0));
        assertThat(d.max()).isEqualTo(70);
        assertThat(d.mana()).isEqualTo(70); // cut to the new max
        assertThat(d.regen()).isEqualTo(4);
    }
}
