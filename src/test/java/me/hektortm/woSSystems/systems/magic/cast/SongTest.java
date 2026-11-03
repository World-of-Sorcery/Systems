package me.hektortm.woSSystems.systems.magic.cast;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SongTest {

    private static JsonArray notes(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    @Test
    void notePitchesAreSemitonesAroundTwelve() {
        assertThat(Song.pitch(12)).isEqualTo(1f);
        assertThat(Song.pitch(0)).isCloseTo(0.5f, within(1e-6f));
        assertThat(Song.pitch(24)).isCloseTo(2f, within(1e-6f));
        assertThat(Song.pitch(18)).isCloseTo((float) Math.sqrt(2), within(1e-6f));
        assertThat(Song.pitch(-3)).isCloseTo(0.5f, within(1e-6f)); // clamped like the API's range
    }

    @Test
    void waitsPlaceTheNextNoteAndZeroMakesAChord() {
        List<Song.Note> s = Song.schedule(notes("""
                [{"sound":"minecraft:block.note_block.harp","note":12,"wait":4},
                 {"sound":"minecraft:block.note_block.bass","note":0},
                 {"sound":"minecraft:block.note_block.bell","volume":0.5,"wait":2},
                 {"sound":"minecraft:block.note_block.hat"}]"""), 1);
        assertThat(s).extracting(Song.Note::tick).containsExactly(0, 4, 4, 6);
        assertThat(s.get(1).pitch()).isCloseTo(0.5f, within(1e-6f));
        assertThat(s.get(2).volume()).isEqualTo(0.5f);
        assertThat(s.get(3).volume()).isEqualTo(1f);
        assertThat(s.get(3).pitch()).isEqualTo(1f);
    }

    @Test
    void loopsRepeatAfterTheWholeSong() {
        List<Song.Note> s = Song.schedule(notes("""
                [{"sound":"a","wait":3},{"sound":"b","wait":5}]"""), 3);
        assertThat(s).extracting(Song.Note::tick).containsExactly(0, 3, 8, 11, 16, 19);
        assertThat(s).extracting(Song.Note::sound).containsExactly("a", "b", "a", "b", "a", "b");
    }

    @Test
    void anEntryWithoutSoundIsSkippedButKeepsItsWait() {
        List<Song.Note> s = Song.schedule(notes("""
                [{"note":5,"wait":10},7,{"sound":"x","wait":500},{"sound":"y"}]"""), 1);
        assertThat(s).extracting(Song.Note::sound).containsExactly("x", "y");
        assertThat(s).extracting(Song.Note::tick).containsExactly(10, 210); // a wait is capped at 200 ticks
    }
}
