package me.hektortm.woSSystems.systems.mail;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.hektortm.woSSystems.systems.cscreens.CscreenRules;
import me.hektortm.woSSystems.systems.mail.model.MailAttachment;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Player mail ({@code /mail send <player>}): a one-row chest the sender fills, then a dialog for subject and
 * message, then Send or Cancel. The items leave the sender only into the API's mail: on any refusal or failure,
 * on Cancel, on closing the chest empty-handed or on quitting, every item goes back (overflow rules when full).
 * Unstashable items are refused (the chest already keeps them out); undroppable ones may be sent.
 */
public final class SendBox implements Listener {

    /** Sends the package; completes with null when sent, else the API's error code. */
    @FunctionalInterface
    public interface Sender {
        CompletableFuture<String> send(UUID recipient, UUID sender, String subject, String body, List<MailAttachment> items);
    }

    /** Gives items back to the sender (what doesn't fit is mailed back / dropped). */
    @FunctionalInterface
    public interface GiveBack {
        void give(Player player, List<ItemStack> items);
    }

    /** A message from lang/mail.yml with replacement pairs. */
    @FunctionalInterface
    public interface Notice {
        void send(Player player, String key, String... replacements);
    }

    /** A filled package waiting for the dialog's answer. */
    private record Held(UUID recipient, String recipientName, List<ItemStack> items) {}

    /** The open chest. */
    static final class Holder implements InventoryHolder {
        final UUID recipient;
        final String recipientName;
        Inventory inventory;

        Holder(UUID recipient, String recipientName) {
            this.recipient = recipient;
            this.recipientName = recipientName;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();

    private final Sender sender;
    private final Function<ItemStack, MailAttachment> attach;
    private final Predicate<ItemStack> unstashable;
    private final GiveBack giveBack;
    private final Consumer<Runnable> mainThread;
    private final Notice notice;
    private final LongSupplier clock;
    private final Map<UUID, Held> held = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSend = new ConcurrentHashMap<>();
    private Function<String, String> lang = key -> key;

    public SendBox(Sender sender, Function<ItemStack, MailAttachment> attach, Predicate<ItemStack> unstashable, GiveBack giveBack,
                   Consumer<Runnable> mainThread, Notice notice, LongSupplier clock) {
        this.sender = sender;
        this.attach = attach;
        this.unstashable = unstashable;
        this.giveBack = giveBack;
        this.mainThread = mainThread;
        this.notice = notice;
        this.clock = clock;
    }

    /** Where the chest's and the dialog's texts come from (lang/mail.yml). */
    public SendBox texts(Function<String, String> lang) {
        this.lang = lang;
        return this;
    }

    // ─── the flow ───

    /** Opens the package chest (the recipient is known and not the sender). */
    public void open(Player p, UUID recipient, String recipientName) {
        cancel(p); // a package still waiting goes back first
        Holder holder = new Holder(recipient, recipientName);
        Inventory inv = Bukkit.createInventory(holder, 9, MailItems.text(lang.apply("send.box-title").replace("%player%", recipientName)));
        holder.inventory = inv;
        p.openInventory(inv);
    }

    /** Takes the package out of the chest and holds it for the dialog. */
    void hold(Player p, UUID recipient, String recipientName, List<ItemStack> items) {
        held.put(p.getUniqueId(), new Held(recipient, recipientName, List.copyOf(items)));
    }

    /** Send: checks, then the API; every item comes back on a refusal or failure. */
    void submit(Player p, String subject, String body) {
        Held h = held.remove(p.getUniqueId());
        if (h == null) return;
        long now = clock.getAsLong();
        List<Boolean> flags = new ArrayList<>();
        for (ItemStack s : h.items()) flags.add(unstashable.test(s));
        Optional<String> refused = MailRules.refusePlayerMail(false, true, flags, lastSend.getOrDefault(p.getUniqueId(), 0L), now);
        if (refused.isPresent()) {
            giveBack.give(p, h.items());
            String key = refused.get();
            notice.send(p, key, "%seconds%", String.valueOf(MailRules.cooldownLeftSeconds(lastSend.getOrDefault(p.getUniqueId(), 0L), now)),
                    "%item%", firstUnstashable(h.items()), "%player%", h.recipientName());
            return;
        }
        String s = clean(subject, MailRules.MAX_SUBJECT);
        if (s.isBlank()) s = lang.apply("send.default-subject").replace("%player%", p.getName());
        List<MailAttachment> items = new ArrayList<>();
        for (ItemStack stack : h.items()) items.add(attach.apply(stack));
        lastSend.put(p.getUniqueId(), now);
        String subjectLine = s;
        sender.send(h.recipient(), p.getUniqueId(), subjectLine, clean(body, MailRules.MAX_PLAYER_BODY), items)
                .exceptionally(e -> "unavailable")
                .thenAccept(error -> mainThread.accept(() -> answered(p, h, error)));
    }

    private void answered(Player p, Held h, String error) {
        if (error == null) {
            notice.send(p, "send.done", "%player%", h.recipientName());
            return;
        }
        lastSend.remove(p.getUniqueId()); // nothing was sent: no cooldown
        giveBack.give(p, h.items());
        switch (error) {
            case "mailbox_full" -> notice.send(p, "send.full", "%player%", h.recipientName());
            case "player_not_found" -> notice.send(p, "send.unknown", "%player%", h.recipientName());
            default -> notice.send(p, "send.failed");
        }
    }

    private String firstUnstashable(List<ItemStack> items) {
        for (ItemStack s : items) {
            if (!unstashable.test(s)) continue;
            MailAttachment.Summary sum = Stacks.summary(s, null);
            return sum.name().isEmpty() ? sum.material() : sum.name();
        }
        return "";
    }

    /** Cancel (also quitting, or the dialog going away): the package goes back. */
    void cancel(Player p) {
        Held h = held.remove(p.getUniqueId());
        if (h != null && !h.items().isEmpty()) {
            giveBack.give(p, h.items());
            if (p.isOnline()) notice.send(p, "send.returned");
        }
    }

    /**
     * The server stops: every package still waiting for its dialog goes back to its sender (the items live only in
     * memory until they are sent). {@code online} finds the sender; one who is gone already got theirs on quitting.
     */
    public void returnAll(Function<UUID, Player> online) {
        for (UUID sender : List.copyOf(held.keySet())) {
            Held h = held.remove(sender);
            Player p = online.apply(sender);
            if (h == null || p == null || h.items().isEmpty()) continue;
            try {
                giveBack.give(p, h.items());
            } catch (RuntimeException e) {
                // Mid-shutdown no mail can be written: what doesn't fit drops at their feet.
                for (ItemStack s : p.getInventory().addItem(h.items().toArray(new ItemStack[0])).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), s);
                }
            }
        }
    }

    /** The typed text as custom screen inputs clean it (no § codes, no clickable links, no line breaks), cut. */
    static String clean(String text, int max) {
        String s = CscreenRules.clean(text == null ? "" : text).trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ─── the chest and the dialog ───

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder) || !(e.getPlayer() instanceof Player p)) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : e.getInventory().getContents()) {
            if (s != null && !s.getType().isAir()) items.add(s);
        }
        e.getInventory().clear();
        if (items.isEmpty()) return;
        hold(p, holder.recipient, holder.recipientName, items);
        // The dialog opens a tick later: opening a screen inside the close event is not allowed.
        mainThread.accept(() -> Bukkit.getScheduler().runTask(me.hektortm.woSSystems.WoSSystems.getInstance(), () -> {
            if (p.isOnline()) p.showDialog(dialog(p, holder.recipientName));
        }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) p.closeInventory(); // onClose holds it
        cancel(p);
    }

    private Dialog dialog(Player p, String recipientName) {
        ActionButton send = ActionButton.builder(MailItems.text(lang.apply("send.button")))
                .action(DialogAction.customClick((response, audience) -> mainThread.accept(() ->
                        submit(p, text(response.getText("subject")), text(response.getText("message")))), ONCE))
                .build();
        ActionButton cancel = ActionButton.builder(MailItems.text(lang.apply("send.cancel")))
                .action(DialogAction.customClick((response, audience) -> mainThread.accept(() -> cancel(p)), ONCE))
                .build();
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(MailItems.text(lang.apply("send.dialog-title").replace("%player%", recipientName)))
                        // No closing with Escape: the package must be sent or taken back (Cancel).
                        .canCloseWithEscape(false)
                        .inputs(List.of(
                                DialogInput.text("subject", MailItems.text(lang.apply("send.subject"))).maxLength(MailRules.MAX_SUBJECT).width(300).build(),
                                DialogInput.text("message", MailItems.text(lang.apply("send.message"))).maxLength(MailRules.MAX_PLAYER_BODY).width(300)
                                        .multiline(io.papermc.paper.registry.data.dialog.input.TextDialogInput.MultilineOptions.create(4, null)).build()))
                        .build())
                .type(DialogType.confirmation(send, cancel)));
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }
}
