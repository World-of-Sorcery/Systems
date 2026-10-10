package me.hektortm.woSSystems.systems.citems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.WoSSystems;
import net.kyori.adventure.text.Component;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.interactions.InteractionManager;
import me.hektortm.woSSystems.utils.Keys;
import me.hektortm.wosCore.LangManager;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.WoSCore;
import me.hektortm.wosCore.logging.LogManager;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


public class CitemManager {

    private final DAOHub hub;

    private final LogManager log = new LogManager(new LangManager(WoSCore.getPlugin(WoSCore.class)),WoSCore.getPlugin(WoSCore.class));


    public CitemManager(DAOHub hub) {
        this.hub = hub;
    }


    public void giveCitem(CommandSender s, Player t, String id, Integer amount) {
        if (t == null) {
            Utils.error(s, "general", "error.online");
            return;
        }

        ItemStack itemToGive = hub.getCitemDAO().getCitem(id);

        if (itemToGive == null) {
            Utils.error(s, "citems", "error.not-found");
            return;
        }


        itemToGive.setAmount(amount);
        t.getInventory().addItem(personalize(itemToGive, t));
        t.playSound(t.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1 ,1);
        Utils.success(s, "citems", "given", "%amount%", String.valueOf(amount), "%id%", id, "%player%", t.getName());
    }

    public boolean isCitem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        String itemId = data.get(Keys.ID.get(), PersistentDataType.STRING); // Retrieve the ID

        return hub.getCitemDAO().getCitem(itemId) != null;
    }

    public void updateItem(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        int amount = item.getAmount();
        if (item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();

        if (meta == null) return;


        PersistentDataContainer data = meta.getPersistentDataContainer();
        String itemId = data.get(Keys.ID.get(), PersistentDataType.STRING);
        String uuuid = data.get(Keys.UUUID.get(),  PersistentDataType.STRING);
        if (itemId == null) return;

        ItemStack dbItem = hub.getCitemDAO().getCitem(itemId);

            if (dbItem == null) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                Utils.success(p, "citems", "update.removed", "%item%", meta.getDisplayName());
                p.getInventory().remove(item);
                return;
            }



        if (isUpdateUuidDifferent(item, dbItem)) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            Utils.success(p, "citems", "update.updated", "%item%", meta.getDisplayName());

            // Update the item in hand with the new data
            dbItem.setAmount(amount);
            p.getInventory().setItemInMainHand(personalize(dbItem, p));
        } else if (hasPlaceholders(item)) {
            // Fresh values each time the item is held
            p.getInventory().setItemInMainHand(personalize(item, p));
        }


    }

    /** Whether the item's name or lore has placeholders to fill in. */
    private static boolean hasPlaceholders(ItemStack item) {
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        String pages = data.get(CitemBuilder.KEY_LORE_PAGES, PersistentDataType.STRING);
        return data.has(CitemBuilder.KEY_NAME_TEMPLATE, PersistentDataType.STRING)
                || (pages != null && pages.indexOf('{') >= 0);
    }

    /** The item's name and lore as typed (with their placeholders), if it has any; empty otherwise. */
    public List<String> templates(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return List.of();
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        List<String> out = new ArrayList<>();
        String name = data.get(CitemBuilder.KEY_NAME_TEMPLATE, PersistentDataType.STRING);
        String pages = data.get(CitemBuilder.KEY_LORE_PAGES, PersistentDataType.STRING);
        if (name != null) out.add(name);
        if (pages != null) out.add(pages);
        return out;
    }

    /**
     * The item with the placeholders of its name and lore filled in for
     * {@code p} (changed in place). An item without placeholders is left as it is.
     */
    public ItemStack personalize(ItemStack item, Player p) {
        if (item == null || !item.hasItemMeta() || !hasPlaceholders(item)) return item;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        boolean font = Boolean.TRUE.equals(data.get(CitemBuilder.KEY_CUSTOM_FONT, PersistentDataType.BOOLEAN));

        String name = data.get(CitemBuilder.KEY_NAME_TEMPLATE, PersistentDataType.STRING);
        if (name != null) {
            String resolved = WoSSystems.getInstance().getPlaceholderResolver().resolvePlaceholders(name, p);
            meta.displayName(CitemBuilder.text(resolved, font));
        }
        String pages = data.get(CitemBuilder.KEY_LORE_PAGES, PersistentDataType.STRING);
        if (pages != null) {
            Integer page = data.get(CitemBuilder.KEY_LORE_PAGE, PersistentDataType.INTEGER);
            meta.lore(lorePage(pages, page == null ? 0 : page, font, p));
        }
        item.setItemMeta(meta);
        return item;
    }

    /** One lore page of an item (its pages as stored on it), with placeholders filled in for {@code p}. */
    public List<Component> lorePage(String pagesJson, int page, boolean font, Player p) {
        JsonArray pages = JsonParser.parseString(pagesJson).getAsJsonArray();
        List<String> lines = new ArrayList<>();
        if (page >= 0 && page < pages.size() && pages.get(page).isJsonArray()) {
            for (JsonElement line : pages.get(page).getAsJsonArray()) {
                lines.add(line.getAsString());
            }
        }
        List<Component> lore = new ArrayList<>();
        for (String line : WoSSystems.getInstance().getPlaceholderResolver().resolveLines(lines, p)) {
            lore.add(CitemBuilder.text(line, font));
        }
        return lore;
    }


    public boolean isUpdateUuidDifferent(ItemStack item1, ItemStack item2) {
        String updateUUID1 = item1.getItemMeta().getPersistentDataContainer().get(Keys.UUUID.get(), PersistentDataType.STRING);
        String updateUUID2 = item2.getItemMeta().getPersistentDataContainer().get(Keys.UUUID.get(), PersistentDataType.STRING);

        return !Objects.equals(updateUUID1, updateUUID2);
    }

    /** The citem id stored on an item, or {@code null} if it isn't a citem. */
    public static String citemIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(Keys.ID.get(), PersistentDataType.STRING);
    }

    /** The id of the custom item in the player's main hand, or {@code null} if they hold none. */
    public String heldCitemId(Player p) {
        return citemIdOf(p.getInventory().getItemInMainHand());
    }

    /** How many of the custom item {@code id} the player carries (matched by its id tag). */
    public int countCitem(Player p, String id) {
        int found = 0;
        for (ItemStack item : p.getInventory().getStorageContents()) {
            if (id.equals(citemIdOf(item))) found += item.getAmount();
        }
        return found;
    }

    /**
     * Takes {@code amount} of the custom item {@code id} (matched by its id tag).
     * Takes nothing and returns false if the player has fewer.
     */
    public boolean takeCitem(Player p, String id, int amount) {
        if (countCitem(p, id) < amount) return false;
        ItemStack[] contents = p.getInventory().getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (!id.equals(citemIdOf(item))) continue;
            int take = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        p.getInventory().setStorageContents(contents);
        return true;
    }

    /**
     * Removes up to {@code amount} of the custom item {@code id} (matched by its
     * id tag, so whatever lore page or placeholder values a stack shows) and
     * returns how many were removed: fewer if the player has fewer.
     */
    public int removeCitem(Player p, String id, int amount) {
        int taken = Math.min(amount, countCitem(p, id));
        if (taken <= 0) return 0;
        takeCitem(p, id, taken);
        return taken;
    }

    /**
     * Gives {@code amount} of the custom item {@code id} without messages; what
     * doesn't fit drops at the player's feet. False if the item doesn't exist.
     */
    public boolean addCitem(Player p, String id, int amount) {
        ItemStack item = hub.getCitemDAO().getCitem(id);
        if (item == null) return false;
        item.setAmount(amount);
        for (ItemStack rest : p.getInventory().addItem(personalize(item, p)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        return true;
    }

    public boolean hasCitemAmount(Player p, String id, int amount) {
        if (!hub.getCitemDAO().citemExists(id)) return false;

        int found = 0;

        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null) continue;

            // By id: an item on another lore page, or with its placeholders filled in, still counts
            if (id.equals(citemIdOf(item))) {
                found += item.getAmount();
                if (found >= amount) {
                    return true;
                }
            }
        }

        return false;
    }


    private String parseTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return now.format(formatter);
    }



}