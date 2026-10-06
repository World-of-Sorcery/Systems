package me.hektortm.woSSystems.systems.cscreens;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.content.Json;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * What a custom screen and its elements hold beyond their columns: the
 * settings JSON the portal saves, as typed values. Reading never fails:
 * anything missing or unreadable gets its default, and numbers are kept inside
 * what Minecraft accepts (a width outside 1–1024 would make the client refuse
 * the whole screen).
 */
public final class CscreenSettings {

    private static final int MAX_WIDTH = 1024;

    private CscreenSettings() {}

    /** A screen's own settings. {@code afterAction}: close, none or wait_for_response. */
    public record Screen(String externalTitle, boolean canCloseWithEscape, String afterAction, int columns, int buttonWidth,
                         List<String> screens) {}

    /** A body element: a text, or an item (a material or a custom item) with a description next to it. */
    public record Body(boolean item, String text, int width, boolean citem, String material, String citemId, int amount,
                       String description, boolean showDecorations, boolean showTooltip, int height) {}

    public record Option(String id, String label, boolean initial) {}

    /** An input. {@code kind}: text, boolean, option or number; the fields of the other kinds hold their defaults. */
    public record Input(String kind, String key, String label, boolean labelVisible, int width,
                        String initial, int maxLength, boolean multiline, int maxLines, int height,
                        boolean initialOn, String onTrue, String onFalse,
                        List<Option> options,
                        float start, float end, float step, float initialNumber, String labelFormat) {}

    /** A button. {@code action}: commands, screen, url, copy or close. */
    public record Button(boolean exit, String label, String tooltip, int width, String action, List<String> commands, String target) {}

    public static Screen screen(@Nullable String json) {
        JsonObject o = object(json);
        String after = Json.str(o, "after_action", "close");
        if (!after.equals("none") && !after.equals("wait_for_response")) after = "close";
        return new Screen(Json.str(o, "external_title", ""), bool(o, "can_close_with_escape", true), after,
                within(integer(o, "columns", 2), 1, 10), within(integer(o, "button_width", 150), 1, MAX_WIDTH), Json.strings(o, "screens"));
    }

    public static Body body(@Nullable String json) {
        JsonObject o = object(json);
        boolean item = "item".equals(Json.str(o, "kind", "text"));
        return new Body(item, Json.str(o, "text", ""), within(integer(o, "width", item ? 16 : 200), 1, item ? 256 : MAX_WIDTH),
                "citem".equals(Json.str(o, "item_kind", "item")), Json.str(o, "item", ""), Json.str(o, "citem", ""),
                within(integer(o, "amount", 1), 1, 99), Json.str(o, "description", ""),
                bool(o, "show_decorations", true), bool(o, "show_tooltip", true), within(integer(o, "height", 16), 1, 256));
    }

    public static Input input(@Nullable String json) {
        JsonObject o = object(json);
        String kind = Json.str(o, "kind", "text");
        int maxLength = within(integer(o, "max_length", 32), 1, 32767);
        String initial = Json.str(o, "initial", "");
        boolean number = kind.equals("number");

        float start = number(o, "start", 0), end = number(o, "end", 10);
        if (end < start) {
            float swap = start;
            start = end;
            end = swap;
        }
        float step = Math.max(0, number(o, "step", 1));
        float initialNumber = Math.max(start, Math.min(end, number ? number(o, "initial", start) : start));

        return new Input(kind, Json.str(o, "key", "").trim(), Json.str(o, "label", ""), bool(o, "label_visible", true),
                within(integer(o, "width", 200), 1, MAX_WIDTH),
                // The client refuses a start value longer than the field allows.
                number || initial.length() <= maxLength ? initial : initial.substring(0, maxLength), maxLength,
                bool(o, "multiline", false), within(integer(o, "max_lines", 4), 1, 100), within(integer(o, "height", 64), 1, 512),
                !number && !kind.equals("text") && bool(o, "initial", false), Json.str(o, "on_true", "true"), Json.str(o, "on_false", "false"),
                options(o), start, end, step, initialNumber, Json.str(o, "label_format", "options.generic_value"));
    }

    /** The options of a dropdown: without an id they are dropped, and exactly one is preselected (the first, if none is marked). */
    private static List<Option> options(JsonObject o) {
        List<Option> out = new ArrayList<>();
        boolean chosen = false;
        for (JsonElement el : Json.array(o, "options")) {
            if (!el.isJsonObject()) continue;
            JsonObject option = el.getAsJsonObject();
            String id = Json.str(option, "id", "").trim();
            if (id.isEmpty() || out.stream().anyMatch(known -> known.id().equals(id))) continue;
            boolean initial = !chosen && bool(option, "initial", false);
            chosen |= initial;
            out.add(new Option(id, Json.str(option, "label", id), initial));
        }
        if (!chosen && !out.isEmpty()) out.set(0, new Option(out.get(0).id(), out.get(0).label(), true));
        return out;
    }

    public static Button button(@Nullable String json) {
        JsonObject o = object(json);
        String action = Json.str(o, "action", "commands");
        if (!List.of("commands", "screen", "url", "copy", "close").contains(action)) action = "commands";
        return new Button("exit".equals(Json.str(o, "role", "normal")), Json.str(o, "label", ""), Json.str(o, "tooltip", ""),
                within(integer(o, "width", 150), 1, MAX_WIDTH), action, Json.strings(o, "commands"), Json.str(o, "target", "").trim());
    }

    // ── reading ─────────────────────────────────────────────────────────────────

    private static JsonObject object(@Nullable String json) {
        if (json == null || json.isBlank()) return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static int integer(JsonObject o, String name, int fallback) {
        return Math.round(number(o, name, fallback));
    }

    /** A number, also when it was saved as text ("12"); the fallback for anything else. */
    private static float number(JsonObject o, String name, float fallback) {
        JsonElement e = Json.field(o, name);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        try {
            float value = e.getAsFloat();
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject o, String name, boolean fallback) {
        JsonElement e = Json.field(o, name);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        if (e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        String text = e.getAsString();
        return text.equals("true") || (!text.equals("false") && fallback);
    }

    private static int within(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
