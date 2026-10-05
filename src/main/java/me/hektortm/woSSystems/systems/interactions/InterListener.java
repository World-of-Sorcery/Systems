package me.hektortm.woSSystems.systems.interactions;


import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.citems.CitemManager;
import me.hektortm.woSSystems.utils.model.Interaction;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;

public class InterListener implements Listener {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final InteractionManager interactionManager = plugin.getInteractionManager();
    private final CitemManager citemManager = plugin.getCitemManager();
    private final DAOHub hub;

    public InterListener(DAOHub hub) {
        this.hub = hub;
    }
    

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = e.getClickedBlock();
            if (e.isCancelled()) {
                return; // Event already canceled
            }
            if (block == null) return;

            Location blockLocation = block.getLocation();

            // Process interactions
            String bound = hub.getInteractionDAO().getBound(blockLocation);
            if (bound != null) {
                e.setCancelled(true);
                if (touchOnly(bound)) return;
                interactionManager.triggerByClick(bound, p, buildKey(blockLocation));
            }

        }
    }

    @EventHandler
    public void NPCClick(NPCRightClickEvent e) {
        int npcid = e.getNPC().getId();
        String id = hub.getInteractionDAO().getNpcBound(npcid);
        if (touchOnly(id)) return;
        InteractionKey key = new InteractionKey("npc:"+npcid);
        interactionManager.triggerByClick(id, e.getClicker(), key);
    }

    /** An interaction with a touch display is run through that display, not by clicking the block or NPC. */
    private boolean touchOnly(String interactionId) {
        if (interactionId == null) return false;
        Interaction inter = hub.getInteractionDAO().getInteractionByID(interactionId);
        return inter != null && inter.isTouchOnly();
    }

    public static InteractionKey buildKey (Location loc) {
        String world = loc.getWorld().getName();
        String x = String.valueOf(loc.getX());
        String y = String.valueOf(loc.getY());
        String z = String.valueOf(loc.getZ());

        return new InteractionKey("block:" + world + ":" + x + ":" + y + ":" + z);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (hub.getCitemDAO().isItemDisplay(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (hub.getInteractionDAO().getAllBlockLocations().contains(block.getLocation())) {
            event.setCancelled(true);
            return;
        }
    }
}
