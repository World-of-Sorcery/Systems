package me.hektortm.woSSystems.systems.citems;

import com.google.gson.*;
import me.hektortm.woSSystems.utils.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.components.*;
import org.bukkit.inventory.meta.trim.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import javax.naming.Name;
import java.net.URI;
import java.util.*;

/**
 * Converts a CitemWebData JSON object into a Bukkit ItemStack.
 * Targets Paper 1.21.x
 */
public final class CitemBuilder {

    private static final String NS = "wos";

    public static final NamespacedKey KEY_LORE_PAGES = new NamespacedKey(NS, "lore_pages");
    public static final NamespacedKey KEY_LORE_PAGE  = new NamespacedKey(NS, "lore_page");
    /** The display name as typed, on items whose name has placeholders. */
    public static final NamespacedKey KEY_NAME_TEMPLATE = new NamespacedKey(NS, "name_template");
    /** Set on items whose name and lore use the custom font. */
    public static final NamespacedKey KEY_CUSTOM_FONT = new NamespacedKey(NS, "custom_font");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Maps legacy §/& color codes to <#HEX> MiniMessage equivalents
    private static final Map<Character, String> LEGACY_TO_MM;
    static {
        Map<Character, String> m = new HashMap<>();
        m.put('0', "<#000000>"); m.put('1', "<#0000AA>"); m.put('2', "<#00AA00>");
        m.put('3', "<#00AAAA>"); m.put('4', "<#AA0000>"); m.put('5', "<#AA00AA>");
        m.put('6', "<#FFAA00>"); m.put('7', "<#AAAAAA>"); m.put('8', "<#555555>");
        m.put('9', "<#5555FF>"); m.put('a', "<#55FF55>"); m.put('b', "<#55FFFF>");
        m.put('c', "<#FF5555>"); m.put('d', "<#FF55FF>"); m.put('e', "<#FFFF55>");
        m.put('f', "<#FFFFFF>");
        m.put('l', "<bold>"); m.put('o', "<italic>"); m.put('m', "<strikethrough>");
        m.put('n', "<underlined>"); m.put('r', "<reset>");
        LEGACY_TO_MM = Collections.unmodifiableMap(m);
    }

    private CitemBuilder() {}

    // ── Entry points ──────────────────────────────────────────────────────────

    public static ItemStack build(String id, String rawJson) {
        return build(id, JsonParser.parseString(rawJson).getAsJsonObject());
    }

    public static ItemStack build(String id, JsonObject data) {
        // ── Material ─────────────────────────────────────────────────────────
        String matName = getString(data, "material", "PAPER");
        Material material = Material.matchMaterial(matName);
        if (material == null || material == Material.AIR) {
            Bukkit.getLogger().warning("[CitemBuilder] Unknown material: " + matName + ", falling back to PAPER");
            material = Material.PAPER;
        }

        legacyDyeColor(data);

        // Components without ItemMeta code of their own (equippable, glider,
        // use_cooldown, anything newer) are applied by the server's item parser.
        ItemStack item = base(id, material, data);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // ── Font ─────────────────────────────────────────────────────────────
        // The name and lore are stored as typed; the custom font is applied here.
        boolean font = getBoolean(data, "custom_font", false);
        if (font) {
            meta.getPersistentDataContainer().set(KEY_CUSTOM_FONT, PersistentDataType.BOOLEAN, true);
        }

        // ── Display name ─────────────────────────────────────────────────────
        if (data.has("display_name") && !data.get("display_name").isJsonNull()) {
            String name = data.get("display_name").getAsString();
            meta.displayName(text(name, font));
            // Kept as typed so its placeholders can be filled in per player
            if (name.indexOf('{') >= 0) {
                meta.getPersistentDataContainer().set(KEY_NAME_TEMPLATE, PersistentDataType.STRING, name);
            }
        }

        // ── Lore ─────────────────────────────────────────────────────────────
        // Prefer lore_pages; fall back to lore array
        List<String> loreLines = new ArrayList<>();
        JsonArray pages = new JsonArray();
        if (data.has("lore_pages") && data.get("lore_pages").isJsonArray()) {
            pages = data.getAsJsonArray("lore_pages");
            // Show only page 0 initially
            if (pages.size() > 0 && pages.get(0).isJsonArray()) {
                for (JsonElement line : pages.get(0).getAsJsonArray()) {
                    loreLines.add(line.getAsString());
                }
            }
        } else if (data.has("lore") && data.get("lore").isJsonArray()) {
            JsonArray page = data.getAsJsonArray("lore");
            for (JsonElement line : page) {
                loreLines.add(line.getAsString());
            }
            pages.add(page);
        }
        // Store all pages in PDC: for F-key cycling, and for lore with placeholders
        if (pages.size() > 1 || pages.toString().indexOf('{') >= 0) {
            meta.getPersistentDataContainer().set(KEY_LORE_PAGES, PersistentDataType.STRING, pages.toString());
            meta.getPersistentDataContainer().set(KEY_LORE_PAGE, PersistentDataType.INTEGER, 0);
        }

        meta.getPersistentDataContainer().set(Keys.ID.get(), PersistentDataType.STRING, id);

        meta.getPersistentDataContainer().set(Keys.UUUID.get(), PersistentDataType.STRING, data.get("update_uuid").getAsString().trim());

        if (data.has("flags") && data.get("flags").isJsonObject()) {
            JsonObject flags = data.getAsJsonObject("flags");
            int placeableVal = flags.has("placeable") && !flags.get("placeable").isJsonNull()
                    ? CitemRules.placeable(flags.get("placeable").getAsString())
                    : CitemRules.PLACE_NONE;
            meta.getPersistentDataContainer().set(Keys.PLACEABLE.get(), PersistentDataType.INTEGER, placeableVal);

            if (getBoolean(flags, "unstashable", false)) {
                meta.getPersistentDataContainer().set(Keys.UNSTASHABLE.get(), PersistentDataType.BOOLEAN, true);
            }

            if (getBoolean(flags, "unplaceable", false)) {
                meta.getPersistentDataContainer().set(Keys.UNPLACEABLE.get(), PersistentDataType.BOOLEAN, true);
            }

            if (getBoolean(flags, "unwearable", false)) {
                meta.getPersistentDataContainer().set(Keys.UNWEARABLE.get(), PersistentDataType.BOOLEAN, true);
            }

            if (flags.has("undroppable") && !flags.get("undroppable").isJsonNull()) {
                boolean undroppable = flags.get("undroppable").getAsBoolean();
                meta.getPersistentDataContainer().set(Keys.UNDROPPABLE.get(), PersistentDataType.BOOLEAN, undroppable);
            }

            if (flags.has("unusable") && !flags.get("unusable").isJsonNull()) {
                boolean unusable = flags.get("unusable").getAsBoolean();
                meta.getPersistentDataContainer().set(Keys.UNUSABLE.get(), PersistentDataType.BOOLEAN, unusable);
            }
        }

        if (!loreLines.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreLines) {
                loreComponents.add(text(line, font));
            }
            meta.lore(loreComponents);
        }

        // ── Enchanted glow (no real enchantments — just visual shimmer) ──────
        if (getBoolean(data, "enchanted", false)) {
            meta.setEnchantmentGlintOverride(true);
        }

        // ── Custom model / item model ─────────────────────────────────────────
        // Paper 1.21.4+: meta.setItemModel(NamespacedKey)
        // Fall back to legacy CustomModelData int if the value looks numeric
        if (data.has("model") && !data.get("model").isJsonNull()) {
            String modelStr = data.get("model").getAsString().trim();
            if (!modelStr.isEmpty()) {
                String ns = modelStr.contains(":") ? modelStr : NS + ":" + modelStr;
                meta.setItemModel(NamespacedKey.fromString(ns));
            }
        }

        // ── Skull texture ─────────────────────────────────────────────────────
        if (data.has("skull_texture") && !data.get("skull_texture").isJsonNull()
                && meta instanceof SkullMeta skullMeta) {
            applySkullTexture(skullMeta, data.get("skull_texture").getAsString());
        }

        // ── Custom tooltip style ─────────────────────────────────────────────
        // Stored in PDC so the resource pack / plugin logic can read it
        if (data.has("tooltip") && !data.get("tooltip").isJsonNull()) {

            NamespacedKey tooltip = new NamespacedKey("minecraft", data.get("tooltip").getAsString());
            meta.setTooltipStyle(tooltip);
        }

        // ── Click interactions ────────────────────────────────────────────────
        if (data.has("action-left") && !data.get("action-left").isJsonNull()) {
            meta.getPersistentDataContainer().set(
                    Keys.LEFT_ACTION.get(), PersistentDataType.STRING, data.get("action-left").getAsString()
            );
        }
        if (data.has("action-right") && !data.get("action-right").isJsonNull()) {
            meta.getPersistentDataContainer().set(
                    Keys.RIGHT_ACTION.get(), PersistentDataType.STRING, data.get("action-right").getAsString()
            );
        }
        if (data.has("action-placed") && !data.get("action-placed").isJsonNull()) {
            meta.getPersistentDataContainer().set(
                    Keys.PLACED_ACTION.get(), PersistentDataType.STRING, data.get("action-placed").getAsString()
            );
        }

        // ── Attributes ────────────────────────────────────────────────────────
        if (data.has("attributes") && data.get("attributes").isJsonArray()) {
            applyAttributes(meta, data.getAsJsonArray("attributes"));
        }

        // ── Components ───────────────────────────────────────────────────────
        if (data.has("components") && data.get("components").isJsonObject()) {
            applyComponents(meta, item.getType(), data.getAsJsonObject("components"));
        }

        item.setItemMeta(meta);
        return item;
    }

    // ── Components ────────────────────────────────────────────────────────────

    /**
     * Items saved before the dye color became the dyed_color component hold it
     * as "dye_color": it is applied as that component (unless the item has one).
     */
    private static void legacyDyeColor(JsonObject data) {
        if (!data.has("dye_color") || !data.get("dye_color").isJsonPrimitive()) return;
        if (!data.has("components") || !data.get("components").isJsonObject()) data.add("components", new JsonObject());
        JsonObject components = data.getAsJsonObject("components");
        if (!components.has("minecraft:dyed_color") && !components.has("dyed_color")) {
            components.add("minecraft:dyed_color", data.get("dye_color"));
        }
    }

    /**
     * The item with its parser-applied components (see {@link CitemComponents}).
     * A component the server refuses (a typo, a field of another version) is
     * skipped with a warning; the others still apply.
     */
    private static ItemStack base(String citemId, Material material, JsonObject data) {
        if (!material.isItem() || !data.has("components") || !data.get("components").isJsonObject()) {
            return new ItemStack(material);
        }
        Map<String, String> components = CitemComponents.vanilla(data.getAsJsonObject("components"));
        if (components.isEmpty()) return new ItemStack(material);

        String itemId = material.getKey().toString();
        ItemStack all = parse(CitemComponents.itemString(itemId, components));
        if (all != null) return all;

        // Something in there is invalid: find out which, keep the rest.
        Map<String, String> valid = new LinkedHashMap<>();
        for (Map.Entry<String, String> c : components.entrySet()) {
            if (parse(CitemComponents.itemString(itemId, Map.of(c.getKey(), c.getValue()))) != null) {
                valid.put(c.getKey(), c.getValue());
            } else {
                Bukkit.getLogger().warning("[CitemBuilder] " + citemId + ": component " + c.getKey()
                        + " is not valid on this server and was skipped: " + c.getValue());
            }
        }
        ItemStack rest = valid.isEmpty() ? null : parse(CitemComponents.itemString(itemId, valid));
        return rest != null ? rest : new ItemStack(material);
    }

    /** The item for a /give-style string, or null when the server can't parse it. */
    private static ItemStack parse(String itemString) {
        try {
            return Bukkit.getItemFactory().createItemStack(itemString);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** The components applied through ItemMeta ({@link CitemComponents#TYPED}). */
    private static void applyComponents(ItemMeta meta, Material mat, JsonObject components) {
        for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
            String id = CitemComponents.namespaced(entry.getKey().trim());
            JsonElement val = entry.getValue();

            switch (id) {
                // ── Display ───────────────────────────────────────────────────
                case "minecraft:rarity" -> {
                    try {
                        meta.setRarity(ItemRarity.valueOf(val.getAsString().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {}
                }
                case "minecraft:enchantment_glint_override" ->
                        meta.setEnchantmentGlintOverride(
                                val.isJsonPrimitive() && !val.getAsString().equals("false")
                        );
                // TODO: Components?
                case "minecraft:hide_tooltip" ->
                        meta.setHideTooltip(true);
                case "minecraft:hide_additional_tooltip" ->
                        // The dye line ("Dyed" / "Color: #000000") is not part of the additional tooltip
                        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_DYE);
                case "minecraft:item_name" -> {
                    // item_name stores a raw text component JSON string
                    String nameStr = val.getAsString();
                    meta.itemName(parseText(nameStr));
                }
                // ── Durability ────────────────────────────────────────────────
                case "minecraft:damage" -> {
                    if (meta instanceof Damageable d) d.setDamage(val.getAsInt());
                }
                case "minecraft:max_damage" -> {
                    if (meta instanceof Damageable d) d.setMaxDamage(val.getAsInt());
                }
                case "minecraft:unbreakable" -> {
                    meta.setUnbreakable(true);
                    // hide tooltip if show_in_tooltip is false
                    if (val.isJsonObject()) {
                        boolean show = getBooleanFromObj(val.getAsJsonObject(), "show_in_tooltip", true);
                        if (!show) meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                    }
                }
                case "minecraft:repair_cost" -> {
                    if (meta instanceof Repairable r) r.setRepairCost(val.getAsInt());
                }

                // ── Stack & Use ───────────────────────────────────────────────
                case "minecraft:max_stack_size" ->
                        meta.setMaxStackSize(val.getAsInt());

                // ── Enchantments ──────────────────────────────────────────────
                case "minecraft:enchantments" -> {
                    if (val.isJsonObject()) applyEnchantments(meta, val.getAsJsonObject(), false);
                }
                case "minecraft:stored_enchantments" -> {
                    if (val.isJsonObject() && meta instanceof EnchantmentStorageMeta esm) {
                        applyStoredEnchantments(esm, val.getAsJsonObject());
                    }
                }

                // ── Food ──────────────────────────────────────────────────────
                case "minecraft:food" -> {
                    if (val.isJsonObject()) applyFood(meta, val.getAsJsonObject());
                }

                // ── Trim ──────────────────────────────────────────────────────
                case "minecraft:trim" -> {
                    if (val.isJsonObject() && meta instanceof ArmorMeta am) {
                        applyTrim(am, val.getAsJsonObject());
                    }
                }

                // ── Banner / Shield ───────────────────────────────────────────
                case "minecraft:banner_patterns" -> {
                    if (val.isJsonArray() && meta instanceof BannerMeta bm) {
                        applyBannerPatterns(bm, val.getAsJsonArray());
                    }
                }

                // ── Map ───────────────────────────────────────────────────────
                case "minecraft:map_color" -> {
                    if (meta instanceof MapMeta mm2) {
                        mm2.setColor(Color.fromRGB(val.getAsInt()));
                    }
                }
                case "minecraft:map_id" -> {
                    if (meta instanceof MapMeta mm2) mm2.setMapId(val.getAsInt());
                }

                // ── Written book ──────────────────────────────────────────────
                case "minecraft:written_book_content" -> {
                    if (val.isJsonObject() && meta instanceof BookMeta bm) {
                        applyBook(bm, val.getAsJsonObject());
                    }
                }

                // ── Misc ──────────────────────────────────────────────────────
                case "minecraft:fire_resistant" ->
                        meta.setFireResistant(true);

                // Every other component was applied by the item parser (see base()).
                default -> { }
            }
        }
    }

    // ── Sub-appliers ──────────────────────────────────────────────────────────

    private static void applyAttributes(ItemMeta meta, JsonArray attrs) {
        for (JsonElement el : attrs) {
            if (!el.isJsonObject()) continue;
            JsonObject a = el.getAsJsonObject();

            String attrId  = getString(a, "attribute", "");
            String slotStr = getString(a, "slot", "any");
            double amount  = a.has("amount") ? a.get("amount").getAsDouble() : 0;
            String opStr   = getString(a, "operation", "add_value");
            String modId   = getString(a, "id", attrId + "_modifier");

            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.fromString(attrId));
            if (attribute == null) continue;

            AttributeModifier.Operation op = switch (opStr) {
                case "add_multiplied_base"  -> AttributeModifier.Operation.ADD_SCALAR;
                case "add_multiplied_total" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                default                     -> AttributeModifier.Operation.ADD_NUMBER;
            };

            EquipmentSlotGroup slot = switch (slotStr) {
                case "mainhand" -> EquipmentSlotGroup.MAINHAND;
                case "offhand"  -> EquipmentSlotGroup.OFFHAND;
                case "head"     -> EquipmentSlotGroup.HEAD;
                case "chest"    -> EquipmentSlotGroup.CHEST;
                case "legs"     -> EquipmentSlotGroup.LEGS;
                case "feet"     -> EquipmentSlotGroup.FEET;
                case "body"     -> EquipmentSlotGroup.BODY;
                default         -> EquipmentSlotGroup.ANY;
            };

            NamespacedKey modKey = NamespacedKey.fromString(modId.contains(":") ? modId : NS + ":" + modId);
            meta.addAttributeModifier(attribute,
                    new AttributeModifier(modKey, amount, op, slot));
        }
    }

    private static void applyEnchantments(ItemMeta meta, JsonObject enchants, boolean ignoreLevelRestriction) {
        for (Map.Entry<String, JsonElement> e : enchants.entrySet()) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.fromString(e.getKey()));
            if (ench != null) meta.addEnchant(ench, e.getValue().getAsInt(), true);
        }
    }

    private static void applyStoredEnchantments(EnchantmentStorageMeta meta, JsonObject enchants) {
        for (Map.Entry<String, JsonElement> e : enchants.entrySet()) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.fromString(e.getKey()));
            if (ench != null) meta.addStoredEnchant(ench, e.getValue().getAsInt(), true);
        }
    }

    private static void applyFood(ItemMeta meta, JsonObject food) {
        FoodComponent f = meta.getFood();
        f.setNutrition(getInt(food, "nutrition", 4));
        f.setSaturation(getFloat(food, "saturation", 2.4f));
        f.setCanAlwaysEat(getBoolean(food, "can_always_eat", false));
        meta.setFood(f);
    }

    private static void applyTrim(ArmorMeta am, JsonObject trim) {
        TrimMaterial mat = Registry.TRIM_MATERIAL.get(
                NamespacedKey.fromString(getString(trim, "material", "minecraft:iron")));
        TrimPattern pat = Registry.TRIM_PATTERN.get(
                NamespacedKey.fromString(getString(trim, "pattern", "minecraft:sentry")));
        if (mat != null && pat != null) am.setTrim(new ArmorTrim(mat, pat));
    }

    private static void applyBannerPatterns(BannerMeta bm, JsonArray layers) {
        for (JsonElement el : layers) {
            if (!el.isJsonObject()) continue;
            JsonObject layer = el.getAsJsonObject();
            PatternType pt = Registry.BANNER_PATTERN.get(
                    NamespacedKey.fromString(getString(layer, "pattern", "minecraft:base")));
            DyeColor color;
            try { color = DyeColor.valueOf(getString(layer, "color", "white").toUpperCase()); }
            catch (IllegalArgumentException e) { color = DyeColor.WHITE; }
            if (pt != null) bm.addPattern(new org.bukkit.block.banner.Pattern(color, pt));
        }
    }

    private static void applyBook(BookMeta bm, JsonObject book) {
        if (book.has("title")) bm.setTitle(book.get("title").getAsString());
        if (book.has("author")) bm.setAuthor(book.get("author").getAsString());
        if (book.has("pages") && book.get("pages").isJsonArray()) {
            List<Component> pages = new ArrayList<>();
            for (JsonElement p : book.getAsJsonArray("pages")) {
                pages.add(parseText(p.getAsString()));
            }
            bm.pages(pages);
        }
        // generation: 0 original, 1 copy of original, 2 copy of copy, 3 tattered
        BookMeta.Generation[] generations = BookMeta.Generation.values();
        int generation = getInt(book, "generation", 0);
        bm.setGeneration(generation >= 0 && generation < generations.length
                ? generations[generation] : BookMeta.Generation.ORIGINAL);
    }

    private static void applySkullTexture(SkullMeta meta, String textureUrl) {
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(textureUrl).toURL());
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[CitemBuilder] Invalid skull texture URL: " + textureUrl);
        }
    }

    // ── Text parsing ──────────────────────────────────────────────────────────

    /**
     * Parses a string that may contain:
     *   - Legacy §/& color codes
     *   - <#RRGGBB> hex color tags
     *   - <gradient:#FROM:#TO>text</gradient>
     * All three are normalised to MiniMessage before parsing.
     */
    public static Component parseText(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return MM.deserialize("<!italic>" + preprocessLegacy(raw));
    }

    /** A name or lore line: {@link #parseText}, in the custom font when the item uses it. */
    public static Component text(String raw, boolean font) {
        return parseText(font ? CitemRules.stylize(raw) : raw);
    }

    /**
     * Converts §x / &x legacy codes to their MiniMessage equivalents so that
     * MiniMessage.deserialize() can handle everything in one pass.
     */
    private static String preprocessLegacy(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                String mm = LEGACY_TO_MM.get(code);
                if (mm != null) {
                    sb.append(mm);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Color parseColor(String s) {
        if (s == null || s.isBlank()) return Color.WHITE;
        try {
            if (s.startsWith("#")) return Color.fromRGB(Integer.parseInt(s.substring(1), 16));
            return Color.fromRGB(Integer.parseInt(s)); // decimal
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static int getInt(JsonObject o, String key, int def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
    }

    private static float getFloat(JsonObject o, String key, float def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : def;
    }

    private static boolean getBoolean(JsonObject o, String key, boolean def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : def;
    }

    private static boolean getBooleanFromObj(JsonObject o, String key, boolean def) {
        return getBoolean(o, key, def);
    }
}