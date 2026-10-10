package me.hektortm.woSSystems.systems.crecipes;

import me.hektortm.woSSystems.utils.model.Crecipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The decisions about a custom recipe that need no server: whether what lies
 * in a grid is the recipe, whether the recipe is complete, and what its
 * commands and its server key look like.
 *
 * <p>A grid is a list of keys, one per slot, {@code null} for an empty slot:
 * {@code citem:<id>} for a custom item, {@code item:<MATERIAL>} for any other
 * item (see {@link Crecipe#citemKey} and {@link Crecipe#itemKey}).</p>
 */
public final class CrecipeRules {

    private CrecipeRules() {}

    /**
     * Whether the conditions let a player craft: without conditions always;
     * with match type {@code one} when any is met, otherwise when all are.
     */
    public static boolean allowed(@Nullable String matchtype, int conditions, int met) {
        if (conditions == 0) return true;
        return "one".equalsIgnoreCase(matchtype) ? met > 0 : met == conditions;
    }

    // ── shapes ─────────────────────────────────────────────────────────────────

    /** A grid without its empty outer rows and columns. */
    public record Shape<T>(int width, int height, List<T> cells) {
        public T at(int column, int row) {
            return cells.get(row * width + column);
        }
    }

    /**
     * Cuts the empty rows and columns off the sides of a grid, so a recipe
     * drawn in one corner fits anywhere. A grid with nothing in it has width 0.
     */
    public static <T> Shape<T> trim(List<T> cells, int width, Predicate<T> empty) {
        int height = width == 0 ? 0 : cells.size() / width;
        int left = width, right = -1, top = height, bottom = -1;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                if (empty.test(cells.get(row * width + column))) continue;
                left = Math.min(left, column);
                right = Math.max(right, column);
                top = Math.min(top, row);
                bottom = Math.max(bottom, row);
            }
        }
        if (right < 0) return new Shape<>(0, 0, List.of());
        List<T> out = new ArrayList<>();
        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) out.add(cells.get(row * width + column));
        }
        return new Shape<>(right - left + 1, bottom - top + 1, out);
    }

    /** A shaped recipe's 9 slots without the empty sides. */
    public static Shape<Crecipe.Slot> shape(List<Crecipe.Slot> slots) {
        return trim(slots, 3, Crecipe.Slot::isEmpty);
    }

    // ── matching ───────────────────────────────────────────────────────────────

    /**
     * Whether the grid holds the shaped recipe: the same layout anywhere in
     * the grid, also mirrored left to right, every slot holding one of the
     * items it accepts and nothing lying outside it.
     *
     * @param gridWidth 3 for a crafting table, 2 for the inventory grid
     */
    public static boolean matchesShaped(List<Crecipe.Slot> slots, List<String> grid, int gridWidth) {
        Shape<Crecipe.Slot> recipe = shape(slots);
        Shape<String> placed = trim(grid, gridWidth, Objects::isNull);
        if (recipe.width() == 0 || recipe.width() != placed.width() || recipe.height() != placed.height()) return false;
        return fits(recipe, placed, false) || fits(recipe, placed, true);
    }

    private static boolean fits(Shape<Crecipe.Slot> recipe, Shape<String> placed, boolean mirrored) {
        for (int row = 0; row < recipe.height(); row++) {
            for (int column = 0; column < recipe.width(); column++) {
                Crecipe.Slot slot = recipe.at(mirrored ? recipe.width() - 1 - column : column, row);
                if (!accepts(slot, placed.at(column, row))) return false;
            }
        }
        return true;
    }

    /** An empty slot wants nothing; any other wants one of its items. */
    private static boolean accepts(Crecipe.Slot slot, @Nullable String key) {
        return slot.isEmpty() ? key == null : key != null && slot.keys().contains(key);
    }

    /**
     * Whether the grid holds the shapeless recipe: as many items as the recipe
     * has slots, each slot getting one item it accepts, in any order.
     */
    public static boolean matchesShapeless(List<Crecipe.Slot> slots, List<String> grid) {
        List<String> placed = new ArrayList<>();
        for (String key : grid) {
            if (key != null) placed.add(key);
        }
        if (slots.isEmpty() || placed.size() != slots.size()) return false;
        return assign(slots, 0, placed, new boolean[placed.size()]);
    }

    /** Gives slot {@code index} and every later one an item of their own (alternatives make a first-fit wrong). */
    private static boolean assign(List<Crecipe.Slot> slots, int index, List<String> placed, boolean[] used) {
        if (index == slots.size()) return true;
        Set<String> wanted = slots.get(index).keys();
        for (int i = 0; i < placed.size(); i++) {
            if (used[i] || !wanted.contains(placed.get(i))) continue;
            used[i] = true;
            if (assign(slots, index + 1, placed, used)) return true;
            used[i] = false;
        }
        return false;
    }

    /** Whether template, base and addition are what the smithing recipe wants (an empty slot wants nothing). */
    public static boolean matchesSmithing(List<Crecipe.Slot> slots, List<String> inputs) {
        if (slots.size() != 3 || inputs.size() != 3) return false;
        for (int i = 0; i < 3; i++) {
            if (!accepts(slots.get(i), inputs.get(i))) return false;
        }
        return true;
    }

    /** Whether the grid holds the crafting recipe (shaped or shapeless). */
    public static boolean matchesCrafting(Crecipe recipe, List<String> grid, int gridWidth) {
        if (Crecipe.SHAPED.equals(recipe.type())) return matchesShaped(recipe.slots(), grid, gridWidth);
        return Crecipe.SHAPELESS.equals(recipe.type()) && matchesShapeless(recipe.slots(), grid);
    }

    /** The first of the recipes (in the order given) the grid holds, or {@code null}. */
    @Nullable
    public static Crecipe findCrafting(Collection<Crecipe> recipes, List<String> grid, int gridWidth) {
        for (Crecipe recipe : recipes) {
            if (matchesCrafting(recipe, grid, gridWidth)) return recipe;
        }
        return null;
    }

    // ── completeness ───────────────────────────────────────────────────────────

    /**
     * Why a recipe can't be used yet, or {@code null} if it is complete. A
     * recipe may be saved half built; such a recipe is left out in game.
     */
    @Nullable
    public static String problem(Crecipe recipe) {
        if (recipe.result().id().isBlank()) return "it has no result";
        List<Crecipe.Slot> slots = recipe.slots();
        switch (recipe.type()) {
            case Crecipe.SHAPED:
                return slots.size() == 9 && shape(slots).width() > 0 ? null : "it has no ingredients";
            case Crecipe.SHAPELESS:
                if (slots.isEmpty() || slots.size() > 9) return "it needs one to nine ingredients";
                return slots.stream().anyMatch(Crecipe.Slot::isEmpty) ? "one of its ingredients is empty" : null;
            case Crecipe.COOKING:
                if (slots.size() != 1 || slots.get(0).isEmpty()) return "it has no ingredient";
                return recipe.cooking().stations().isEmpty() ? "it has no station" : null;
            case Crecipe.SMITHING:
                if (slots.size() != 3 || slots.get(1).isEmpty()) return "it has no base item";
                return slots.get(0).isEmpty() && slots.get(2).isEmpty() ? "it needs a template or an addition" : null;
            default:
                return "its type '" + recipe.type() + "' is unknown";
        }
    }

    /**
     * What tells two crafting recipes with the same layout apart from the
     * others: equal for recipes that want the same items in the same places
     * (shapeless: in any order). Mirrored twins are not found.
     */
    public static String layout(Crecipe recipe) {
        List<String> parts = new ArrayList<>();
        if (Crecipe.SHAPED.equals(recipe.type())) {
            Shape<Crecipe.Slot> shape = shape(recipe.slots());
            parts.add(shape.width() + "x" + shape.height());
            for (Crecipe.Slot slot : shape.cells()) parts.add(sorted(slot.keys()));
        } else {
            for (Crecipe.Slot slot : recipe.slots()) parts.add(sorted(slot.keys()));
            parts.sort(null);
            parts.add(0, "any");
        }
        return String.join("|", parts);
    }

    private static String sorted(Set<String> keys) {
        List<String> list = new ArrayList<>(keys);
        list.sort(null);
        return String.join(",", list);
    }

    // ── keys, commands, numbers ────────────────────────────────────────────────

    /**
     * The name of the server recipe for a recipe id (and, for cooking, a
     * station): lowercase, anything a key may not hold replaced by {@code _}.
     */
    public static String keyName(String id, @Nullable String station) {
        String name = "crecipe/" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return station == null ? name : name + "/" + station;
    }

    /** The commands with {@code {crafted}} replaced by how many items were made. */
    public static List<String> fill(List<String> commands, int crafted) {
        List<String> out = new ArrayList<>();
        for (String command : commands) out.add(command.replace("{crafted}", String.valueOf(crafted)));
        return out;
    }

    /** A cook time in seconds as ticks: at least one tick, 10 seconds for a value that makes no sense. */
    public static int ticks(double seconds) {
        if (Double.isNaN(seconds) || seconds <= 0) return 200;
        return (int) Math.max(1, Math.min(Math.round(seconds * 20), 72000));
    }

    /**
     * How many items a click made, from what the player carried before and
     * after it. A click that dropped the result leaves nothing behind, so it
     * counts as one craft; any other click that added nothing made nothing.
     */
    public static int crafted(int before, int after, boolean dropped, int perCraft) {
        if (after > before) return after - before;
        return dropped ? perCraft : 0;
    }
}
