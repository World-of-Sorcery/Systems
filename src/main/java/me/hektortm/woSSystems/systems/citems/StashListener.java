package me.hektortm.woSSystems.systems.citems;

import me.hektortm.woSSystems.utils.Keys;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Set;

/**
 * The unstashable flag: an item with it stays with the player. It can't be put
 * into a chest or any other container, a bundle, or be picked up by a hopper.
 */
public class StashListener implements Listener {

    /** Opened inventories that hold nothing: the player's own, and work stations that hand everything back when closed. */
    private static final Set<InventoryType> NOT_STORAGE = EnumSet.of(
            InventoryType.CRAFTING, InventoryType.CREATIVE, InventoryType.PLAYER,
            InventoryType.WORKBENCH, InventoryType.ANVIL, InventoryType.ENCHANTING, InventoryType.GRINDSTONE,
            InventoryType.SMITHING, InventoryType.STONECUTTER, InventoryType.LOOM, InventoryType.CARTOGRAPHY,
            InventoryType.MERCHANT);

    /** Whether items left in an inventory of this type stay there (a chest, a furnace, an ender chest …). */
    static boolean stores(InventoryType type) {
        return !NOT_STORAGE.contains(type);
    }

    public static boolean isUnstashable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && Boolean.TRUE.equals(
                meta.getPersistentDataContainer().get(Keys.UNSTASHABLE.get(), PersistentDataType.BOOLEAN));
    }

    private static boolean isBundle(ItemStack item) {
        return item != null && Tag.ITEMS_BUNDLES.isTagged(item.getType());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack clicked = e.getCurrentItem();

        // A bundle is a container too, in any inventory
        if ((isBundle(cursor) && isUnstashable(clicked)) || (isBundle(clicked) && isUnstashable(cursor))) {
            e.setCancelled(true);
            return;
        }
        if (!stores(e.getView().getTopInventory().getType())) return;

        boolean inContainer = e.getClickedInventory() == e.getView().getTopInventory();
        if (inContainer ? entersBySlot(e, cursor) : e.isShiftClick() && isUnstashable(clicked)) {
            e.setCancelled(true);
        }
    }

    /** A click on a container slot that would put an unstashable item into it. */
    private static boolean entersBySlot(InventoryClickEvent e, ItemStack cursor) {
        if (e.getClick() == ClickType.NUMBER_KEY) {
            return isUnstashable(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()));
        }
        if (e.getClick() == ClickType.SWAP_OFFHAND) {
            return isUnstashable(e.getWhoClicked().getInventory().getItemInOffHand());
        }
        return isUnstashable(cursor);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!isUnstashable(e.getOldCursor()) || !stores(e.getView().getTopInventory().getType())) return;
        int containerSlots = e.getView().getTopInventory().getSize();
        if (e.getRawSlots().stream().anyMatch(slot -> slot < containerSlots)) e.setCancelled(true);
    }

    /** A hopper (or hopper minecart) under a dropped item. */
    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (isUnstashable(e.getItem().getItemStack())) e.setCancelled(true);
    }
}
