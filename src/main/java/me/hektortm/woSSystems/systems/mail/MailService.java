package me.hektortm.woSSystems.systems.mail;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.citems.CitemManager;
import me.hektortm.woSSystems.systems.mail.MailRules.StackNeed;
import me.hektortm.woSSystems.systems.mail.model.Mail;
import me.hektortm.woSSystems.systems.quests.QuestHooks;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.WoSCore;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The mailbox in game: opening it, letters, claiming (claim in the API first, then give: spec "Claiming"), claim all,
 * deleting, and the notices on join and when mail arrives. Every API call runs off the server thread; what touches
 * the game runs on it.
 */
public final class MailService implements Listener {

    /** How many of the newest mails a mailbox shows (pages over them). */
    private static final int SHOWN = 200;

    private final WoSSystems plugin;
    private final MailClient client;
    private final MailItems items;
    private final MailboxGui gui;
    private final LetterDialog letters;
    private final ClaimGate gate = new ClaimGate();
    private final SendBox sendBox;
    private volatile MailSettings settings = MailSettings.parse(null);

    public MailService(WoSSystems plugin, DAOHub hub, MailClient client) {
        this.plugin = plugin;
        this.client = client;
        this.items = new MailItems(hub, plugin.getLogger());
        this.gui = new MailboxGui(this, items, this::lang, text -> plugin.getPlaceholderResolver().resolvePlaceholders(text, null));
        this.letters = new LetterDialog(items, this::lang, (text, p) -> plugin.getPlaceholderResolver().resolvePlaceholders(text, p));
        this.sendBox = new SendBox(client::sendPlayerMail,
                stack -> Stacks.attachment(stack, CitemManager.citemIdOf(stack)),
                me.hektortm.woSSystems.systems.citems.StashListener::isUnstashable,
                (p, back) -> plugin.getItemDelivery().give(p, back, "Package returned"),
                this::main,
                (p, key, replacements) -> Utils.success(p, "mail", key, replacements),
                System::currentTimeMillis).texts(this::lang);
    }

    /** The player-mail chest and dialog (a listener of its own). */
    public SendBox sendBox() {
        return sendBox;
    }

    public MailboxGui gui() {
        return gui;
    }

    String lang(String key) {
        String s = plugin.getLangManager().getMessage("mail", key);
        return s == null ? key : s;
    }

    private void main(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Loads the look from the API (at start and on the "mail" webhook). */
    public void reloadSettings() {
        client.settings().thenAccept(json -> settings = MailSettings.parse(json))
                .exceptionally(e -> {
                    plugin.getLogger().warning("[Mail] settings not loaded: " + e.getMessage());
                    return null;
                });
    }

    // ─── opening ───

    /** Opens a mailbox (the viewer's own, or read-only someone else's). */
    public void open(Player viewer, UUID owner, String ownerName, boolean readOnly, int page) {
        client.list(owner, null, SHOWN).whenComplete((box, err) -> main(() -> {
            if (!viewer.isOnline()) return;
            if (err != null) {
                Utils.error(viewer, "mail", "error.unavailable");
                return;
            }
            if (box.items().isEmpty() && !readOnly) Utils.success(viewer, "mail", "box.empty");
            gui.open(viewer, owner, ownerName, readOnly, page, box.items(), settings);
        }));
    }

    void reopen(Player viewer, MailboxGui.Holder h) {
        open(viewer, h.owner, h.ownerName, h.readOnly, h.page);
    }

    void openLetter(Player viewer, MailboxGui.Holder h, Mail m) {
        viewer.closeInventory();
        if (!h.readOnly && !m.read()) client.read(h.owner, m.id());
        viewer.showDialog(letters.build(viewer, m, settings, h.readOnly, new LetterDialog.Actions() {
            @Override
            public void claim() {
                claimOne(viewer, m, () -> reopen(viewer, h));
            }

            @Override
            public void delete() {
                askDelete(viewer, h, m);
            }

            @Override
            public void back() {
                reopen(viewer, h);
            }
        }));
    }

    void button(Player viewer, MailboxGui.Holder h, String name) {
        switch (name) {
            case "prev" -> open(viewer, h.owner, h.ownerName, h.readOnly, h.page - 1);
            case "next" -> open(viewer, h.owner, h.ownerName, h.readOnly, h.page + 1);
            case "claim_all" -> claimAll(viewer, h);
            case "delete_read" -> deleteRead(viewer, h);
            default -> viewer.closeInventory();
        }
    }

    // ─── claiming ───

    /** Free storage slots and what the claim's items need (similar items merged). */
    static List<StackNeed> needs(List<ItemStack> give, ItemStack[] storage) {
        List<ItemStack> kinds = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        for (ItemStack g : give) {
            int at = -1;
            for (int i = 0; i < kinds.size(); i++) if (kinds.get(i).isSimilar(g)) at = i;
            if (at < 0) {
                kinds.add(g);
                amounts.add(g.getAmount());
            } else {
                amounts.set(at, amounts.get(at) + g.getAmount());
            }
        }
        List<StackNeed> out = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            ItemStack k = kinds.get(i);
            int max = k.getMaxStackSize();
            int room = 0;
            for (ItemStack s : storage) {
                if (s != null && !s.getType().isAir() && s.isSimilar(k) && s.getAmount() < max) room += max - s.getAmount();
            }
            out.add(new StackNeed(amounts.get(i), max, room));
        }
        return out;
    }

    static int emptySlots(ItemStack[] storage) {
        int n = 0;
        for (ItemStack s : storage) if (s == null || s.getType().isAir()) n++;
        return n;
    }

    /** Refuses on the hub, without room, or while another claim runs; null when the claim may go to the API. */
    private String refusal(Player p, Mail m) {
        if (!MailRules.claimAllowedHere(WoSCore.identity().tag())) return "claim.hub";
        List<ItemStack> give = items.attachments(m, new ArrayList<>());
        ItemStack[] storage = p.getInventory().getStorageContents();
        if (!MailRules.fits(needs(give, storage), emptySlots(storage))) return "claim.no-room";
        return null;
    }

    private void claimOne(Player p, Mail m, Runnable after) {
        String refused = refusal(p, m);
        if (refused != null) {
            int need = MailRules.slotsNeeded(needs(items.attachments(m, new ArrayList<>()), p.getInventory().getStorageContents()));
            Utils.success(p, "mail", refused, "%count%", String.valueOf(need));
            after.run();
            return;
        }
        claim(p, m).thenAccept(ok -> main(after));
    }

    /** Claims in the API, then gives; completes true when the mail was claimed now. */
    private CompletableFuture<Boolean> claim(Player p, Mail m) {
        UUID id = p.getUniqueId();
        if (!gate.tryBegin(id)) {
            Utils.success(p, "mail", "claim.busy");
            return CompletableFuture.completedFuture(false);
        }
        return client.claim(id, m.id()).thenCompose(result -> {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            main(() -> {
                gate.end(id);
                switch (MailRules.claimAnswer(p.isOnline(), result)) {
                    case GIVE -> done.complete(give(p, result.mail()));
                    case MAIL_BACK -> {
                        mailBack(p, result.mail());
                        done.complete(false);
                    }
                    case ALREADY -> {
                        Utils.success(p, "mail", "claim.already");
                        done.complete(false);
                    }
                    default -> {
                        Utils.success(p, "mail", "claim.failed");
                        done.complete(false);
                    }
                }
            });
            return done;
        });
    }

    /**
     * The API claimed it, but the player left before the answer came: their items go back to their mailbox (one
     * overflow mail); the claim commands are not run, and the log says so.
     */
    private void mailBack(Player p, Mail m) {
        List<ItemStack> back = items.attachments(m, new ArrayList<>());
        if (!back.isEmpty()) plugin.getItemDelivery().give(p, back, "Mail: " + m.subject());
        if (!m.claimCommands().isEmpty()) {
            plugin.getLogger().warning("[Mail] " + p.getName() + " left while claiming mail " + m.id() + ": items mailed back, claim commands not run");
        }
    }

    /** Gives a claimed mail's items and runs its claim effects (server thread). */
    private boolean give(Player p, Mail m) {
        List<String> missing = new ArrayList<>();
        List<ItemStack> give = items.attachments(m, missing);
        // What fits goes in and counts as obtained; the rest (rare: room was checked) is mailed back.
        plugin.getItemDelivery().give(p, give, "Mail: " + m.subject());
        for (String what : missing) {
            Utils.success(p, "mail", "stack".equals(what) ? "claim.unreadable" : "claim.missing-citem", "%id%", what);
        }
        if (!m.claimCommands().isEmpty()) {
            plugin.getActionHandler().executeActions(p, m.claimCommands(), ActionHandler.SourceType.MAIL,
                    m.presetId() == null ? "mail:" + m.id() : m.presetId(), null);
        }
        QuestHooks.report(p, "mail", "claimed", Map.of("preset", m.presetId() == null ? "" : m.presetId()));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1, 1);
        Utils.success(p, "mail", "claim.done", "%subject%", m.subject());
        return true;
    }

    /** Claims every unclaimed mail in order while it fits; stops at the first that doesn't. */
    private void claimAll(Player p, MailboxGui.Holder h) {
        Deque<Mail> queue = new ArrayDeque<>();
        for (Mail m : h.mails) if (m.claimable() && !m.claimed()) queue.add(m);
        p.closeInventory();
        claimNext(p, h, queue, 0);
    }

    private void claimNext(Player p, MailboxGui.Holder h, Deque<Mail> queue, int claimed) {
        Mail m = queue.poll();
        String refused = m == null ? null : refusal(p, m);
        if (m == null || refused != null) {
            if (refused != null) {
                int need = MailRules.slotsNeeded(needs(items.attachments(m, new ArrayList<>()), p.getInventory().getStorageContents()));
                Utils.success(p, "mail", refused, "%count%", String.valueOf(need));
            }
            Utils.success(p, "mail", "claim.all", "%count%", String.valueOf(claimed));
            reopen(p, h);
            return;
        }
        claim(p, m).thenAccept(ok -> main(() -> {
            if (p.isOnline()) claimNext(p, h, queue, ok ? claimed + 1 : claimed);
        }));
    }

    // ─── deleting ───

    private void askDelete(Player p, MailboxGui.Holder h, Mail m) {
        if (m.claimable() && !m.claimed()) {
            p.showDialog(letters.confirmDelete(() -> delete(p, h, m), () -> reopen(p, h)));
            return;
        }
        delete(p, h, m);
    }

    private void delete(Player p, MailboxGui.Holder h, Mail m) {
        client.delete(h.owner, m.id()).whenComplete((v, err) -> main(() -> {
            Utils.success(p, "mail", err == null ? "delete.done" : "error.unavailable");
            reopen(p, h);
        }));
    }

    private void deleteRead(Player p, MailboxGui.Holder h) {
        List<CompletableFuture<Void>> calls = new ArrayList<>();
        for (Mail m : h.mails) {
            String status = MailRules.status(m);
            if (status.equals("claimed") || status.equals("read")) calls.add(client.delete(h.owner, m.id()));
        }
        int n = calls.size();
        CompletableFuture.allOf(calls.toArray(new CompletableFuture[0])).whenComplete((v, err) -> main(() -> {
            Utils.success(p, "mail", "delete.read-done", "%count%", String.valueOf(n));
            reopen(p, h);
        }));
    }

    // ─── player mail ───

    /** {@code /mail send <player>}: the package chest for that player (who must have joined, and isn't the sender). */
    public void startPackage(Player sender, String recipient) {
        Player online = Bukkit.getPlayerExact(recipient);
        CompletableFuture<UUID> uuid = online != null ? CompletableFuture.completedFuture(online.getUniqueId()) : client.uuidOf(recipient);
        uuid.whenComplete((id, err) -> main(() -> {
            if (!sender.isOnline()) return;
            if (err != null) {
                Utils.error(sender, "mail", "send.unknown", "%player%", recipient);
            } else if (id.equals(sender.getUniqueId())) {
                Utils.error(sender, "mail", "send.self");
            } else {
                sendBox.open(sender, id, online != null ? online.getName() : recipient);
            }
        }));
    }

    // ─── notices ───

    /** The "player_mail" webhook: the player got mail while online. */
    public void arrived(String uuid) {
        Player p;
        try {
            p = Bukkit.getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (p == null) return;
        Player player = p;
        client.list(player.getUniqueId(), null, 1).thenAccept(box -> main(() -> {
            if (!player.isOnline() || box.items().isEmpty()) return;
            Mail newest = box.items().get(0);
            Utils.success(player, "mail", "notice.arrived", "%sender%", MailRules.senderLine(newest, lang("box.server")), "%subject%", newest.subject());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
            if (box.unclaimed() >= MailRules.LIMIT) Utils.success(player, "mail", "notice.full", "%count%", String.valueOf(box.unclaimed()));
        }));
    }

    /** On join: "You have N new mail", clickable, when there is some (after the session had time to load). */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            client.list(p.getUniqueId(), null, 0).thenAccept(box -> main(() -> {
                int count = Math.max(box.unread(), box.unclaimed());
                if (count <= 0 || !p.isOnline()) return;
                p.sendMessage(MailItems.text(lang("notice.join").replace("%count%", String.valueOf(count)))
                        .clickEvent(ClickEvent.runCommand("/mail")));
            }));
        }, 40L);
    }
}
