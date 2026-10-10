package me.hektortm.woSSystems.utils.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A custom recipe as staff built it in the portal. Its conditions are of type
 * {@code recipe} with the id {@code <recipe>:craft}.
 *
 * @param type      shaped, shapeless, cooking or smithing
 * @param slots     shaped: 9, row by row; shapeless: 1 to 9; cooking: 1;
 *                  smithing: template, base, addition
 * @param result    what the recipe gives ({@code id} empty: not chosen yet)
 * @param category  the recipe book tab
 * @param matchtype all or one of its conditions
 * @param commands  run after a craft ({@code {crafted}}: how many were made)
 * @param cooking   stations, experience and cook time (cooking only)
 * @param keepBase  smithing: the result keeps the base item's enchantments and damage
 */
public record Crecipe(String id, String type, List<Slot> slots, Item result, int amount, String category,
                      String matchtype, List<String> commands, Cooking cooking, boolean keepBase) {

    public static final String SHAPED = "shaped";
    public static final String SHAPELESS = "shapeless";
    public static final String COOKING = "cooking";
    public static final String SMITHING = "smithing";

    /** A custom item ({@code kind} citem) or a vanilla item ({@code kind} item, {@code id} a material). */
    public record Item(String kind, String id) {
        public boolean isCitem() {
            return "citem".equals(kind);
        }

        /** What an item in a grid must be to count as this one: {@code citem:<id>} or {@code item:<MATERIAL>}. */
        public String key() {
            return isCitem() ? citemKey(id) : itemKey(id);
        }
    }

    /** One slot: the items it accepts (none: the slot stays empty). */
    public record Slot(List<Item> items, Set<String> keys) {
        public static Slot of(List<Item> items) {
            Set<String> keys = new LinkedHashSet<>();
            for (Item item : items) keys.add(item.key());
            return new Slot(List.copyOf(items), keys);
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    /** @param ticks the cook time in ticks */
    public record Cooking(List<String> stations, float experience, int ticks) {}

    public static String citemKey(String id) {
        return "citem:" + id;
    }

    public static String itemKey(String material) {
        return "item:" + material.toUpperCase(Locale.ROOT);
    }

    public boolean isCrafting() {
        return SHAPED.equals(type) || SHAPELESS.equals(type);
    }
}
