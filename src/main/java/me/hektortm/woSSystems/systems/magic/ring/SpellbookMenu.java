package me.hektortm.woSSystems.systems.magic.ring;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.guis.GuiBackground;
import me.hektortm.woSSystems.systems.guis.GuiRules;
import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicRules;
import me.hektortm.woSSystems.systems.magic.MagicSystem;
import me.hektortm.woSSystems.systems.magic.MagicText;
import me.hektortm.woSSystems.systems.magic.model.MagicSettings;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.systems.resourcepack.Glyph;
import me.hektortm.woSSystems.utils.ColorText;
import me.hektortm.woSSystems.utils.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The spellbook (spec 2026-11-11 §3): one ring page's eight slots around the player's wand
 * (slots in {@link SpellbookLayout}), the wand skins above the wand and an arrow to the other
 * page. Clicking a ring slot opens the {@link SpellPicker} for it. Opened by {@code /spells}
 * or sneak + right-click with the wand. Nothing can be taken out.
 */
public final class SpellbookMenu implements Listener {

    private final MagicSystem magic;

    public SpellbookMenu(MagicSystem magic) {
        this.magic = magic;
    }

    private static final class Book implements InventoryHolder {
        private final Player player;
        private final int page;
        private Inventory inv;

        private Book(Player player, int page) {
            this.player = player;
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    public void open(Player p) {
        open(p, 0);
    }

    /** Opens the spellbook on ring page {@code page} (0 or 1; other values wrap). */
    public void open(Player p, int page) {
        MagicData d = magic.players().data(p);
        if (d == null) return;
        int pg = Math.floorMod(page, MagicRules.PAGES);
        Book b = new Book(p, pg);
        b.inv = Bukkit.createInventory(b, 54, withBackground(magic, magic.stores().settings().bookBackground(),
                MagicText.component("book.title", "%page%", String.valueOf(pg + 1))));
        draw(b, d);
        p.openInventory(b.inv);
    }

    private void draw(Book b, MagicData d) {
        Player p = b.player;
        int wandSlot = wandSlot(magic, p);
        String[] loadout = d.loadout();
        for (int pos = 0; pos < MagicRules.SLOTS; pos++) {
            String id = loadout[SpellbookLayout.loadoutIndex(b.page, pos)];
            String key = wandSlot < 0 ? null : String.valueOf(MagicRules.keyOf(pos, wandSlot));
            ItemStack item;
            if (!id.isEmpty() && d.knows(id) && magic.stores().spell(id) != null) {
                item = spellIcon(magic, p, id);
                addLore(item, key == null ? MagicText.text("book.click") : MagicText.text("book.key", "%key%", key));
            } else {
                item = named(Material.LIGHT_GRAY_DYE, MagicText.text("book.empty"),
                        key == null ? MagicText.text("book.click_empty") : MagicText.text("book.key_empty", "%key%", key));
            }
            b.inv.setItem(SpellbookLayout.RING_SLOTS[pos], item);
        }
        if (d.wand() != null) {
            ItemStack wand = magic.wands().build(p);
            if (wand != null) b.inv.setItem(SpellbookLayout.WAND, displayOnly(wand));
            b.inv.setItem(SpellbookLayout.SKIN, named(Material.PAINTING, MagicText.text("book.skins")));
        }
        b.inv.setItem(SpellbookLayout.PAGE_ARROW, named(Material.ARROW, MagicText.text(b.page == 0 ? "book.page_next" : "book.page_prev")));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Book b)) return;
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= b.inv.getSize()) return;
        Player p = b.player;
        int pos = SpellbookLayout.posOfSlot(raw);
        if (pos >= 0) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            later(p, () -> magic.picker().open(p, b.page, pos, 0));
        } else if (raw == SpellbookLayout.SKIN && b.inv.getItem(SpellbookLayout.SKIN) != null) {
            later(p, () -> magic.wandMenu().open(p, true));
        } else if (raw == SpellbookLayout.PAGE_ARROW) {
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.3f);
            later(p, () -> open(p, b.page + 1));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Book) e.setCancelled(true);
    }

    // ── shared with the picker ──

    /**
     * A spell as the spellbook shows it (spec §5, §6): its spellbook icon, its name and
     * description in colour, level and XP bar (spells with levels), mana and cooldown at the
     * player's level; mastered adds the glint and the mastered tooltip style. Callers add
     * the context line.
     */
    static ItemStack spellIcon(MagicSystem magic, Player p, String id) {
        SpellDef s = magic.stores().spell(id);
        GuiRules.Key model = s == null || s.bookIcon().isBlank() ? null : GuiRules.resourceKey(s.bookIcon(), "wos");
        ItemStack item = new ItemStack(model == null ? Material.ENCHANTED_BOOK : Material.PAPER);
        ItemMeta m = item.getItemMeta();
        if (model != null) m.setItemModel(new NamespacedKey(model.namespace(), model.path()));
        if (s == null) {
            m.displayName(color(id));
            item.setItemMeta(m);
            return item;
        }
        MagicData d = magic.players().data(p);
        MagicData.Progress pr = d == null ? new MagicData.Progress(1, 0) : d.progress(id);
        int max = s.maxLevel();
        boolean mastered = SpellTooltip.mastered(pr.level(), max);
        int level = Math.max(1, Math.min(pr.level(), max));
        MagicSettings set = magic.stores().settings();
        m.displayName(color(SpellTooltip.displayName(s.name(), mastered)));

        List<Component> lore = new ArrayList<>();
        for (String line : SpellTooltip.descriptionLines(s.description())) lore.add(color(line));
        if (max > 1) {
            if (!lore.isEmpty()) lore.add(Component.empty());
            String full = glyph(magic, set.xpBarFull());
            String empty = glyph(magic, set.xpBarEmpty());
            if (mastered) {
                lore.add(color(MagicText.text("book.mastered")));
                lore.add(color(SpellTooltip.bar(SpellTooltip.SEGMENTS, true, full, empty)));
            } else {
                double next = s.xpToNext(level);
                String bar = SpellTooltip.bar(SpellTooltip.filledSegments(pr.xp(), next), false, full, empty);
                lore.add(color(MagicText.text("book.level", "%level%", String.valueOf(level), "%max%", String.valueOf(max))));
                lore.add(color(MagicText.text("book.xp", "%bar%", bar, "%xp%", MagicRules.manaText(pr.xp()), "%next%", MagicRules.manaText(next))));
            }
        }
        if (!lore.isEmpty()) lore.add(Component.empty());
        SpellDef tier = s.at(level);
        lore.add(color(MagicText.text("book.stats", "%mana%", MagicRules.manaText(tier.manaCost()), "%cooldown%", seconds(tier.cooldown()))));
        m.lore(lore);
        if (mastered) {
            m.setEnchantmentGlintOverride(true);
            GuiRules.Key style = GuiRules.resourceKey(set.masteredTooltip(), "wos");
            if (style != null) m.setTooltipStyle(new NamespacedKey(style.namespace(), style.path()));
        }
        item.setItemMeta(m);
        return item;
    }

    /** The hotbar slot (0–8) of the player's own wand: the held one first; -1 if it isn't in the hotbar. */
    static int wandSlot(MagicSystem magic, Player p) {
        int held = p.getInventory().getHeldItemSlot();
        if (magic.wands().isOwnWand(p.getInventory().getItem(held), p)) return held;
        for (int i = 0; i < 9; i++) {
            if (magic.wands().isOwnWand(p.getInventory().getItem(i), p)) return i;
        }
        return -1;
    }

    static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta m = item.getItemMeta();
        m.displayName(color(name));
        List<Component> l = new ArrayList<>();
        for (String line : lore) l.add(color(line));
        if (!l.isEmpty()) m.lore(l);
        item.setItemMeta(m);
        return item;
    }

    static void addLore(ItemStack item, String line) {
        ItemMeta m = item.getItemMeta();
        List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        lore.add(color(line));
        m.lore(lore);
        item.setItemMeta(m);
    }

    /** {@code &} / {@code §} / hex colour text as an item line (not italic). */
    static Component color(String text) {
        return ColorText.component(text).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * {@code title} with the background glyph {@code id} (a magic setting, "" = none) behind a
     * 6-row screen; just the title for an unknown glyph or one that isn't a 6-row background.
     */
    static Component withBackground(MagicSystem magic, String id, Component title) {
        return id.isEmpty() ? title : GuiBackground.titleWith(magic.glyph(id), 6, title);
    }

    /** Runs a screen change on the next tick (opening an inventory inside a click event is unsafe). */
    static void later(Player p, Runnable r) {
        Bukkit.getScheduler().runTask(WoSSystems.getPlugin(WoSSystems.class), () -> {
            if (p.isOnline()) r.run();
        });
    }

    /** A wand for looking at only: without the owner stamp it is no wand anywhere it might end up. */
    private static ItemStack displayOnly(ItemStack wand) {
        ItemStack copy = wand.clone();
        ItemMeta m = copy.getItemMeta();
        m.getPersistentDataContainer().remove(Keys.WAND_OWNER.get());
        m.getPersistentDataContainer().remove(Keys.WAND.get());
        copy.setItemMeta(m);
        return copy;
    }

    @Nullable
    private static String glyph(MagicSystem magic, String id) {
        if (id.isEmpty()) return null;
        Glyph g = magic.glyph(id);
        return g == null ? null : g.character();
    }

    private static String seconds(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
