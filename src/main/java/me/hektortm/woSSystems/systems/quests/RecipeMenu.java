package me.hektortm.woSSystems.systems.quests;

import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.Comparator;
import java.util.List;

/**
 * The recipe menu (spec §4.8): clicking something that runs an interaction
 * that sequences name as "starts at", while no sequence runs, opens a chest of
 * those sequences. One that can start is clicked to start at that very block
 * (it becomes the anchor); one that can't shows what it needs.
 */
public final class RecipeMenu implements Listener {

    private final QuestEngine e;

    RecipeMenu(QuestEngine engine) {
        this.e = engine;
    }

    /** What the menu remembers: where it was opened and which sequence each slot holds. */
    private static final class Holder implements InventoryHolder {
        private final String anchor;
        private final List<String> ids;
        private Inventory inventory;

        Holder(@Nullable String anchor, List<String> ids) {
            this.anchor = anchor;
            this.ids = ids;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** The sequences that start at an interaction and are listed in its menu. */
    public List<QuestDef> sequencesAt(String interactionId) {
        List<QuestDef> out = new ArrayList<>();
        for (QuestDef d : e.published()) {
            if (d.sequence() && d.inMenu() && interactionId.equals(d.startsAt())) out.add(d);
        }
        out.sort(Comparator.comparing(d -> e.title(d.id())));
        return out;
    }

    /** Opens the menu; false when no sequence starts at this interaction (it then runs as usual). */
    boolean open(Player p, String interactionId, @Nullable String anchor) {
        List<QuestDef> list = sequencesAt(interactionId);
        if (list.isEmpty()) return false;
        Holder holder = new Holder(anchor, list.stream().map(QuestDef::id).toList());
        int size = Math.min(54, Math.max(9, ((list.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(holder, size, Component.text(QuestRules.pretty(interactionId), NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        for (int i = 0; i < list.size() && i < size; i++) inv.setItem(i, icon(p, list.get(i)));
        p.openInventory(inv);
        return true;
    }

    private ItemStack icon(Player p, QuestDef d) {
        boolean ok = e.startConditionsPass(p, d);
        ItemStack item = null;
        if (ok && !d.menuIcon().isEmpty()) item = e.hub().getCitemDAO().getCitem(d.menuIcon());
        if (item == null) item = new ItemStack(ok ? Material.PAPER : Material.GRAY_DYE);
        item = item.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(e.title(d.id()), ok ? NamedTextColor.GOLD : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (ok) {
            lore.add(Component.text("Click to start", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Needs:", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            for (Condition c : d.startConditions()) {
                if (e.plugin().getConditionHandler().evaluate(p, c, null)) continue;
                lore.add(Component.text(" " + describe(c), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** "has_citem raw_fish 2" → "raw fish ×2". */
    static String describe(Condition c) {
        String what = QuestRules.pretty(c.getValue());
        String n = c.getParameter() == null || c.getParameter().isBlank() ? "" : " ×" + c.getParameter();
        String key = c.getName() == null ? "" : c.getName();
        if (key.startsWith("has_citem") || key.startsWith("has_currency")) return what + n;
        return QuestRules.pretty(key) + " " + what + n;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getSlot();
        if (slot < 0 || slot >= holder.ids.size()) return;
        String id = holder.ids.get(slot);
        p.closeInventory();
        QuestEngine.Start result = e.start(p, id, holder.anchor);
        if (result != QuestEngine.Start.STARTED) {
            Utils.error(p, "quests", "error.start." + result.name().toLowerCase(), "%quest%", e.title(id));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
