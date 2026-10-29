package me.hektortm.woSSystems.systems.magic;

import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps wands bound (magic spec §6.1). Dropping and containers are refused by the existing
 * undroppable / unstashable listeners; here: item frames and armor stands, death (the wand
 * stays: it is taken out of the drops and given back on respawn), picking up a wand, and
 * wands that aren't the holder's own (removed at join and in the periodic check).
 */
final class WandListener implements Listener {

    private final Plugin plugin;
    private final WandService wands;
    private final Set<UUID> diedWithWand = ConcurrentHashMap.newKeySet();

    WandListener(Plugin plugin, WandService wands) {
        this.plugin = plugin;
        this.wands = wands;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame && wands.isWand(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (wands.isWand(e.getPlayerItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        boolean had = e.getDrops().removeIf(wands::isWand);
        if (e.getKeepInventory()) return;
        if (had) diedWithWand.add(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (diedWithWand.remove(p.getUniqueId())) plugin.getServer().getScheduler().runTask(plugin, () -> wands.restore(p));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        ItemStack item = e.getItem().getItemStack();
        if (!wands.isWand(item) && !wands.isUnboundWandCitem(item)) return;
        if (e.getEntity() instanceof Player p && wands.isOwnWand(item, p)) return; // restored onto the ground with a full inventory
        e.setCancelled(true);
        e.getItem().remove(); // a wand on the ground is never someone's to take
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) wands.removeForeign(p);
        }, 20L);
    }
}
