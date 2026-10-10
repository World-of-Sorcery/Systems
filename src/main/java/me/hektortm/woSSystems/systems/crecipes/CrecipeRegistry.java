package me.hektortm.woSSystems.systems.crecipes;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.model.Crecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns custom recipes into server recipes and keeps track of which server
 * recipe belongs to which custom recipe. The server recipes are what the
 * recipe book shows and fills in; for crafting and smithing the result is
 * still decided by {@link CrecipeManager}.
 */
final class CrecipeRegistry {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;

    /** Every server recipe of ours and the custom recipe it was made from. */
    private final Map<NamespacedKey, Crecipe> registered = new LinkedHashMap<>();
    /** Those of them a recipe book can show (crafting, and cooking except the campfire). */
    private final Map<NamespacedKey, Crecipe> book = new LinkedHashMap<>();

    CrecipeRegistry(DAOHub hub) {
        this.hub = hub;
    }

    @Nullable
    Crecipe recipeOf(NamespacedKey key) {
        return registered.get(key);
    }

    Map<NamespacedKey, Crecipe> bookEntries() {
        return Collections.unmodifiableMap(book);
    }

    /** Takes our recipes off the server. */
    void clear() {
        for (NamespacedKey key : registered.keySet()) Bukkit.removeRecipe(key, false);
        registered.clear();
        book.clear();
    }

    /**
     * Replaces the registered recipes with these and tells the players' clients.
     * A recipe that is not complete, or names an item that does not exist, is
     * left out with a warning; the ones that were registered are returned.
     */
    List<Crecipe> register(List<Crecipe> recipes) {
        clear();
        List<Crecipe> done = new ArrayList<>();
        for (Crecipe recipe : recipes) {
            String problem = CrecipeRules.problem(recipe);
            try {
                if (problem == null) {
                    add(recipe);
                    done.add(recipe);
                    continue;
                }
            } catch (RuntimeException e) {
                // Unknown items throw our own message; the server may refuse a recipe too (a key taken twice).
                problem = e.getMessage() == null ? e.toString() : e.getMessage();
                forget(recipe);
            }
            plugin.getLogger().warning("[Crecipes] " + recipe.id() + " is left out: " + problem);
        }
        Bukkit.updateRecipes();
        return done;
    }

    private void forget(Crecipe recipe) {
        registered.entrySet().removeIf(entry -> {
            if (entry.getValue() != recipe) return false;
            Bukkit.removeRecipe(entry.getKey(), false);
            return true;
        });
        book.values().removeIf(value -> value == recipe);
    }

    private void add(Crecipe recipe) {
        ItemStack result = item(recipe.result());
        if (result == null) throw unknown(recipe.result());
        result.setAmount(Math.min(recipe.amount(), result.getMaxStackSize()));
        switch (recipe.type()) {
            case Crecipe.SHAPED -> put(recipe, shaped(recipe, key(recipe, null), result), true);
            case Crecipe.SHAPELESS -> put(recipe, shapeless(recipe, key(recipe, null), result), true);
            case Crecipe.SMITHING -> put(recipe, smithing(recipe, key(recipe, null), result), false);
            default -> {
                RecipeChoice input = choice(recipe.slots().get(0));
                for (String station : recipe.cooking().stations()) {
                    put(recipe, cooking(recipe, station, key(recipe, station), result, input), !"campfire".equals(station));
                }
            }
        }
    }

    private void put(Crecipe recipe, Recipe built, boolean inBook) {
        NamespacedKey key = ((org.bukkit.Keyed) built).getKey();
        if (registered.containsKey(key) || !Bukkit.addRecipe(built, false)) {
            throw new IllegalStateException("the server already has a recipe named " + key);
        }
        registered.put(key, recipe);
        if (inBook) book.put(key, recipe);
    }

    private NamespacedKey key(Crecipe recipe, @Nullable String station) {
        return new NamespacedKey(plugin, CrecipeRules.keyName(recipe.id(), station));
    }

    // ── one kind each ──────────────────────────────────────────────────────────

    private ShapedRecipe shaped(Crecipe recipe, NamespacedKey key, ItemStack result) {
        CrecipeRules.Shape<Crecipe.Slot> shape = CrecipeRules.shape(recipe.slots());
        String[] rows = new String[shape.height()];
        Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
        for (int row = 0; row < shape.height(); row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < shape.width(); column++) {
                Crecipe.Slot slot = shape.at(column, row);
                char letter = slot.isEmpty() ? ' ' : (char) ('a' + choices.size());
                if (!slot.isEmpty()) choices.put(letter, choice(slot));
                line.append(letter);
            }
            rows[row] = line.toString();
        }
        ShapedRecipe out = new ShapedRecipe(key, result);
        out.shape(rows);
        choices.forEach(out::setIngredient);
        out.setCategory(craftingTab(recipe.category()));
        return out;
    }

    private ShapelessRecipe shapeless(Crecipe recipe, NamespacedKey key, ItemStack result) {
        ShapelessRecipe out = new ShapelessRecipe(key, result);
        for (Crecipe.Slot slot : recipe.slots()) out.addIngredient(choice(slot));
        out.setCategory(craftingTab(recipe.category()));
        return out;
    }

    private SmithingTransformRecipe smithing(Crecipe recipe, NamespacedKey key, ItemStack result) {
        List<Crecipe.Slot> slots = recipe.slots();
        // "Keep the base item's enchantments" is done by the manager, which sets the result itself.
        return new SmithingTransformRecipe(key, result, choiceOrNone(slots.get(0)), choice(slots.get(1)),
                choiceOrNone(slots.get(2)), false);
    }

    private CookingRecipe<?> cooking(Crecipe recipe, String station, NamespacedKey key, ItemStack result, RecipeChoice input) {
        float experience = recipe.cooking().experience();
        int ticks = recipe.cooking().ticks();
        CookingRecipe<?> out = switch (station) {
            case "furnace" -> new FurnaceRecipe(key, result, input, experience, ticks);
            case "blast_furnace" -> new BlastingRecipe(key, result, input, experience, ticks);
            case "smoker" -> new SmokingRecipe(key, result, input, experience, ticks);
            case "campfire" -> new CampfireRecipe(key, result, input, experience, ticks);
            default -> throw new IllegalArgumentException("its station '" + station + "' is unknown");
        };
        out.setCategory(cookingTab(recipe.category()));
        return out;
    }

    private static CraftingBookCategory craftingTab(String category) {
        try {
            return CraftingBookCategory.valueOf(category.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CraftingBookCategory.MISC;
        }
    }

    private static CookingBookCategory cookingTab(String category) {
        try {
            return CookingBookCategory.valueOf(category.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CookingBookCategory.MISC;
        }
    }

    // ── items ──────────────────────────────────────────────────────────────────

    /** A fresh copy of the item (one of it), or null if the custom item or material does not exist. */
    @Nullable
    ItemStack item(Crecipe.Item item) {
        if (item.isCitem()) return hub.getCitemDAO().getCitem(item.id());
        Material material = CrecipeManager.material(item.id());
        return material == null ? null : new ItemStack(material);
    }

    private RecipeChoice choiceOrNone(Crecipe.Slot slot) {
        return slot.isEmpty() ? RecipeChoice.empty() : choice(slot);
    }

    /**
     * What a slot accepts, for the server: only vanilla items become a choice
     * of materials; a slot with a custom item wants its items exactly as they
     * are defined, which is also what the recipe book then shows.
     */
    private RecipeChoice choice(Crecipe.Slot slot) {
        List<ItemStack> exact = new ArrayList<>();
        List<Material> materials = new ArrayList<>();
        boolean custom = false;
        for (Crecipe.Item item : slot.items()) {
            ItemStack stack = item(item);
            if (stack == null) throw unknown(item);
            stack.setAmount(1);
            exact.add(stack);
            if (item.isCitem()) custom = true;
            else materials.add(stack.getType());
        }
        return custom ? new RecipeChoice.ExactChoice(exact) : new RecipeChoice.MaterialChoice(materials);
    }

    private static IllegalArgumentException unknown(Crecipe.Item item) {
        return new IllegalArgumentException("there is no " + (item.isCitem() ? "custom item" : "item") + " '" + item.id() + "'");
    }
}
