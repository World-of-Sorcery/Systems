package me.hektortm.woSSystems.systems.regions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.hektortm.woSSystems.WoSSystems;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RegionHandler implements Listener {

    /** A block of a world. */
    private record Spot(UUID world, int x, int y, int z) {
        static Spot of(Location at) {
            return new Spot(at.getWorld().getUID(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
        }
    }

    private final RegionBossBar bossbar;
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    /** player → the block their regions were last worked out for. */
    private final Map<UUID, Spot> lastSpot = new HashMap<>();
    /** player → the regions they are in. */
    private final Map<UUID, Set<String>> inside = new HashMap<>();

    public RegionHandler(RegionBossBar bossbar) {
        this.bossbar = bossbar;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // A move event comes for every step and every turn of the head; a region
        // is made of blocks, so nothing changes until the player is in another one.
        Spot spot = Spot.of(event.getTo());
        if (spot.equals(lastSpot.put(player.getUniqueId(), spot))) return;
        updateRegion(player, event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastSpot.remove(uuid);
        inside.remove(uuid);
        bossbar.removeBossBar(event.getPlayer());
    }

    /** Works out the regions at {@code at}: the boss bar, the player's region and the enter interactions. */
    public void updateRegion(Player player, Location at) {
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(at.getWorld()));

        if (regionManager == null) {
            bossbar.updateBossBar(player, ""); // Clear bossbar if no regions
            plugin.getPlayerRegions().remove(player.getUniqueId()); // Remove the player from tracked regions
            inside.remove(player.getUniqueId());
            return;
        }

        ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(at));
        Set<String> before = inside.getOrDefault(player.getUniqueId(), Set.of());
        Set<String> now = new HashSet<>();

        String newRegionId = null; // Track the region with a display-name flag

        for (ProtectedRegion region : regions) {
            String displayName = region.getFlag(WoSSystems.DISPLAY_NAME);
            String enterInteraction = region.getFlag(WoSSystems.ENTER_INTERACTION);
            newRegionId = region.getId();
            now.add(newRegionId);
            if (displayName != null) {
                bossbar.updateBossBar(player, displayName);
            }
            // Once, when this player walks in — not again while they are inside.
            if (enterInteraction != null && !before.contains(newRegionId)) {
                plugin.getInteractionManager().triggerInteraction(enterInteraction, player, null);
            }
        }

        if (newRegionId == null) {
            bossbar.updateBossBar(player, ""); // Clear the bossbar if no region with display-name
        }
        inside.put(player.getUniqueId(), now);
        plugin.getPlayerRegions().put(player.getUniqueId(), newRegionId);
    }

    public static String getRegionDisplayName(Player player) {
        LocalPlayer localPlayer = WorldGuardPlugin.getPlugin(WorldGuardPlugin.class).wrapPlayer(player);
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld());

        if (regionManager == null) {
            return "§6Unknown";
        }

        BlockVector3 blockVector3 = localPlayer.getBlockLocation().toVector().toBlockPoint();
        ApplicableRegionSet regions = regionManager.getApplicableRegions(blockVector3);
        String displayName = null;
        for (ProtectedRegion region : regions) {
            displayName = region.getFlag(WoSSystems.DISPLAY_NAME);

        }
        return displayName != null ? displayName : "§6Unknown";
    }
}
