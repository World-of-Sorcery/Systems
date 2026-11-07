package me.hektortm.woSSystems.systems.mail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.mail.model.MailIcon;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The mailbox's look, from the settings JSON the portal's Appearance page saves ({@code GET /v1/content/mail}).
 * Reading never fails: anything missing or unreadable gets its default; rows are kept to 3–6 and slots outside the
 * chest are dropped. The portal's {@code modules/mail/settings.ts} reads and writes the same keys.
 */
public record MailSettings(Box box, Letter letter, General general) {

    /** A button of the mailbox: where, how it looks (null: the default item), its name. */
    public record Button(int slot, @Nullable MailIcon item, String label) {}

    /**
     * The mailbox chest. {@code mailSlots}: where mails show, in order (newest first); {@code buttons}: prev, next,
     * claim_all, delete_read, close; {@code background}: a resource pack glyph id, empty for none.
     */
    public record Box(String title, int rows, List<Integer> mailSlots, @Nullable MailIcon filler, Map<String, Button> buttons,
                      String background, Map<String, String> colours) {}

    /** The letter (a dialog): which lines show, button names, the default icons of mail without its own. */
    public record Letter(boolean showSender, boolean showDate, boolean showItems, Map<String, String> labels,
                         @Nullable MailIcon unreadIcon, @Nullable MailIcon openedIcon) {}

    public record General(String timezone, String dateFormat) {}

    public static final String[] BUTTONS = {"prev", "claim_all", "close", "delete_read", "next"};
    /** Where each button sits in the last row by default (0 = its first slot). */
    private static final int[] BUTTON_OFFSETS = {0, 3, 4, 5, 8};
    private static final Map<String, String> BUTTON_LABELS = Map.of(
            "prev", "Previous page", "next", "Next page", "claim_all", "Claim all", "delete_read", "Delete read mail", "close", "Close");
    private static final Map<String, String> COLOURS = Map.of(
            "unclaimed", "&a", "claimed", "&7", "new", "&e", "read", "&7", "sender", "&f", "date", "&7");
    private static final Map<String, String> LABELS = Map.of("claim", "Claim", "delete", "Delete", "back", "Back");

    public static MailSettings parse(@Nullable String json) {
        JsonObject root = object(json);
        return new MailSettings(box(Json.object(root, "box")), letter(Json.object(root, "letter")), general(Json.object(root, "general")));
    }

    private static JsonObject object(@Nullable String json) {
        if (json == null || json.isBlank()) return new JsonObject();
        try {
            JsonElement e = JsonParser.parseString(json);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static int integer(JsonObject o, String name, int fallback) {
        try {
            return Json.integer(o, name, fallback);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static Box box(JsonObject o) {
        int rows = Math.max(3, Math.min(6, integer(o, "rows", 6)));
        int size = rows * 9;
        int lastRow = (rows - 1) * 9;
        List<Integer> slots = new ArrayList<>();
        if (o.has("mail_slots") && o.get("mail_slots").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("mail_slots")) {
                int slot = e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : -1;
                if (slot >= 0 && slot < size && !slots.contains(slot)) slots.add(slot);
            }
        } else {
            for (int i = 0; i < lastRow; i++) slots.add(i);
        }
        JsonObject given = Json.object(o, "buttons");
        Map<String, Button> buttons = new LinkedHashMap<>();
        for (int i = 0; i < BUTTONS.length; i++) {
            String name = BUTTONS[i];
            JsonObject b = Json.object(given, name);
            int slot = integer(b, "slot", lastRow + BUTTON_OFFSETS[i]);
            if (slot < 0 || slot >= size) continue;
            buttons.put(name, new Button(slot, MailIcon.of(Json.field(b, "item")), Json.str(b, "label", BUTTON_LABELS.get(name))));
        }
        return new Box(Json.str(o, "title", "Mailbox"), rows, List.copyOf(slots), MailIcon.of(Json.field(o, "filler")), buttons,
                Json.str(o, "background", ""), withDefaults(Json.object(o, "colours"), COLOURS));
    }

    private static Map<String, String> withDefaults(JsonObject given, Map<String, String> defaults) {
        Map<String, String> out = new LinkedHashMap<>(defaults);
        for (String key : defaults.keySet()) {
            String v = Json.str(given, key);
            if (v != null && !v.isBlank()) out.put(key, v);
        }
        return out;
    }

    private static Letter letter(JsonObject o) {
        return new Letter(bool(o, "show_sender"), bool(o, "show_date"), bool(o, "show_items"),
                withDefaults(Json.object(o, "labels"), LABELS),
                MailIcon.of(Json.field(o, "unread_icon")), MailIcon.of(Json.field(o, "opened_icon")));
    }

    private static boolean bool(JsonObject o, String name) {
        try {
            return Json.bool(o, name, true);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static General general(JsonObject o) {
        String tz = Json.str(o, "timezone", "");
        String format = Json.str(o, "date_format", "");
        return new General(tz.isBlank() ? "Europe/Berlin" : tz, format.isBlank() ? "dd.MM.yyyy HH:mm" : format);
    }
}
