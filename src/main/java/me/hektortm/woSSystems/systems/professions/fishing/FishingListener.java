package me.hektortm.woSSystems.systems.professions.fishing;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.interactions.InteractionManager;
import me.hektortm.woSSystems.utils.model.FishingItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class FishingListener implements Listener {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final InteractionManager interactionManager = plugin.getInteractionManager();

    public FishingListener(DAOHub hub) {
        this.hub = hub;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            Player player = event.getPlayer();
            String region = plugin.getPlayerRegions().get(player.getUniqueId());
            String rarity = getRandomRarity();
            event.setExpToDrop(0);
            try {
                FishingItem fishingItem = hub.getFishingDAO().getRandomItemByRarityAndRegion(rarity, region);

                if (fishingItem != null) {
                    Item caughtItemEntity = (Item) event.getCaught();
                    String id = fishingItem.getCitem();

                    if (hub.getCitemDAO().citemExists(id)) {
                        ItemStack citem = hub.getCitemDAO().getCitem(id);

                        if (citem != null) {
                            caughtItemEntity.setItemStack(citem);
                            me.hektortm.woSSystems.systems.quests.QuestHooks.report(player, "fishing", "caught", java.util.Map.of("fish", fishingItem.getId()));
                            interactionManager.triggerInteraction(fishingItem.getInteraction(), player, null);
                        } else fallback(player, event);
                    } else fallback(player, event);
                } else fallback(player, event);
            } catch (Exception e) {
                Bukkit.getLogger().severe("[" + player.getName() + "] Error while fishing: " + e.getMessage());
            }
        }
    }

    private void fallback(Player p, PlayerFishEvent e ){
        Item caughtItemEntity = (Item) e.getCaught();
        ItemStack fallback = new ItemStack(Material.AIR);
        caughtItemEntity.setItemStack(fallback);
        p.sendMessage("§7Your line broke...");
    }

    private String getRandomRarity() {
        Random random = new Random();
        float chance = random.nextFloat(100);

        if (chance < 50) {
            return "COMMON";          // 50% chance (0-49.999...)
        } else if (chance < 80) {
            return "UNCOMMON";        // 30% chance (50-79.999...)
        } else if (chance < 95) {
            return "RARE";            // 15% chance (80-94.999...)
        } else if (chance < 99) {
            return "EPIC";            // 4% chance (95-98.999...)
        } else if (chance < 99.7) {
            return "LEGENDARY";       // 0.7% chance (99-99.699...)
        } else if (chance < 99.97) {
            return "ANCIENT";         // 0.27% chance (99.7-99.969...)
        } else {
            return "MYTHIC";          // 0.03% chance (99.97-100)
        }
    }
}