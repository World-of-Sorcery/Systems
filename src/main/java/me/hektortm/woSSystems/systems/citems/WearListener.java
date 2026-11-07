package me.hektortm.woSSystems.systems.citems;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.utils.Keys;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The unwearable flag: an item with it can't be worn, whatever it is (armor,
 * a head, an item with the equippable component).
 */
public class WearListener implements Listener {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);

    static boolean isUnwearable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && Boolean.TRUE.equals(
                meta.getPersistentDataContainer().get(Keys.UNWEARABLE.get(), PersistentDataType.BOOLEAN));
    }

    /** Whether the game would put the item on: armor and heads, or the equippable component. */
    private static boolean isWearable(ItemStack item) {
        return item.getType().getEquipmentSlot().isArmor() || item.getItemMeta().hasEquippable();
    }

    /** Right-clicking with armor in hand puts it on: stopped here, so nothing is swapped. */
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick() || !isUnwearable(e.getItem()) || !isWearable(e.getItem())) return;
        // A block item (a head) clicked on a block is placed, not worn
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem().getType().isBlock()) return;
        e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler
    public void onDispense(BlockDispenseArmorEvent e) {
        if (isUnwearable(e.getItem())) e.setCancelled(true);
    }

    /**
     * Every other way into an armor slot (clicking, shift-clicking, dragging,
     * number keys, commands): the item goes back to the inventory.
     */
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent e) {
        if (!isUnwearable(e.getNewItem())) return;
        Player p = e.getPlayer();
        EquipmentSlot slot = e.getSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack worn = p.getInventory().getItem(slot);
            if (!isUnwearable(worn)) return; // already taken off again
            p.getInventory().setItem(slot, null);
            plugin.getItemDelivery().give(p, worn, "Taken off: can't be worn");
        });
    }
}
