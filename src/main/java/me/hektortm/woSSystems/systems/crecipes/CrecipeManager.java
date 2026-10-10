package me.hektortm.woSSystems.systems.crecipes;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.citems.CitemManager;
import me.hektortm.woSSystems.systems.debug.DebugFormat;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.Crecipe;
import me.hektortm.woSSystems.utils.types.ConditionType;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom recipes (Crecipes): recipes staff build in the portal. Each one is
 * registered as a server recipe ({@link CrecipeRegistry}), which gives the
 * recipe book entry and click-to-fill; what a grid really gives is decided
 * here, by custom item id ({@link CrecipeRules}), together with the recipe's
 * conditions. {@link CrecipeListener} asks this class on every change.
 */
public final class CrecipeManager {

    /** How often every online player's recipe book is compared with their conditions. */
    private static final long BOOK_PERIOD_TICKS = 100L;

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final CrecipeRegistry registry;
    private final AtomicBoolean syncQueued = new AtomicBoolean();

    /** The complete recipes by kind, in the order of their ids (the first match wins). */
    private volatile List<Crecipe> crafting = List.of();
    private volatile List<Crecipe> smithing = List.of();
    private volatile List<Crecipe> cooking = List.of();

    public CrecipeManager(DAOHub hub) {
        this.hub = hub;
        this.registry = new CrecipeRegistry(hub);
        // A changed custom item changes what a recipe shows and gives, so both lists lead to a new registration.
        hub.getCrecipeDAO().onChange(this::queueSync);
        hub.getCitemDAO().onChange(this::queueSync);
    }

    /** Registers the recipes and starts keeping the players' recipe books up to date. Main thread. */
    public void start() {
        sync();
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBooks, BOOK_PERIOD_TICKS, BOOK_PERIOD_TICKS);
    }

    /** Takes every custom recipe off the server again (the plugin is stopping). */
    public void stop() {
        registry.clear();
    }

    private void queueSync() {
        if (!plugin.isEnabled() || !syncQueued.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncQueued.set(false);
            sync();
        });
    }

    /** Registers the recipes as they are now, in place of the ones registered before. */
    private void sync() {
        List<Crecipe> all = new ArrayList<>(hub.getCrecipeDAO().all());
        all.sort(Comparator.comparing(Crecipe::id));
        List<Crecipe> registered = registry.register(all);

        List<Crecipe> craft = new ArrayList<>(), smith = new ArrayList<>(), cook = new ArrayList<>();
        Map<String, String> layouts = new HashMap<>();
        for (Crecipe recipe : registered) {
            if (Crecipe.COOKING.equals(recipe.type())) cook.add(recipe);
            else if (Crecipe.SMITHING.equals(recipe.type())) smith.add(recipe);
            else {
                craft.add(recipe);
                String first = layouts.putIfAbsent(CrecipeRules.layout(recipe), recipe.id());
                if (first != null) {
                    plugin.getLogger().warning("[Crecipes] " + recipe.id() + " has the same ingredients as " + first
                            + ": " + first + " is the one players get.");
                }
            }
        }
        crafting = craft;
        smithing = smith;
        cooking = cook;
        refreshBooks();
    }

    // ── what lies in a grid ────────────────────────────────────────────────────

    /** The key of an item for matching: null for nothing, the custom item's id, or the material. */
    @Nullable
    public String keyOf(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return null;
        String citem = CitemManager.citemIdOf(item);
        return citem != null ? Crecipe.citemKey(citem) : Crecipe.itemKey(item.getType().name());
    }

    private List<String> keys(ItemStack... items) {
        List<String> out = new ArrayList<>();
        for (ItemStack item : items) out.add(keyOf(item));
        return out;
    }

    /** Whether any of the items is a custom item. */
    public boolean holdsCitem(ItemStack... items) {
        return Arrays.stream(items).anyMatch(item -> item != null && CitemManager.citemIdOf(item) != null);
    }

    /** The crafting recipe a grid holds (9 slots: a table or a crafter, 4: the inventory grid), or null. */
    @Nullable
    public Crecipe matchCrafting(ItemStack[] matrix) {
        if (matrix.length != 9 && matrix.length != 4) return null;
        return CrecipeRules.findCrafting(crafting, keys(matrix), matrix.length == 9 ? 3 : 2);
    }

    /** The smithing recipe for what lies in the three slots, or null. */
    @Nullable
    public Crecipe matchSmithing(@Nullable ItemStack template, @Nullable ItemStack base, @Nullable ItemStack addition) {
        List<String> inputs = keys(template, base, addition);
        for (Crecipe recipe : smithing) {
            if (CrecipeRules.matchesSmithing(recipe.slots(), inputs)) return recipe;
        }
        return null;
    }

    /** Whether the server recipe is one of ours. */
    public boolean isOurs(@Nullable Recipe recipe) {
        return recipe instanceof Keyed keyed && registry.recipeOf(keyed.getKey()) != null;
    }

    /**
     * Whether a station may cook a custom item: only through a custom cooking
     * recipe for that station which lists the item.
     *
     * @param station furnace, blast_furnace, smoker or campfire
     */
    public boolean mayCook(String citemId, String station) {
        String key = Crecipe.citemKey(citemId);
        for (Crecipe recipe : cooking) {
            if (recipe.cooking().stations().contains(station) && recipe.slots().get(0).keys().contains(key)) return true;
        }
        return false;
    }

    // ── conditions ─────────────────────────────────────────────────────────────

    private List<Condition> conditions(Crecipe recipe) {
        return hub.getConditionDAO().getConditions(ConditionType.RECIPE, recipe.id() + ":craft");
    }

    public boolean hasConditions(Crecipe recipe) {
        return !conditions(recipe).isEmpty();
    }

    /** Whether the player's conditions let them craft the recipe. */
    public boolean allowed(Player player, Crecipe recipe) {
        List<Condition> conditions = conditions(recipe);
        int met = 0;
        for (Condition c : conditions) {
            if (plugin.getConditionHandler().evaluate(player, c, null)) met++;
        }
        return CrecipeRules.allowed(recipe.matchtype(), conditions.size(), met);
    }

    /** Tells a player in debug mode which recipe their grid holds and, if it is withheld, each condition. */
    public void debug(Player player, Crecipe recipe, boolean allowed) {
        if (!plugin.getDebugMode().isOn(player)) return;
        player.sendMessage(DebugFormat.header("crecipe", recipe.id(), recipe.type(), allowed ? null : "withheld"));
        if (allowed) return;
        for (Condition c : conditions(recipe)) {
            player.sendMessage(DebugFormat.condition(c.getName(), c.getValue(), c.getParameter(),
                    plugin.getConditionHandler().evaluate(player, c, null), plugin.getConditionHandler().actual(player, c, null)));
        }
    }

    // ── the result ─────────────────────────────────────────────────────────────

    /**
     * What the recipe gives, or null if its item no longer exists.
     *
     * @param player whose values fill in a custom item's placeholders (null: none)
     * @param base   smithing: the item being upgraded, for "keep the base item's enchantments and damage"
     */
    @Nullable
    public ItemStack result(Crecipe recipe, @Nullable Player player, @Nullable ItemStack base) {
        ItemStack item = registry.item(recipe.result());
        if (item == null) return null;
        item.setAmount(Math.min(recipe.amount(), item.getMaxStackSize()));
        if (recipe.keepBase() && base != null) keepBase(item, base);
        if (player != null) plugin.getCitemManager().personalize(item, player);
        return item;
    }

    private static void keepBase(ItemStack result, ItemStack base) {
        result.addUnsafeEnchantments(base.getEnchantments());
        ItemMeta from = base.getItemMeta();
        ItemMeta to = result.getItemMeta();
        if (from instanceof Damageable worn && to instanceof Damageable fresh && worn.hasDamage()) {
            fresh.setDamage(worn.getDamage());
            result.setItemMeta(to);
        }
    }

    /** How many of the items the player carries (inventory, armour, off hand and cursor) are the same kind as {@code like}. */
    public int carried(Player player, ItemStack like) {
        String key = keyOf(like);
        if (key == null) return 0;
        int found = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (key.equals(keyOf(item))) found += item.getAmount();
        }
        ItemStack cursor = player.getItemOnCursor();
        return key.equals(keyOf(cursor)) ? found + cursor.getAmount() : found;
    }

    /** Runs the recipe's commands for the player after a craft that made {@code crafted} items. */
    public void crafted(Player player, Crecipe recipe, int crafted) {
        if (crafted <= 0 || recipe.commands().isEmpty() || !player.isOnline()) return;
        plugin.getActionHandler().executeActions(player, CrecipeRules.fill(recipe.commands(), crafted),
                ActionHandler.SourceType.CRECIPE, recipe.id(), null, "crafted " + crafted);
    }

    // ── the recipe book ────────────────────────────────────────────────────────

    /** Gives every online player's recipe book the recipes their conditions pass and takes the others out. */
    public void refreshBooks() {
        for (Player player : Bukkit.getOnlinePlayers()) refreshBook(player);
    }

    public void refreshBook(Player player) {
        for (Map.Entry<NamespacedKey, Crecipe> entry : registry.bookEntries().entrySet()) {
            boolean wanted = allowed(player, entry.getValue());
            if (wanted == player.hasDiscoveredRecipe(entry.getKey())) continue;
            if (wanted) player.discoverRecipe(entry.getKey());
            else player.undiscoverRecipe(entry.getKey());
        }
    }

    /** The material a vanilla ingredient or result names, or null if there is no such item. */
    @Nullable
    static Material material(String id) {
        Material material = Material.matchMaterial(id);
        return material != null && material.isItem() && !material.isAir() ? material : null;
    }
}
