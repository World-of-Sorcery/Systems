package me.hektortm.woSSystems.systems.crecipes;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.citems.CitemManager;
import me.hektortm.woSSystems.utils.model.Crecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import javax.annotation.Nullable;

/**
 * Custom recipes in the crafting grid, the crafter, the smithing table and
 * the cooking blocks.
 *
 * <p>Crafting and smithing: on every change of the grid the result is set to
 * what the matching custom recipe gives, or to nothing when the player's
 * conditions fail. A vanilla recipe with a custom item in it gives nothing.
 * When the server itself knows no recipe for the grid (a custom item whose
 * name or lore was filled in for its owner no longer equals its definition),
 * the click on the result is carried out here, one craft per click.</p>
 *
 * <p>Cooking: the server matches on its own; a custom item is only let through
 * when a custom cooking recipe for that block lists it.</p>
 */
public final class CrecipeListener implements Listener {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final CrecipeManager recipes;

    public CrecipeListener(CrecipeManager recipes) {
        this.recipes = recipes;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        recipes.refreshBook(event.getPlayer());
    }

    // ── crafting ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        showCraftingResult(player, event.getInventory(), true);
    }

    /** Puts into the result slot what the grid gives this player (see the class comment). */
    private void showCraftingResult(Player player, CraftingInventory inventory, boolean debug) {
        ItemStack[] matrix = inventory.getMatrix();
        Crecipe match = recipes.matchCrafting(matrix);
        if (match == null) {
            if (recipes.holdsCitem(matrix) || recipes.isOurs(inventory.getRecipe())) inventory.setResult(null);
            return;
        }
        boolean allowed = recipes.allowed(player, match);
        if (debug) recipes.debug(player, match, allowed);
        inventory.setResult(allowed ? recipes.result(match, player, null) : null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResultClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT || !(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory() instanceof CraftingInventory crafting) {
            craftingClick(event, player, crafting);
        } else if (event.getInventory() instanceof SmithingInventory smithing) {
            smithingClick(event, player, smithing);
        }
    }

    private void craftingClick(InventoryClickEvent event, Player player, CraftingInventory inventory) {
        Crecipe match = recipes.matchCrafting(inventory.getMatrix());
        if (match == null) return;
        if (!recipes.allowed(player, match)) {
            event.setCancelled(true);
            return;
        }
        ItemStack result = recipes.result(match, player, null);
        if (result == null) {
            event.setCancelled(true);
        } else if (inventory.getRecipe() == null) {
            event.setCancelled(true);
            boolean toInventory = event.isShiftClick();
            later(() -> craftByHand(player, inventory, match, toInventory));
        } else {
            countAfterClick(event, player, match, result);
        }
    }

    /**
     * A craft the server carries out itself: the commands run once the click
     * is through, with the number of items it made.
     */
    private void countAfterClick(InventoryClickEvent event, Player player, Crecipe match, ItemStack result) {
        if (match.commands().isEmpty()) return;
        int before = recipes.carried(player, result);
        boolean dropped = event.getAction() == InventoryAction.DROP_ONE_SLOT || event.getAction() == InventoryAction.DROP_ALL_SLOT;
        later(() -> recipes.crafted(player, match,
                CrecipeRules.crafted(before, recipes.carried(player, result), dropped, result.getAmount())));
    }

    /** One craft of a grid the server has no recipe for: an item off every slot, the result to the cursor or the inventory. */
    private void craftByHand(Player player, CraftingInventory inventory, Crecipe expected, boolean toInventory) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
        ItemStack[] matrix = inventory.getMatrix();
        Crecipe match = recipes.matchCrafting(matrix);
        if (match != expected || !recipes.allowed(player, match)) return;
        ItemStack result = recipes.result(match, player, null);
        if (result == null || !hand(player, result, toInventory)) return;
        inventory.setMatrix(oneLess(matrix));
        showCraftingResult(player, inventory, false);
        player.updateInventory();
        recipes.crafted(player, match, result.getAmount());
    }

    // ── smithing ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        SmithingInventory inventory = event.getInventory();
        ItemStack[] inputs = inputs(inventory);
        Crecipe match = recipes.matchSmithing(inputs[0], inputs[1], inputs[2]);
        if (match == null) {
            if (recipes.holdsCitem(inputs) || recipes.isOurs(inventory.getRecipe())) event.setResult(null);
            return;
        }
        boolean allowed = recipes.allowed(player, match);
        recipes.debug(player, match, allowed);
        event.setResult(allowed ? recipes.result(match, player, inputs[1]) : null);
    }

    private static ItemStack[] inputs(SmithingInventory inventory) {
        return new ItemStack[]{inventory.getInputTemplate(), inventory.getInputEquipment(), inventory.getInputMineral()};
    }

    private void smithingClick(InventoryClickEvent event, Player player, SmithingInventory inventory) {
        ItemStack[] inputs = inputs(inventory);
        Crecipe match = recipes.matchSmithing(inputs[0], inputs[1], inputs[2]);
        if (match == null) return;
        ItemStack result = recipes.allowed(player, match) ? recipes.result(match, player, inputs[1]) : null;
        if (result == null) {
            event.setCancelled(true);
        } else if (inventory.getRecipe() == null) {
            event.setCancelled(true);
            boolean toInventory = event.isShiftClick();
            later(() -> smithByHand(player, inventory, match, toInventory));
        } else {
            countAfterClick(event, player, match, result);
        }
    }

    private void smithByHand(Player player, SmithingInventory inventory, Crecipe expected, boolean toInventory) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
        ItemStack[] inputs = inputs(inventory);
        Crecipe match = recipes.matchSmithing(inputs[0], inputs[1], inputs[2]);
        if (match != expected || !recipes.allowed(player, match)) return;
        ItemStack result = recipes.result(match, player, inputs[1]);
        if (result == null || !hand(player, result, toInventory)) return;
        ItemStack[] left = oneLess(inputs);
        inventory.setInputTemplate(left[0]);
        inventory.setInputEquipment(left[1]);
        inventory.setInputMineral(left[2]);
        // What is left decides the next result; nothing left, nothing shown.
        Crecipe next = recipes.matchSmithing(left[0], left[1], left[2]);
        inventory.setResult(next != null && recipes.allowed(player, next) ? recipes.result(next, player, left[1]) : null);
        player.updateInventory();
        recipes.crafted(player, match, result.getAmount());
    }

    // ── shared ─────────────────────────────────────────────────────────────────

    private void later(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** The items with one taken off every stack. */
    private static ItemStack[] oneLess(ItemStack[] items) {
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType().isAir() || item.getAmount() <= 1) continue;
            out[i] = item.clone();
            out[i].setAmount(item.getAmount() - 1);
        }
        return out;
    }

    /**
     * Gives the result to the player: onto the cursor (if it is empty or holds
     * the same item with room left) or, for a shift-click, into the inventory.
     * False, and nothing given, when there is no room.
     */
    private static boolean hand(Player player, ItemStack result, boolean toInventory) {
        if (toInventory) {
            if (room(player, result) < result.getAmount()) return false;
            player.getInventory().addItem(result.clone());
            return true;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType().isAir()) {
            player.setItemOnCursor(result.clone());
            return true;
        }
        if (!cursor.isSimilar(result) || cursor.getAmount() + result.getAmount() > cursor.getMaxStackSize()) return false;
        cursor.setAmount(cursor.getAmount() + result.getAmount());
        player.setItemOnCursor(cursor);
        return true;
    }

    /** How many of the item still fit into the player's inventory. */
    private static int room(Player player, ItemStack item) {
        int room = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) room += item.getMaxStackSize();
            else if (slot.isSimilar(item)) room += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
        }
        return room;
    }

    // ── the crafter block ──────────────────────────────────────────────────────

    /** Nobody stands at a crafter, so a recipe with conditions never works there. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (!(event.getBlock().getState(false) instanceof Crafter crafter)) return;
        ItemStack[] matrix = crafter.getInventory().getContents();
        Crecipe match = recipes.matchCrafting(matrix);
        if (match == null) {
            if (recipes.holdsCitem(matrix) || recipes.isOurs(event.getRecipe())) event.setCancelled(true);
            return;
        }
        ItemStack result = recipes.hasConditions(match) ? null : recipes.result(match, null, null);
        if (result == null) event.setCancelled(true);
        else event.setResult(result);
    }

    // ── cooking ────────────────────────────────────────────────────────────────

    /** A furnace does not light for a custom item it may not cook. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        if (!(event.getBlock().getState(false) instanceof Furnace furnace)) return;
        if (!mayCook(furnace.getInventory().getSmelting(), event.getBlock())) event.setCancelled(true);
    }

    /** A furnace that is already lit, and a campfire, cook nothing out of a custom item they may not cook. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        if (!mayCook(event.getSource(), event.getBlock())) event.setCancelled(true);
    }

    /** A custom item that may not be cooked on a campfire is not put on one. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCampfire(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !"campfire".equals(station(block.getType()))) return;
        if (!mayCook(event.getItem(), block)) event.setUseInteractedBlock(Event.Result.DENY);
    }

    private boolean mayCook(@Nullable ItemStack item, Block block) {
        String citem = item == null ? null : CitemManager.citemIdOf(item);
        String station = station(block.getType());
        return citem == null || station == null || recipes.mayCook(citem, station);
    }

    /** The station name a cooking recipe uses for a block, or null for any other block. */
    @Nullable
    private static String station(Material block) {
        return switch (block) {
            case FURNACE -> "furnace";
            case BLAST_FURNACE -> "blast_furnace";
            case SMOKER -> "smoker";
            case CAMPFIRE, SOUL_CAMPFIRE -> "campfire";
            default -> null;
        };
    }
}
