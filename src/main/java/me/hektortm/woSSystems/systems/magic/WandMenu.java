package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.systems.magic.model.WandSkin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The wand menu ({@code /wand}, spec §6.1): the default look and every skin, owned ones
 * pickable (owning is the skin's unlockable). The current one is marked.
 */
public final class WandMenu implements Listener {

    private final MagicSystem magic;

    WandMenu(MagicSystem magic) {
        this.magic = magic;
    }

    private static final class Menu implements InventoryHolder {
        final Player player;
        final List<String> skins = new ArrayList<>(); // by slot; "" = default
        Inventory inv;

        Menu(Player player) {
            this.player = player;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    public void open(Player p) {
        MagicData d = magic.players().data(p);
        if (d == null || d.wand() == null) return;
        List<WandSkin> skins = new ArrayList<>(magic.stores().skins());
        skins.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        Menu m = new Menu(p);
        int size = Math.min(54, Math.max(9, (int) Math.ceil((skins.size() + 1) / 9.0) * 9));
        m.inv = Bukkit.createInventory(m, size, MagicText.component("wandmenu.title"));
        String current = d.wand().skin();
        m.skins.add("");
        m.inv.setItem(0, item(Material.STICK, null, MagicText.text("wandmenu.default"), current.isEmpty(), true));
        for (WandSkin s : skins) {
            if (m.skins.size() >= size) break;
            boolean owned = magic.wands().ownsSkin(p, s);
            m.inv.setItem(m.skins.size(), item(Material.BLAZE_ROD, s.itemModel(), s.displayName(), s.id().equals(current), owned));
            m.skins.add(s.id());
        }
        p.openInventory(m.inv);
    }

    private static ItemStack item(Material material, String model, String name, boolean current, boolean owned) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (model != null && !model.isBlank()) meta.setItemModel(NamespacedKey.fromString(model.contains(":") ? model : "wos:" + model));
        meta.displayName(text(name));
        List<Component> lore = new ArrayList<>();
        if (current) lore.add(text(MagicText.text("wandmenu.current")));
        if (!owned) lore.add(text(MagicText.text("wandmenu.locked")));
        meta.lore(lore);
        if (current) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= m.skins.size()) return;
        String skin = m.skins.get(raw);
        Player p = m.player;
        if (!skin.isEmpty()) {
            WandSkin s = magic.stores().skin(skin);
            if (s == null || !magic.wands().ownsSkin(p, s)) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                return;
            }
        }
        magic.wands().setSkin(p, skin);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 1.2f);
        p.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
