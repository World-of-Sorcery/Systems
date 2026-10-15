package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a run looked like when it passed a Checkpoint node: a later failure
 * puts it back to this (see {@link QuestRun#rewind}). Saved inside the run
 * document as {@code checkpoint}.
 */
public final class QuestCheckpoint {

    final String node;
    final int score;
    @Nullable final String anchor;
    final Map<String, Integer> given;
    final Map<String, Integer> taken;
    final Set<String> displays;

    QuestCheckpoint(String node, int score, @Nullable String anchor,
                    Map<String, Integer> given, Map<String, Integer> taken, Set<String> displays) {
        this.node = node;
        this.score = score;
        this.anchor = anchor;
        this.given = new LinkedHashMap<>(given);
        this.taken = new LinkedHashMap<>(taken);
        this.displays = new LinkedHashSet<>(displays);
    }

    public String node() { return node; }

    /**
     * What a rewind hands back: custom items to take from the player (given
     * since the checkpoint), ingredients to give back (taken since), and the
     * score difference to undo.
     */
    public record Rewind(String node, Map<String, Integer> takeBack, Map<String, Integer> giveBack, int scoreUndone) {}

    /** now − then per item, only what grew. */
    static Map<String, Integer> since(Map<String, Integer> then, Map<String, Integer> now) {
        Map<String, Integer> out = new LinkedHashMap<>();
        now.forEach((id, n) -> {
            int more = n - then.getOrDefault(id, 0);
            if (more > 0) out.put(id, more);
        });
        return out;
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("node", node);
        o.addProperty("score", score);
        if (anchor != null) o.addProperty("anchor", anchor);
        o.add("given", ints(given));
        o.add("taken", ints(taken));
        JsonArray d = new JsonArray();
        displays.forEach(d::add);
        o.add("displays", d);
        return o;
    }

    @Nullable
    static QuestCheckpoint fromJson(JsonObject o) {
        String node = Json.str(o, "node", "");
        if (node.isEmpty()) return null;
        Map<String, Integer> given = new LinkedHashMap<>();
        Map<String, Integer> taken = new LinkedHashMap<>();
        readInts(Json.object(o, "given"), given);
        readInts(Json.object(o, "taken"), taken);
        return new QuestCheckpoint(node, Json.integer(o, "score", 0), Json.str(o, "anchor"), given, taken,
                new LinkedHashSet<>(Json.strings(o, "displays")));
    }

    private static JsonObject ints(Map<String, Integer> m) {
        JsonObject o = new JsonObject();
        m.forEach(o::addProperty);
        return o;
    }

    private static void readInts(JsonObject o, Map<String, Integer> into) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) into.put(e.getKey(), e.getValue().getAsInt());
    }
}
