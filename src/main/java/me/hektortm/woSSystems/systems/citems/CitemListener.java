package me.hektortm.woSSystems.systems.citems;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.interactions.InteractionManager;
import me.hektortm.woSSystems.utils.Keys;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static me.hektortm.woSSystems.systems.interactions.InterListener.buildKey;

public class CitemListener implements Listener {
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final CitemManager citemManager = plugin.getCitemManager();
    private final InteractionManager interactionManager = plugin.getInteractionManager();
    private final CitemDisplays citemDisplays = plugin.getCitemDisplays();
    private final DAOHub hub;
    private final Map<UUID, Long> displayCooldowns = new HashMap<>();


    public CitemListener(DAOHub hub) {
        this.hub = hub;
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action action = e.getAction();
        ItemStack item = e.getItem();

        Location locClicked = getClickedLocation(e);

        if (locClicked != null &&
                isEmptyHandInteraction(e, p) &&
                isDisplayBlock(e.getClickedBlock())) {

            handleDisplayInteraction(e, p, action, locClicked);
            return;
        }

        if (citemManager.isCitem(item)) {
            handleCitemPlacement(e, p, item, action);
            handleCitemActions(e, p, action, item);
        }
    }

    /** Whether the item has the unusable flag: its actions run, but it can't be placed, eaten, thrown … */
    private static boolean isUnusable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer()
                .get(Keys.UNUSABLE.get(), PersistentDataType.BOOLEAN));
    }

    /** An unusable item is not used on a mob or another entity either (name tag, shears, lead …). */
    @EventHandler
    public void onUseOnEntity(PlayerInteractEntityEvent e) {
        if (isUnusable(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    // Whatever gets past the click (a client that places or eats anyway) is stopped here.
    @EventHandler
    public void onPlaceUnusable(BlockPlaceEvent e) {
        if (isUnusable(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler
    public void onConsumeUnusable(PlayerItemConsumeEvent e) {
        if (isUnusable(e.getItem())) e.setCancelled(true);
    }

    private Location getClickedLocation(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return null;
        return e.getClickedBlock().getLocation();
    }

    private boolean isEmptyHandInteraction(PlayerInteractEvent e, Player p) {
        return p.getInventory().getItemInMainHand().getType().isAir();
    }

    private boolean isDisplayBlock(Block block) {
        if (block == null) return false;
        return block.getType() == Material.BARRIER || block.getType() == Material.DEAD_TUBE_CORAL_FAN;
    }

    private void handleDisplayInteraction(PlayerInteractEvent e, Player p, Action action, Location loc) {
        if (!hub.getCitemDAO().isItemDisplay(loc)) return;

        if (action.isRightClick() && !p.isSneaking()) handleRightClickDisplay(e, p, loc);
        if (action.isRightClick() && p.isSneaking()) handleSneakRightClickDisplay(e, p, loc);
    }

    private void handleRightClickDisplay(PlayerInteractEvent e, Player p, Location loc) {
        // One cooldown for the whole click: the event fires once per hand
        if (isOnCooldown(p)) return;
        updateCooldown(p);

        triggerInteractionForDisplay(p, loc);
    }

    /** Runs the placed item's "placed" interaction, if it has one. */
    private void triggerInteractionForDisplay(Player p, Location loc) {
        String id = hub.getCitemDAO().getItemDisplayID(loc);
        ItemStack citem = id == null ? null : hub.getCitemDAO().getCitem(id);
        if (citem == null || !citem.hasItemMeta()) return;

        PersistentDataContainer data = citem.getItemMeta().getPersistentDataContainer();
        String interId = data.get(Keys.PLACED_ACTION.get(), PersistentDataType.STRING);

        if (interId == null) {
            return;
        }

        plugin.getInteractionManager().triggerInteraction(interId, p, buildKey(loc));
    }

    private void handleSneakRightClickDisplay(PlayerInteractEvent e, Player p, Location loc) {

        if (!hub.getCitemDAO().isItemDisplay(loc)) return;
        if (hub.getCitemDAO().isCreativePlaced(loc) && p.getGameMode() != GameMode.CREATIVE) return;

        if (!hub.getCitemDAO().isItemDisplayOwner(loc, p.getUniqueId()) &&
                p.getGameMode() != GameMode.CREATIVE) {

            p.sendMessage("You are not the owner of this display.");
            return;
        }

        if (isOnCooldown(p)) return;
        updateCooldown(p);

        String id = hub.getCitemDAO().getItemDisplayID(loc);
        ItemStack give = hub.getCitemDAO().getCitem(id);
        give.setAmount(1);

        citemDisplays.removeEntityAtLocation(hub.getCitemDAO().getDisplayLocation(loc));

        if (p.getGameMode() == GameMode.CREATIVE) {
            hub.getCitemDAO().removeItemDisplay(hub.getCitemDAO().getUUID(loc), loc);
        } else {
            hub.getCitemDAO().removeItemDisplay(p.getUniqueId(), loc);
        }

        e.getClickedBlock().setType(Material.AIR);
        citemDisplays.spawnPickupParticle(loc);
        p.playSound(loc, Sound.BLOCK_CHISELED_BOOKSHELF_PICKUP, 1, 1);
        p.getInventory().addItem(citemManager.personalize(give, p));
    }

    private void handleCitemPlacement(PlayerInteractEvent e, Player p, ItemStack item, Action action) {

        if (!action.isRightClick()) return;

        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        int placeable = data.get(Keys.PLACEABLE.get(), PersistentDataType.INTEGER) != null
                ? data.get(Keys.PLACEABLE.get(), PersistentDataType.INTEGER)
                : 0;

        if (placeable <= 0) return;
        if (e.getClickedBlock() == null) return;

        Block target = e.getClickedBlock();
        Block above = target.getRelative(BlockFace.UP);

        if (!above.getType().isAir()) return;

        Location spawnLoc = target.getLocation().add(0, 1, 0);

        if (hub.getCitemDAO().isItemDisplay(spawnLoc)) return;
        if (isOnCooldown(p)) return;
        updateCooldown(p);

        // The item becomes a display: a block item (a head, a banner) must not also be placed as a block
        e.setCancelled(true);
        placeCitemDisplay(p, spawnLoc, data, item);

        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    private void placeCitemDisplay(Player p, Location loc, PersistentDataContainer data, ItemStack item) {

        String id = data.get(Keys.ID.get(), PersistentDataType.STRING);

        citemDisplays.spawnItemDisplay(loc, id, p);

        int type = data.get(Keys.PLACEABLE.get(), PersistentDataType.INTEGER);

        Material mat = type == CitemRules.PLACE_SMALL ? Material.DEAD_TUBE_CORAL_FAN :
                type == CitemRules.PLACE_NORMAL ? Material.BARRIER : Material.AIR;

        Block block = loc.getBlock();
        block.setType(mat);

        if (block.getBlockData() instanceof Waterlogged wL) {
            wL.setWaterlogged(false);
            block.setBlockData(wL);
        }

        p.playSound(loc, Sound.BLOCK_CANDLE_PLACE, 1, 1);
    }

    private void handleCitemActions(PlayerInteractEvent e, Player p, Action action, ItemStack item) {

        switch (action) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> rightClickAction(p);
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> leftClickAction(p);
        }

        // Unusable: the item itself does nothing (no placing, eating, throwing, equipping).
        // The clicked block still reacts (a door opens), and the actions above have run.
        if (action.isRightClick() && isUnusable(item)) {
            e.setUseItemInHand(Event.Result.DENY);
        }
    }

    private boolean isOnCooldown(Player p) {
        long current = System.currentTimeMillis();
        long last = displayCooldowns.getOrDefault(p.getUniqueId(), 0L);
        return current - last < 250;
    }

    private void updateCooldown(Player p) {
        displayCooldowns.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public void leftClickAction(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        String actionId = data.get(Keys.LEFT_ACTION.get(), PersistentDataType.STRING);
        if (actionId != null) {
            interactionManager.triggerByClick(actionId, p, null);
        }
    }

    public void rightClickAction(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        String actionId = data.get(Keys.RIGHT_ACTION.get(), PersistentDataType.STRING);
        if (actionId != null) {
            interactionManager.triggerByClick(actionId, p, null);
        }
    }

    @EventHandler
    public void onSwapHands(InventoryClickEvent e) {
        if (e.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack item = e.getCurrentItem();
            if (!citemManager.isCitem(item)) {
                return;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }


            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String pagesJson = pdc.get(CitemBuilder.KEY_LORE_PAGES, PersistentDataType.STRING);
            if (pagesJson == null || !(e.getWhoClicked() instanceof Player p)) {
                return;
            }
            JsonArray pages = JsonParser.parseString(pagesJson).getAsJsonArray();
            if (pages.size() < 2) {
                return; // one page: nothing to switch, F swaps hands as usual
            }

            e.setCancelled(true);

            Integer currentRaw = pdc.get(CitemBuilder.KEY_LORE_PAGE, PersistentDataType.INTEGER);
            int current = currentRaw != null ? currentRaw : 0;
            int next = (current + 1) % pages.size();
            boolean font = Boolean.TRUE.equals(pdc.get(CitemBuilder.KEY_CUSTOM_FONT, PersistentDataType.BOOLEAN));

            meta.lore(citemManager.lorePage(pagesJson, next, font, p));
            pdc.set(CitemBuilder.KEY_LORE_PAGE, PersistentDataType.INTEGER, next);
            // Back into the slot it was clicked in (never the main hand: that overwrote the held item)
            item.setItemMeta(meta);
            e.setCurrentItem(item);
        }
    }


}
