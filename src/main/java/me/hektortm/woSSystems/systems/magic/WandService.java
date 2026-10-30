package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.magic.model.WandSkin;
import me.hektortm.woSSystems.utils.Keys;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * The wand (magic spec §6): one per player, drawn from their wand record. The item is the
 * citem marked {@code wand}, personalised, with the owned skin's model and the colour as
 * {@code custom_model_data} colour 0 (tint source {@code minecraft:custom_model_data}),
 * marked with its owner and made undroppable / unstashable (the existing listeners keep
 * it in the inventory; {@link WandListener} does the rest).
 */
public final class WandService {

    private final WoSSystems plugin;
    private final DAOHub hub;
    private final MagicSystem magic;
    private final Random random = new Random();

    WandService(WoSSystems plugin, DAOHub hub, MagicSystem magic) {
        this.plugin = plugin;
        this.hub = hub;
        this.magic = magic;
    }

    /** Whether the item is a given wand (any owner). */
    public boolean isWand(@Nullable ItemStack item) {
        return owner(item) != null;
    }

    /** Whether the item is this player's own wand. */
    public boolean isOwnWand(@Nullable ItemStack item, Player p) {
        return p.getUniqueId().toString().equals(owner(item));
    }

    @Nullable
    private static String owner(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.WAND_OWNER.get(), PersistentDataType.STRING);
    }

    /** A wand citem item without an owner (handed out by /cgive …): not a valid wand. */
    public boolean isUnboundWandCitem(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(Keys.WAND.get(), PersistentDataType.BOOLEAN) && !pdc.has(Keys.WAND_OWNER.get(), PersistentDataType.STRING);
    }

    public boolean ownsSkin(Player p, WandSkin skin) {
        String u = skin.unlockable();
        return u == null || hub.getUnlockableDAO().getPlayerUnlockable(p, u) || hub.getUnlockableDAO().getPlayerTempUnlockable(p, u);
    }

    /** The wand item for the player's record; null without a record or without a wand citem. */
    @Nullable
    public ItemStack build(Player p) {
        MagicData d = magic.players().data(p);
        MagicData.Wand w = d == null ? null : d.wand();
        String citemId = hub.getCitemDAO().wandId();
        if (w == null || citemId == null) return null;
        ItemStack base = hub.getCitemDAO().getCitem(citemId);
        if (base == null) return null;
        ItemStack item = plugin.getCitemManager().personalize(base.clone(), p);
        ItemMeta meta = item.getItemMeta();
        WandSkin skin = w.skin().isEmpty() ? null : magic.stores().skin(w.skin());
        boolean owned = skin != null && ownsSkin(p, skin);
        NamespacedKey own = meta.getItemModel();
        String model = WandRules.model(skin, owned, own == null ? null : own.toString());
        if (model != null) meta.setItemModel(NamespacedKey.fromString(model));
        if (WandRules.tinted(skin, owned)) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setColors(List.of(Color.fromRGB(WandRules.rgb(w.color()))));
            meta.setCustomModelDataComponent(cmd);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.WAND_OWNER.get(), PersistentDataType.STRING, p.getUniqueId().toString());
        pdc.set(Keys.UNDROPPABLE.get(), PersistentDataType.BOOLEAN, true);
        pdc.set(Keys.UNSTASHABLE.get(), PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * {@code wand give}: creates the player's wand record (a random colour from the
     * settings' range) when there is none, then gives the item. False when there is no
     * wand citem in the portal or the player's data isn't loaded.
     */
    public boolean give(Player p) {
        MagicData d = magic.players().data(p);
        if (d == null) return false;
        if (d.wand() == null) {
            d.setWand(new MagicData.Wand(MagicRules.wandColor(magic.stores().settings().wandColor(), random), ""));
            magic.players().flush(p.getUniqueId());
        }
        return restore(p);
    }

    /** Takes every wand item out of the inventory and gives the one drawn from the record. */
    public boolean restore(Player p) {
        ItemStack item = build(p);
        if (item == null) return false;
        PlayerInventory inv = p.getInventory();
        int slot = removeWands(p);
        if (slot >= 0 && inv.getItem(slot) == null) inv.setItem(slot, item);
        else if (!inv.addItem(item).isEmpty()) p.getWorld().dropItem(p.getLocation(), item); // full: it can't be dropped again by hand
        return true;
    }

    /** Sets the wand's skin ("" = the default look) and redraws it; false without a wand record. */
    public boolean setSkin(Player p, String skin) {
        MagicData d = magic.players().data(p);
        if (d == null || d.wand() == null) return false;
        d.setWand(new MagicData.Wand(d.wand().color(), skin == null ? "" : skin));
        magic.players().flush(p.getUniqueId());
        refresh(p);
        return true;
    }

    /** Sets the wand's colour ({@code #RRGGBB}, or {@code random} from the settings' range) and redraws it. */
    public boolean setColor(Player p, String color) {
        MagicData d = magic.players().data(p);
        if (d == null || d.wand() == null) return false;
        String hex = "random".equalsIgnoreCase(color) ? MagicRules.wandColor(magic.stores().settings().wandColor(), random) : color.toUpperCase(java.util.Locale.ROOT);
        if (!WandRules.isColor(hex)) return false;
        d.setWand(new MagicData.Wand(hex, d.wand().skin()));
        magic.players().flush(p.getUniqueId());
        refresh(p);
        return true;
    }

    /** Redraws the wands in the inventory after the record changed (colour, skin, item edited). */
    public void refresh(Player p) {
        MagicData d = magic.players().data(p);
        if (d == null || d.wand() == null) return;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isOwnWand(inv.getItem(i), p)) {
                ItemStack fresh = build(p);
                if (fresh != null) inv.setItem(i, fresh);
            }
        }
    }

    /** Removes every wand item (and unbound wand citem) from the inventory; the first slot one was in, or -1. */
    public int removeWands(Player p) {
        PlayerInventory inv = p.getInventory();
        int first = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isWand(it) || isUnboundWandCitem(it)) {
                if (first < 0) first = i;
                inv.setItem(i, null);
            }
        }
        return first;
    }

    /** Removes wands that aren't the holder's own (spec §6.1); true when one was found. */
    public boolean removeForeign(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean found = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if ((isWand(it) && !isOwnWand(it, p)) || isUnboundWandCitem(it)) {
                inv.setItem(i, null);
                found = true;
            }
        }
        return found;
    }

    /** Whether the player holds their own wand in the main hand. */
    public boolean holdsWand(Player p) {
        return isOwnWand(p.getInventory().getItemInMainHand(), p);
    }
}
