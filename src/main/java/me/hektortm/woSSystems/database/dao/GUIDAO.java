package me.hektortm.woSSystems.database.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentRegistry;
import me.hektortm.woSSystems.systems.guis.GuiRules;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.utils.model.BackgroundRule;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.GUI;
import me.hektortm.woSSystems.utils.model.GUICheck;
import me.hektortm.woSSystems.utils.model.GUIItemBehaviour;
import me.hektortm.woSSystems.utils.model.GUIPage;
import me.hektortm.woSSystems.utils.model.GUISlot;
import me.hektortm.woSSystems.utils.model.GUISlotConfig;
import me.hektortm.woSSystems.utils.types.CheckType;
import me.hektortm.wosCore.api.WosApi;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * GUI definitions ({@code /v1/content/guis/{id}}). One API call returns the whole
 * GUI — pages, slots, slot configs and their conditions — which is assembled into
 * the {@link GUI} model once and cached.
 */
public class GUIDAO {
    private final ContentStore<GUI> store;
    private final ConditionDAO conditionDAO;
    private final Logger log;

    public GUIDAO(ContentRegistry registry, WosApi api, ConditionDAO conditionDAO, Logger log) {
        this.log = log;
        this.conditionDAO = conditionDAO;
        this.store = registry.register(new ContentStore<>("guis", "GUI",
                ApiSource.tree(api, "/v1/content/guis", this::map, log)));
    }

    /** The GUI, or {@code null} if it does not exist. */
    public GUI getGUIbyId(String id) {
        return store.get(id);
    }

    // ── tree → model ────────────────────────────────────────────────────────

    private GUI map(JsonObject tree) {
        JsonObject g = Json.object(tree, "gui");
        String guiId = Json.str(g, "id");

        conditionDAO.replaceChildren(java.util.Set.of("guislot", "guibg"), guiId, Json.array(tree, "conditions"));

        // conditions keyed by "<gui>:<page>:<slot>:<config>"
        Map<String, List<Condition>> conditions = new HashMap<>();
        for (JsonElement el : Json.array(tree, "conditions")) {
            JsonObject c = el.getAsJsonObject();
            conditions.computeIfAbsent(Json.str(c, "type_id"), k -> new ArrayList<>())
                    .add(new Condition(Json.str(c, "condition_key"), Json.str(c, "value"), Json.str(c, "parameter")));
        }

        // configs grouped by (page, slot)
        Map<String, List<GUISlotConfig>> configs = new HashMap<>();
        for (JsonElement el : Json.array(tree, "configs")) {
            JsonObject c = el.getAsJsonObject();
            int page = Json.integer(c, "page_id", 0);
            int slot = Json.integer(c, "slot_id", 0);
            String configId = Json.str(c, "config_id");
            String material = Json.str(c, "material");
            String displayName = Json.str(c, "display_name");
            String lore = Json.str(c, "lore"); // JSON array text; GUIManager.parseLore reads it
            boolean enchanted = Json.bool(c, "enchanted", false);
            List<Condition> conds = conditions.getOrDefault(guiId + ":" + page + ":" + slot + ":" + configId, List.of());

            configs.computeIfAbsent(page + ":" + slot, k -> new ArrayList<>()).add(new GUISlotConfig(
                    guiId, page, slot, configId,
                    Json.str(c, "matchtype"),
                    Json.integer(c, "amount", 1),
                    Json.bool(c, "visible", true),
                    material, displayName, lore,
                    Json.str(c, "model"),
                    Json.str(c, "color"),
                    Json.str(c, "tooltip"),
                    enchanted,
                    buildItemStack(material, displayName, Json.strings(c, "lore"), enchanted),
                    Json.strings(c, "global_actions"),
                    Json.strings(c, "right_actions"),
                    Json.strings(c, "left_actions"),
                    Json.bool(c, "confirm", false),
                    Json.str(c, "sound"),
                    buildChecks(guiId, Json.array(c, "checks")),
                    conds,
                    behaviour(c)));
        }

        Map<Integer, List<GUISlot>> slots = new HashMap<>();
        for (JsonElement el : Json.array(tree, "slots")) {
            JsonObject s = el.getAsJsonObject();
            int page = Json.integer(s, "page_id", 0);
            int slot = Json.integer(s, "slot_id", 0);
            slots.computeIfAbsent(page, k -> new ArrayList<>()).add(new GUISlot(
                    guiId, page, slot, Json.bool(s, "active", true),
                    configs.getOrDefault(page + ":" + slot, new ArrayList<>())));
        }

        List<GUIPage> pages = new ArrayList<>();
        for (JsonElement el : Json.array(tree, "pages")) {
            int page = Json.integer(el.getAsJsonObject(), "page_id", 0);
            pages.add(new GUIPage(guiId, page, slots.getOrDefault(page, new ArrayList<>())));
        }

        return new GUI(guiId,
                Json.integer(g, "size", 1),
                Json.str(g, "title"),
                Json.str(g, "type"),
                pages,
                Json.strings(g, "open_actions"),
                Json.strings(g, "close_actions"),
                Json.strings(g, "cooldown_actions"),
                Json.str(g, "post_use", "stay"),
                Json.str(g, "post_use_target"),
                // Absent (an API that doesn't know it yet): the page turn. Empty: none.
                Json.str(g, "page_sound", GuiRules.PAGE_TURN_SOUND),
                blankToNull(Json.str(g, "background")),
                backgroundRules(guiId, Json.array(g, "background_rules"), conditions));
    }

    /** The background rules of a GUI, each with its {@code guibg} conditions ("<gui>:<rule id>"). */
    private static List<BackgroundRule> backgroundRules(String guiId, JsonArray arr, Map<String, List<Condition>> conditions) {
        List<BackgroundRule> rules = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            String glyph = blankToNull(Json.str(r, "background"));
            if (glyph == null) continue;
            int id = Json.integer(r, "id", 0);
            rules.add(new BackgroundRule(id, glyph, Json.str(r, "matchtype", "all"),
                    conditions.getOrDefault(guiId + ":" + id, List.of())));
        }
        return rules;
    }

    /** A config's cost, cooldown, trade, extra click types and post-use (defaults when absent). */
    static GUIItemBehaviour behaviour(JsonObject c) {
        return new GUIItemBehaviour(
                blankToNull(Json.str(c, "head_texture")),
                blankToNull(Json.str(c, "citem_id")),
                Json.bool(c, "citem_name", true),
                Json.str(c, "citem_lore", "citem"),
                blankToNull(Json.str(c, "cost_currency")),
                Json.integer(c, "cost_amount", 0),
                Json.bool(c, "show_cost", false),
                blankToNull(Json.str(c, "cooldown_id")),
                Json.strings(c, "cooldown_actions"),
                Json.strings(c, "shift_left_actions"),
                Json.strings(c, "shift_right_actions"),
                Json.strings(c, "drop_actions"),
                trade(Json.object(c, "trade")),
                Json.bool(c, "clickable", true),
                Json.str(c, "post_use", "default"),
                blankToNull(Json.str(c, "post_use_target")));
    }

    static GUIItemBehaviour.Trade trade(JsonObject t) {
        return new GUIItemBehaviour.Trade(tradeEntries(Json.array(t, "take")), tradeEntries(Json.array(t, "give")),
                Json.bool(t, "show", false), Json.bool(t, "show_give", false));
    }

    private static List<GUIItemBehaviour.Entry> tradeEntries(JsonArray arr) {
        List<GUIItemBehaviour.Entry> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject e = el.getAsJsonObject();
            String type = Json.str(e, "type");
            String id = Json.str(e, "id");
            int amount = Json.integer(e, "amount", 0);
            if (type != null && id != null && !id.isBlank() && amount > 0) out.add(new GUIItemBehaviour.Entry(type, id, amount));
        }
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private List<GUICheck> buildChecks(String guiId, JsonArray arr) {
        List<GUICheck> checks = new ArrayList<>();
        for (JsonElement el : arr) {
            try {
                JsonObject obj = el.isJsonObject() ? el.getAsJsonObject() : JsonParser.parseString(el.getAsString()).getAsJsonObject();
                CheckType type = CheckType.valueOf(obj.get("type").getAsString().toUpperCase());
                String id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsString() : null;
                checks.add(new GUICheck(type, id, obj.get("amount").getAsInt()));
            } catch (Exception e) {
                log.warning("GUIDAO: " + guiId + ": skipped invalid check " + el + " — " + e.getMessage());
            }
        }
        return checks;
    }

    private static ItemStack buildItemStack(String materialName, String displayName, List<String> lore, boolean enchanted) {
        Material material = materialName != null ? Material.getMaterial(materialName.toUpperCase()) : null;
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (displayName != null && !displayName.isEmpty()) meta.setDisplayName(displayName);
        if (!lore.isEmpty()) meta.setLore(lore);
        if (enchanted) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        item.setItemMeta(meta);
        return item;
    }
}
