package me.hektortm.woSSystems.systems.citems;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.dao.CitemDAO;
import me.hektortm.woSSystems.utils.Keys;
import me.hektortm.woSSystems.utils.Parsers;
import me.hektortm.wosCore.api.ApiException;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Takes placed custom items out of this server's world after a pull-back took
 * them away (wos-api queues them; the {@code placed_citem} rows are gone
 * already). At start the queue is read; each item's chunk is loaded and held
 * until its entities are in, then the item's display entity and its barrier /
 * coral block are removed and the queue entry is deleted. An item placed again
 * on the same block since is left alone. Entries in a world that isn't loaded
 * stay queued for a later start.
 */
public class PlacedRemovals implements Listener {

    private final WoSSystems plugin;
    private final CitemDAO dao;
    /** Pending removals by chunk ("world:x:z"); main thread. */
    private final Map<String, List<CitemDAO.Removal>> pending = new ConcurrentHashMap<>();

    public PlacedRemovals(WoSSystems plugin, CitemDAO dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    /** Reads the queue off the main thread, then loads the chunks on it. */
    public void start() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<CitemDAO.Removal> removals;
            try {
                removals = dao.placedRemovals();
            } catch (ApiException e) {
                WoSSystems.discordLog(Level.WARNING, "CID:removals", "Could not read the placed items to remove: ", e);
                return;
            }
            if (!removals.isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> queue(removals));
        });
    }

    private void queue(List<CitemDAO.Removal> removals) {
        for (CitemDAO.Removal r : removals) {
            Location block = Parsers.stringToLocation(r.blockLocation());
            if (block == null) continue; // world not loaded: stays queued
            if (dao.isItemDisplay(block)) { // placed again since: it stays
                dao.placedRemovalDone(r.blockLocation());
                continue;
            }
            String key = key(block.getWorld().getName(), block.getBlockX() >> 4, block.getBlockZ() >> 4);
            pending.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        for (String key : List.copyOf(pending.keySet())) {
            String[] k = key.split(":");
            var world = Bukkit.getWorld(k[0]);
            if (world == null) continue;
            world.getChunkAtAsync(Integer.parseInt(k[1]), Integer.parseInt(k[2])).thenAccept(chunk -> {
                chunk.addPluginChunkTicket(plugin); // held until its entities are in
                if (chunk.isEntitiesLoaded()) handle(chunk);
            });
        }
        plugin.getLogger().info("Placed items to remove after a pull-back: " + removals.size());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        if (!pending.isEmpty()) handle(e.getChunk());
    }

    private void handle(Chunk chunk) {
        List<CitemDAO.Removal> list = pending.remove(key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
        if (list == null) return;
        for (CitemDAO.Removal r : list) {
            Location display = Parsers.stringToLocation(r.displayLocation());
            if (display != null) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof ItemDisplay d && d.getLocation().distanceSquared(display) < 0.01
                            && r.citemId().equals(citemId(d.getItemStack()))) {
                        d.remove();
                    }
                }
            }
            Location loc = Parsers.stringToLocation(r.blockLocation());
            if (loc != null) {
                Block block = loc.getBlock();
                // The block placeCitemDisplay set under the display (none for an AIR item)
                if (block.getType() == Material.BARRIER || block.getType() == Material.DEAD_TUBE_CORAL_FAN) {
                    block.setType(Material.AIR);
                }
            }
            dao.placedRemovalDone(r.blockLocation());
            plugin.getLogger().info("Removed placed item " + r.citemId() + " at " + r.blockLocation() + " (pulled back)");
        }
        chunk.removePluginChunkTicket(plugin);
    }

    private static String citemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.ID.get(), PersistentDataType.STRING);
    }

    private static String key(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }
}
