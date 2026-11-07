package me.hektortm.woSSystems.systems.mail;

import me.hektortm.woSSystems.systems.mail.model.Mail;
import me.hektortm.woSSystems.systems.mail.model.MailIcon;
import net.kyori.adventure.text.Component;
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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The mailbox chest: one item per mail, newest first, over pages, plus the buttons of the settings (previous, next,
 * claim all, delete read, close). Every click in it is cancelled; a mail opens its letter.
 */
public final class MailboxGui implements Listener {

    /** Whose mailbox is open, which page, which mail and button sits in which slot. */
    static final class Holder implements InventoryHolder {
        final UUID owner;
        final String ownerName;
        final boolean readOnly;
        final int page;
        final List<Mail> mails;
        final Map<Integer, Integer> mailAt;
        final Map<Integer, String> buttonAt;
        Inventory inventory;

        Holder(UUID owner, String ownerName, boolean readOnly, int page, List<Mail> mails, Map<Integer, Integer> mailAt,
               Map<Integer, String> buttonAt) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.readOnly = readOnly;
            this.page = page;
            this.mails = mails;
            this.mailAt = mailAt;
            this.buttonAt = buttonAt;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final MailService service;
    private final MailItems items;
    private final Function<String, String> lang;
    private final Function<String, String> placeholders;

    /**
     * @param lang         a key of lang/mail.yml → its text
     * @param placeholders fills in placeholders and glyph tokens of the title (no player)
     */
    MailboxGui(MailService service, MailItems items, Function<String, String> lang, Function<String, String> placeholders) {
        this.service = service;
        this.items = items;
        this.lang = lang;
        this.placeholders = placeholders;
    }

    /** Slot → index into the mail list (newest first) for one page. */
    static Map<Integer, Integer> slotsFor(MailSettings.Box box, int page, int count) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        List<Integer> slots = box.mailSlots();
        int start = page * slots.size();
        for (int i = 0; i < slots.size() && start + i < count; i++) out.put(slots.get(i), start + i);
        return out;
    }

    /** How many pages the mail takes (at least one, so an empty mailbox still opens). */
    static int pages(MailSettings.Box box, int count) {
        int per = Math.max(1, box.mailSlots().size());
        return Math.max(1, (count + per - 1) / per);
    }

    void open(Player viewer, UUID owner, String ownerName, boolean readOnly, int page, List<Mail> mails, MailSettings s) {
        MailSettings.Box box = s.box();
        int last = pages(box, mails.size()) - 1;
        int p = Math.max(0, Math.min(page, last));
        Map<Integer, Integer> mailAt = slotsFor(box, p, mails.size());
        Map<Integer, String> buttonAt = new HashMap<>();
        for (Map.Entry<String, MailSettings.Button> b : box.buttons().entrySet()) {
            if (showsButton(b.getKey(), p, last, readOnly)) buttonAt.put(b.getValue().slot(), b.getKey());
        }
        Holder holder = new Holder(owner, ownerName, readOnly, p, mails, mailAt, buttonAt);
        String title = box.background().isBlank() ? box.title() : "{glyph:" + box.background() + "}" + box.title();
        Inventory inv = Bukkit.createInventory(holder, box.rows() * 9, MailItems.text(placeholders.apply(title)));
        holder.inventory = inv;
        ItemStack filler = items.icon(box.filler());
        if (filler != null) {
            filler.editMeta(meta -> meta.displayName(Component.empty()));
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        }
        mailAt.forEach((slot, index) -> inv.setItem(slot, mailItem(mails.get(index), s)));
        buttonAt.forEach((slot, name) -> inv.setItem(slot, button(box.buttons().get(name), name)));
        viewer.openInventory(inv);
    }

    private static boolean showsButton(String name, int page, int last, boolean readOnly) {
        return switch (name) {
            case "prev" -> page > 0;
            case "next" -> page < last;
            case "claim_all", "delete_read" -> !readOnly;
            default -> true;
        };
    }

    private static final Map<String, Material> BUTTON_ITEMS = Map.of(
            "prev", Material.ARROW, "next", Material.ARROW, "claim_all", Material.HOPPER,
            "delete_read", Material.LAVA_BUCKET, "close", Material.BARRIER);

    private ItemStack button(MailSettings.Button b, String name) {
        ItemStack item = items.icon(b.item());
        if (item == null) item = new ItemStack(BUTTON_ITEMS.getOrDefault(name, Material.PAPER));
        String label = b.label();
        item.editMeta(meta -> meta.displayName(MailItems.text(label)));
        return item;
    }

    private ItemStack mailItem(Mail m, MailSettings s) {
        String status = MailRules.status(m);
        boolean opened = m.claimed() || m.read();
        MailIcon icon = opened ? first(m.iconOpened(), s.letter().openedIcon(), m.icon()) : first(m.icon(), s.letter().unreadIcon(), null);
        ItemStack item = items.icon(icon);
        if (item == null) item = new ItemStack(opened ? Material.MAP : Material.PAPER);
        Map<String, String> colours = s.box().colours();
        List<Component> lore = new ArrayList<>();
        lore.add(MailItems.text(colours.get("sender") + lang.apply("box.from").replace("%sender%", MailRules.senderLine(m, lang.apply("box.server")))));
        lore.add(MailItems.text(colours.get("date") + lang.apply("box.received").replace("%date%", date(m, s))));
        if (!m.attachments().isEmpty()) {
            lore.add(MailItems.text("&7" + lang.apply("box.items").replace("%count%", String.valueOf(m.attachments().size()))));
        }
        lore.add(MailItems.text(colours.get(status) + lang.apply("box.status." + status)));
        lore.add(MailItems.text("&8" + lang.apply("box.hint.open")));
        String name = colours.get(status) + m.subject();
        item.editMeta(meta -> {
            meta.displayName(MailItems.text(name));
            meta.lore(lore);
        });
        return item;
    }

    @Nullable
    private static MailIcon first(@Nullable MailIcon a, @Nullable MailIcon b, @Nullable MailIcon c) {
        return a != null ? a : b != null ? b : c;
    }

    /** When it was received, in the settings' timezone and format. */
    static String date(Mail m, MailSettings s) {
        try {
            return DateTimeFormatter.ofPattern(s.general().dateFormat()).withZone(ZoneId.of(s.general().timezone())).format(m.createdAt());
        } catch (RuntimeException e) {
            return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Berlin")).format(m.createdAt());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player viewer) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        Integer index = holder.mailAt.get(slot);
        if (index != null) {
            service.openLetter(viewer, holder, holder.mails.get(index));
            return;
        }
        String button = holder.buttonAt.get(slot);
        if (button != null) service.button(viewer, holder, button);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
