package me.hektortm.woSSystems.systems.magic.ring;

import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicRules;
import me.hektortm.woSSystems.systems.magic.MagicSystem;
import me.hektortm.woSSystems.systems.magic.MagicText;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The spellbook (magic spec §5.3): which known spell sits on which ring slot. Rows 1–4 list
 * the known spells; row 5 is page 1's eight slots, row 6 page 2's. Click a spell, then a
 * slot, to put it there; right-click a slot to empty it. Positions are fixed per page (top
 * first, then clockwise), whatever hotbar slot the wand is in.
 */
public final class SpellbookMenu implements Listener {

    private static final int KNOWN = 36;
    private static final int PAGE1 = 36;
    private static final int PAGE2 = 45;

    private final MagicSystem magic;

    public SpellbookMenu(MagicSystem magic) {
        this.magic = magic;
    }

    private static final class Book implements InventoryHolder {
        final Player player;
        @Nullable
        String picked;
        Inventory inv;

        Book(Player player) {
            this.player = player;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    public void open(Player p) {
        if (magic.players().data(p) == null) return;
        Book b = new Book(p);
        b.inv = Bukkit.createInventory(b, 54, MagicText.component("book.title"));
        draw(b);
        p.openInventory(b.inv);
    }

    private void draw(Book b) {
        MagicData d = magic.players().data(b.player);
        if (d == null) return;
        b.inv.clear();
        List<String> known = d.spells();
        for (int i = 0; i < known.size() && i < KNOWN; i++) {
            String id = known.get(i);
            ItemStack item = spellItem(id, Material.ENCHANTED_BOOK, d, MagicText.text("book.known_lore"));
            if (id.equals(b.picked)) {
                ItemMeta m = item.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                m.lore(List.of(plain(MagicText.text("book.picked"))));
                item.setItemMeta(m);
            }
            b.inv.setItem(i, item);
        }
        if (known.size() > KNOWN) b.inv.setItem(KNOWN - 1, named(Material.PAPER, MagicText.text("book.more", "%count%", String.valueOf(known.size() - KNOWN + 1))));
        String[] loadout = d.loadout();
        for (int page = 0; page < MagicRules.PAGES; page++) {
            int row = page == 0 ? PAGE1 : PAGE2;
            for (int pos = 0; pos < MagicRules.SLOTS; pos++) {
                String id = loadout[page * MagicRules.SLOTS + pos];
                String lore = MagicText.text("book.slot_lore", "%page%", String.valueOf(page + 1), "%slot%", String.valueOf(pos + 1));
                b.inv.setItem(row + pos, id.isEmpty() || !d.knows(id)
                        ? named(Material.GRAY_STAINED_GLASS_PANE, MagicText.text("book.empty"), lore)
                        : spellItem(id, Material.BOOK, d, lore));
            }
            b.inv.setItem(row + MagicRules.SLOTS, named(Material.NAME_TAG, MagicText.text("book.page", "%page%", String.valueOf(page + 1))));
        }
    }

    private ItemStack spellItem(String id, Material material, MagicData d, String extraLore) {
        SpellDef s = magic.stores().spell(id);
        List<Component> lore = new ArrayList<>();
        if (s != null && !s.description().isBlank()) lore.add(plain(s.description()));
        if (s != null && s.maxLevel() > 1) {
            MagicData.Progress pr = d.progress(id);
            int level = Math.min(pr.level(), s.maxLevel());
            lore.add(plain(level >= s.maxLevel()
                    ? MagicText.text("book.level_max", "%level%", String.valueOf(level))
                    : MagicText.text("book.level", "%level%", String.valueOf(level), "%max%", String.valueOf(s.maxLevel()),
                            "%xp%", MagicRules.manaText(pr.xp()), "%next%", MagicRules.manaText(s.xpToNext(level)))));
        }
        lore.add(plain(extraLore));
        ItemStack item = named(material, s == null ? id : s.name());
        ItemMeta m = item.getItemMeta();
        m.lore(lore);
        item.setItemMeta(m);
        return item;
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta m = item.getItemMeta();
        m.displayName(plain(name));
        List<Component> l = new ArrayList<>();
        for (String line : lore) l.add(plain(line));
        if (!l.isEmpty()) m.lore(l);
        item.setItemMeta(m);
        return item;
    }

    private static Component plain(String legacy) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacy)
                .decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Book b)) return;
        e.setCancelled(true);
        int raw = e.getRawSlot();
        MagicData d = magic.players().data(b.player);
        if (d == null || raw < 0 || raw >= 54) return;
        List<String> known = d.spells();
        if (raw < KNOWN) {
            if (raw < known.size()) {
                b.picked = known.get(raw).equals(b.picked) ? null : known.get(raw);
                b.player.playSound(b.player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                draw(b);
            }
            return;
        }
        int index = slotIndex(raw);
        if (index < 0) return;
        if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
            d.setSlot(index, "");
        } else if (b.picked != null) {
            String[] loadout = d.loadout();
            for (int i = 0; i < loadout.length; i++) {
                if (b.picked.equals(loadout[i])) d.setSlot(i, ""); // one slot per spell
            }
            d.setSlot(index, b.picked);
            b.picked = null;
        } else {
            return;
        }
        b.player.playSound(b.player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.3f);
        magic.players().flush(b.player.getUniqueId());
        draw(b);
    }

    /** The loadout index (0..15) of a menu slot in the two slot rows, or -1. */
    static int slotIndex(int raw) {
        if (raw >= PAGE1 && raw < PAGE1 + MagicRules.SLOTS) return raw - PAGE1;
        if (raw >= PAGE2 && raw < PAGE2 + MagicRules.SLOTS) return MagicRules.SLOTS + raw - PAGE2;
        return -1;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Book) e.setCancelled(true);
    }
}
