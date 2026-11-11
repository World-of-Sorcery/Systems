package me.hektortm.woSSystems.systems.magic.ring;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicRules;
import me.hektortm.woSSystems.systems.magic.MagicSystem;
import me.hektortm.woSSystems.systems.magic.MagicText;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The spell picker (spec 2026-11-11 §4): opened by clicking a ring slot in the spellbook; the
 * learned spells, 45 a page. Picking one puts it on that slot (and takes it off any other);
 * the bottom row has Back, Clear this slot and the page arrows. Nothing can be taken out.
 */
public final class SpellPicker implements Listener {

    /** A dialog button works once (a second click on a stale dialog does nothing). */
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).build();

    private final MagicSystem magic;

    public SpellPicker(MagicSystem magic) {
        this.magic = magic;
    }

    private static final class Picker implements InventoryHolder {
        private final Player player;
        private final int page;
        private final int pos;
        private final int pickerPage;
        private final int pages;
        private final List<String> shown;
        private final String query; // "" = no search
        private Inventory inv;

        private Picker(Player player, int page, int pos, int pickerPage, int pages, List<String> shown, String query) {
            this.player = player;
            this.page = page;
            this.pos = pos;
            this.pickerPage = pickerPage;
            this.pages = pages;
            this.shown = shown;
            this.query = query;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    /** Opens the picker for ring position {@code pos} (0–7) of ring page {@code page}, on picker page {@code pickerPage}. */
    public void open(Player p, int page, int pos, int pickerPage) {
        open(p, page, pos, pickerPage, "");
    }

    /** As above, showing only the spells whose name holds {@code query} ("" = all). */
    public void open(Player p, int page, int pos, int pickerPage, String query) {
        MagicData d = magic.players().data(p);
        if (d == null || pos < 0 || pos >= MagicRules.SLOTS) return;
        List<String> learned = new ArrayList<>();
        for (String id : d.spells()) {
            if (magic.stores().spell(id) != null) learned.add(id); // a deleted spell can't be cast
        }
        String q = query == null ? "" : query.trim();
        List<String> known = SpellbookLayout.search(learned, id -> {
            SpellDef s = magic.stores().spell(id);
            return s == null ? null : SpellTooltip.plainName(s.name());
        }, q);
        int pages = SpellbookLayout.pickerPages(known.size());
        int pp = Math.max(0, Math.min(pickerPage, pages - 1));
        List<String> shown = List.copyOf(SpellbookLayout.pickerSlice(known, pp));
        int wandSlot = SpellbookMenu.wandSlot(magic, p);
        Component title = wandSlot < 0
                ? MagicText.component("picker.title_slot", "%page%", String.valueOf(page + 1), "%slot%", String.valueOf(pos + 1))
                : MagicText.component("picker.title", "%key%", String.valueOf(MagicRules.keyOf(pos, wandSlot)));
        Picker h = new Picker(p, page, pos, pp, pages, shown, q);
        h.inv = Bukkit.createInventory(h, 54, title);

        String[] loadout = d.loadout();
        int index = SpellbookLayout.loadoutIndex(page, pos);
        for (int i = 0; i < shown.size(); i++) {
            String id = shown.get(i);
            ItemStack item = SpellbookMenu.spellIcon(magic, p, id);
            int at = Arrays.asList(loadout).indexOf(id);
            if (at >= 0 && at != index) {
                int atPos = at % MagicRules.SLOTS;
                SpellbookMenu.addLore(item, wandSlot < 0
                        ? MagicText.text("picker.bound_slot", "%page%", String.valueOf(at / MagicRules.SLOTS + 1), "%slot%", String.valueOf(atPos + 1))
                        : MagicText.text("picker.bound", "%key%", String.valueOf(MagicRules.keyOf(atPos, wandSlot))));
            }
            h.inv.setItem(i, item);
        }
        h.inv.setItem(SpellbookLayout.BACK, SpellbookMenu.named(Material.OAK_DOOR, MagicText.text("picker.back")));
        h.inv.setItem(SpellbookLayout.CLEAR, SpellbookMenu.named(Material.BARRIER, MagicText.text("picker.clear")));
        if (pp > 0) h.inv.setItem(SpellbookLayout.PREV, SpellbookMenu.named(Material.ARROW, MagicText.text("picker.prev")));
        if (pp < pages - 1) h.inv.setItem(SpellbookLayout.NEXT, SpellbookMenu.named(Material.ARROW, MagicText.text("picker.next")));
        h.inv.setItem(SpellbookLayout.SEARCH, q.isEmpty()
                ? SpellbookMenu.named(Material.SPYGLASS, MagicText.text("picker.search"), MagicText.text("picker.search_hint"))
                : SpellbookMenu.named(Material.SPYGLASS, MagicText.text("picker.search"),
                        MagicText.text(known.isEmpty() ? "picker.search_none" : "picker.search_current", "%query%", q),
                        MagicText.text("picker.search_hint"), MagicText.text("picker.search_clear")));
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Picker h)) return;
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= h.inv.getSize()) return;
        Player p = h.player;
        MagicData d = magic.players().data(p);
        if (d == null) return;
        int index = SpellbookLayout.loadoutIndex(h.page, h.pos);
        if (raw < h.shown.size()) {
            String id = h.shown.get(raw);
            String[] before = d.loadout();
            if (!id.equals(before[index])) {
                String[] after = before.clone();
                SpellbookLayout.assign(after, index, id);
                for (int i = 0; i < after.length; i++) {
                    if (!after[i].equals(before[i])) d.setSlot(i, after[i]);
                }
                magic.players().flush(p.getUniqueId());
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.3f);
            }
            back(h);
        } else if (raw == SpellbookLayout.BACK) {
            back(h);
        } else if (raw == SpellbookLayout.CLEAR) {
            d.setSlot(index, "");
            magic.players().flush(p.getUniqueId());
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.0f);
            back(h);
        } else if (raw == SpellbookLayout.PREV && h.pickerPage > 0) {
            SpellbookMenu.later(p, () -> open(p, h.page, h.pos, h.pickerPage - 1, h.query));
        } else if (raw == SpellbookLayout.NEXT && h.pickerPage < h.pages - 1) {
            SpellbookMenu.later(p, () -> open(p, h.page, h.pos, h.pickerPage + 1, h.query));
        } else if (raw == SpellbookLayout.SEARCH) {
            if (e.getClick().isRightClick() && !h.query.isEmpty()) {
                SpellbookMenu.later(p, () -> open(p, h.page, h.pos, 0, ""));
            } else {
                SpellbookMenu.later(p, () -> {
                    p.closeInventory();
                    p.showDialog(searchDialog(h));
                });
            }
        }
    }

    /** "Search spells": one name field; Search shows the matches, Cancel goes back to the picker as it was. */
    private Dialog searchDialog(Picker h) {
        Player p = h.player;
        ActionButton search = ActionButton.builder(MagicText.component("picker.search_button"))
                .action(DialogAction.customClick((response, audience) -> {
                    String q = response.getText("query");
                    SpellbookMenu.later(p, () -> open(p, h.page, h.pos, 0, q == null ? "" : q));
                }, ONCE))
                .build();
        ActionButton cancel = ActionButton.builder(MagicText.component("picker.search_cancel"))
                .action(DialogAction.customClick((response, audience) ->
                        SpellbookMenu.later(p, () -> open(p, h.page, h.pos, h.pickerPage, h.query)), ONCE))
                .build();
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(MagicText.component("picker.search_title"))
                        .canCloseWithEscape(true)
                        .inputs(List.of(DialogInput.text("query", MagicText.component("picker.search_label"))
                                .initial(h.query).maxLength(SpellbookLayout.SEARCH_MAX).width(240).build()))
                        .build())
                .type(DialogType.confirmation(search, cancel)));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Picker) e.setCancelled(true);
    }

    private void back(Picker h) {
        SpellbookMenu.later(h.player, () -> magic.spellbook().open(h.player, h.page));
    }
}
