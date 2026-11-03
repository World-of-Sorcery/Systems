package me.hektortm.woSSystems.systems.magic.cast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The notes of a Song node (2026-11-03): which sound plays at which tick, at which pitch.
 * A note is {@code {sound, note 0–24, volume 0–2, wait ticks}} as the API checks it; this
 * only reads, so a bad entry (no sound) is skipped but its wait still counts.
 */
final class Song {

    private Song() { }

    /** One sound of the song: {@code tick} counted from the song's start. */
    record Note(String sound, float pitch, float volume, int tick) { }

    /** A note block's pitch: 12 is the sound's own pitch, each step a semitone, 0 and 24 an octave down / up. */
    static float pitch(int note) {
        return (float) Math.pow(2, (Math.max(0, Math.min(24, note)) - 12) / 12.0);
    }

    /** Every note of {@code list} with the tick it sounds at, played {@code loops} times in a row. */
    static List<Note> schedule(JsonArray list, int loops) {
        List<Note> once = new ArrayList<>();
        int tick = 0;
        for (JsonElement e : list) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String sound = string(o, "sound");
            if (!sound.isBlank()) {
                once.add(new Note(sound, pitch((int) number(o, "note", 12)), (float) Math.max(0, Math.min(2, number(o, "volume", 1))), tick));
            }
            tick += (int) Math.max(0, Math.min(200, number(o, "wait", 0)));
        }
        List<Note> out = new ArrayList<>();
        for (int play = 0; play < Math.max(1, loops); play++) {
            for (Note n : once) out.add(new Note(n.sound(), n.pitch(), n.volume(), n.tick() + play * tick));
        }
        return out;
    }

    private static String string(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() ? v.getAsString() : "";
    }

    private static double number(JsonObject o, String key, double fallback) {
        JsonElement v = o.get(key);
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsDouble() : fallback;
    }
}
