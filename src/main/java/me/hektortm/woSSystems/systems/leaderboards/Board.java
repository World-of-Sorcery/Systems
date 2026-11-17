package me.hektortm.woSSystems.systems.leaderboards;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A leaderboard as the API answers it: locked (frozen) or live, and its entries, best first. */
public record Board(boolean locked, List<Entry> entries) {
    public static final Board EMPTY = new Board(false, List.of());

    public record Entry(int rank, UUID uuid, String name, long value) {}

    /** The entry at this rank (1 is the best), or null when nobody holds it. */
    public @Nullable Entry entry(int rank) {
        for (Entry e : entries) if (e.rank() == rank) return e;
        return null;
    }

    /** The API's {@code {"ranks": [{uuid, rank, value}]}} as player → own rank; unreadable rows are left out. */
    public static java.util.Map<UUID, Integer> parseRanks(@Nullable JsonElement answer) {
        java.util.Map<UUID, Integer> out = new java.util.HashMap<>();
        if (answer == null || !answer.isJsonObject()) return out;
        for (JsonElement el : Json.array(answer.getAsJsonObject(), "ranks")) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            try {
                out.put(UUID.fromString(Json.str(r, "uuid")), Json.integer(r, "rank", 0));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // a broken row is skipped
            }
        }
        return out;
    }

    /** The API's answer as a board; anything unreadable is {@link #EMPTY} (an unreadable entry is left out). */
    public static Board parse(@Nullable JsonElement answer) {
        if (answer == null || !answer.isJsonObject()) return EMPTY;
        JsonObject o = answer.getAsJsonObject();
        List<Entry> entries = new ArrayList<>();
        for (JsonElement el : Json.array(o, "entries")) {
            if (!el.isJsonObject()) continue;
            JsonObject e = el.getAsJsonObject();
            try {
                entries.add(new Entry(Json.integer(e, "rank", 0), UUID.fromString(Json.str(e, "uuid")), Json.str(e, "name"), Json.lng(e, "value", 0)));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // a broken entry is skipped, the rest still shows
            }
        }
        return new Board(Json.bool(o, "locked", false), List.copyOf(entries));
    }
}
