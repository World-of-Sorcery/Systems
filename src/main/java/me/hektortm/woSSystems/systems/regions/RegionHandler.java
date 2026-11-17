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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        bossbar.removeBossBar(event.getPlayer());
    }

    /** Works out the regions at {@code at}: the boss bar, the player's region and the enter interactions. */
    public void updateRegion(Player player, Location at) {
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(at.getWorld()));

        if (regionManager == null) {
            bossbar.updateBossBar(player, ""); // Clear bossbar if no regions
            plugin.getPlayerRegions().remove(player.getUniqueId()); // Remove the player from tracked regions
            return;
        }

        // WorldGuard hands the regions over highest priority first, a child before its
        // parent; regions of equal priority that are not parented come in no fixed order.
        ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(at));
        List<String> before = plugin.getPlayerRegions().getOrDefault(player.getUniqueId(), List.of());
        List<String> now = new ArrayList<>();

        for (ProtectedRegion region : regions) {
            String id = region.getId();
            now.add(id);
            // Each region's own enter interaction, once, when this player walks in.
            String enterInteraction = region.getFlag(WoSSystems.ENTER_INTERACTION);
            if (enterInteraction != null && !before.contains(id)) {
                plugin.getInteractionManager().triggerInteraction(enterInteraction, player, null);
            }
        }

        // The name WorldGuard itself would pick: the most specific region that sets it.
        bossbar.updateBossBar(player, displayName(regions, player));

        // Quests waiting for a region to be entered or left.
        for (String id : now) if (!before.contains(id)) me.hektortm.woSSystems.systems.quests.QuestHooks.report(player, "region", "entered", java.util.Map.of("region", id));
        for (String id : before) if (!now.contains(id)) me.hektortm.woSSystems.systems.quests.QuestHooks.report(player, "region", "left", java.util.Map.of("region", id));
        plugin.getPlayerRegions().put(player.getUniqueId(), List.copyOf(now));
    }

    /**
     * The {@code display-name} of the spot, resolved the way WorldGuard resolves its own flags:
     * the highest priority wins, a child region overrides its parent and inherits it when unset.
     * Reading each region's flag directly ignores all of that.
     */
    private static String displayName(ApplicableRegionSet regions, Player player) {
        String name = regions.queryValue(WorldGuardPlugin.inst().wrapPlayer(player), WoSSystems.DISPLAY_NAME);
        return name != null ? name : "";
    }

    public static String getRegionDisplayName(Player player) {
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(localPlayer.getWorld());

        if (regionManager == null) {
            return "§6Unknown";
        }

        BlockVector3 blockVector3 = localPlayer.getBlockLocation().toVector().toBlockPoint();
        String displayName = displayName(regionManager.getApplicableRegions(blockVector3), player);
        return !displayName.isEmpty() ? displayName : "§6Unknown";
    }
}
