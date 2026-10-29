package me.hektortm.woSSystems.systems.magic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.systems.magic.model.SpellTierTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** XP and levels per spell (magic spec §3.5). */
class SpellLevelsTest {

    /** Levels 1–4: 10 XP to level 2, 25 to 3, 40 to 4. */
    static final SpellDef SPELL = SpellTierTest.spell("{\"levels\":[{\"xp\":10},{\"xp\":25},{\"xp\":40}]}",
            "{\"on_cast\":[{\"type\":\"projectile\"}]}");

    @Test
    void xpBelowTheNextLevelIsKept() {
        assertThat(SpellLevels.add(1, 2, 5, SPELL)).isEqualTo(new SpellLevels.Gain(1, 7, List.of()));
    }

    @Test
    void xpCrossingSeveralLevelsRunsEach() {
        assertThat(SpellLevels.add(1, 0, 40, SPELL)).isEqualTo(new SpellLevels.Gain(3, 5, List.of(2, 3)));
    }

    @Test
    void xpAtTheMaxIsNotKept() {
        assertThat(SpellLevels.add(3, 30, 50, SPELL)).isEqualTo(new SpellLevels.Gain(4, 0, List.of(4)));
        assertThat(SpellLevels.add(4, 0, 50, SPELL)).isEqualTo(new SpellLevels.Gain(4, 0, List.of()));
    }

    @Test
    void loweredMax() {
        // Saved at level 6 before staff removed rows: treated as the max, the saved level stays.
        assertThat(SpellLevels.add(6, 3, 50, SPELL)).isEqualTo(new SpellLevels.Gain(6, 0, List.of()));
        assertThat(SPELL.at(6).level()).isEqualTo(4);
    }

    @Test
    void aSpellWithoutLevelsNeverLevels() {
        SpellDef plain = SpellTierTest.spell("{}", "{\"on_cast\":[]}");
        assertThat(SpellLevels.add(1, 0, 100, plain)).isEqualTo(new SpellLevels.Gain(1, 0, List.of()));
    }

    @Test
    void takingXpNeverLowersTheLevel() {
        assertThat(SpellLevels.take(3, 4, 10)).isEqualTo(new SpellLevels.Gain(3, 0, List.of()));
        assertThat(SpellLevels.take(3, 14, 10)).isEqualTo(new SpellLevels.Gain(3, 4, List.of()));
    }

    @Test
    void validLevels() {
        assertThat(SpellLevels.validLevel(1, SPELL)).isTrue();
        assertThat(SpellLevels.validLevel(4, SPELL)).isTrue();
        assertThat(SpellLevels.validLevel(0, SPELL)).isFalse();
        assertThat(SpellLevels.validLevel(5, SPELL)).isFalse();
    }

    @Test
    void progressIsReadSavedAndResetByForgetting() {
        JsonObject progress = JsonParser.parseString("{\"ember\":{\"level\":3,\"xp\":12.5}}").getAsJsonObject();
        MagicData d = MagicData.fromSession(null, List.of("ember"), progress, MagicDataTest.START);
        assertThat(d.progress("ember")).isEqualTo(new MagicData.Progress(3, 12.5));
        assertThat(d.progress("frost")).isEqualTo(new MagicData.Progress(1, 0)); // unknown: the start
        d.setProgress("ember", new MagicData.Progress(4, 0));
        assertThat(d.isDirty()).isTrue();
        JsonObject body = JsonParser.parseString(new Gson().toJson(d.body())).getAsJsonObject();
        assertThat(body.getAsJsonObject("spell_progress").getAsJsonObject("ember").get("level").getAsInt()).isEqualTo(4);
        d.forget("ember");
        d.learn("ember");
        assertThat(d.progress("ember")).isEqualTo(new MagicData.Progress(1, 0));
        assertThat(JsonParser.parseString(new Gson().toJson(d.body())).getAsJsonObject()
                .getAsJsonObject("spell_progress").getAsJsonObject("ember").get("xp").getAsDouble()).isZero();
    }
}
