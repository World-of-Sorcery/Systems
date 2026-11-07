package me.hektortm.woSSystems.systems.mail;

import me.hektortm.woSSystems.systems.mail.model.MailAttachment;
import me.hektortm.woSSystems.systems.quests.QuestHooks;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * How the plugin hands items to a player (spec: "a full inventory goes to the mail"): what fits goes into the
 * inventory, the rest into one overflow mail. If the mail can't be written (the API is down), the rest drops at the
 * player's feet as it always did. Every give of the server goes through here; picking items up stays vanilla.
 */
public final class ItemDelivery {

    /** Writes an overflow mail; completes false when it gave up. */
    @FunctionalInterface
    public interface Overflow {
        CompletableFuture<Boolean> overflow(UUID player, String reason, List<MailAttachment> items);
    }

    /** Tells the player something (a lang/mail.yml key with a count). */
    @FunctionalInterface
    public interface Notice {
        void send(Player player, String key, int count);
    }

    private final Overflow mail;
    private final Function<ItemStack, MailAttachment> attach;
    private final Function<ItemStack, String> citemOf;
    private final Consumer<Runnable> mainThread;
    private final Notice notice;
    private final Logger log;

    /**
     * @param attach     turns a leftover into a stack attachment ({@link Stacks#attachment})
     * @param citemOf    the custom item id of a stack, or null (for the quest event)
     * @param mainThread runs a task on the server thread
     */
    public ItemDelivery(Overflow mail, Function<ItemStack, MailAttachment> attach, Function<ItemStack, String> citemOf,
                        Consumer<Runnable> mainThread, Notice notice, Logger log) {
        this.mail = mail;
        this.attach = attach;
        this.citemOf = citemOf;
        this.mainThread = mainThread;
        this.notice = notice;
        this.log = log;
    }

    public void give(Player p, ItemStack stack, String reason) {
        give(p, List.of(stack), reason);
    }

    /** Gives the stacks; what doesn't fit goes to the mail with {@code reason} as its subject. */
    public void give(Player p, List<ItemStack> stacks, String reason) {
        if (stacks.isEmpty()) return;
        // A player who already left: their inventory object is no longer saved, so all of it goes to the mail.
        Map<Integer, ItemStack> leftover = new HashMap<>();
        if (p.isOnline()) {
            int[] before = new int[stacks.size()]; // addItem lowers the amounts of what is left over
            for (int i = 0; i < before.length; i++) before[i] = stacks.get(i).getAmount();
            leftover.putAll(p.getInventory().addItem(stacks.toArray(new ItemStack[0])));
            for (String id : obtained(stacks, before, leftover, citemOf)) {
                QuestHooks.report(p, "citem", "obtained", Map.of("citem", id));
            }
        } else {
            for (int i = 0; i < stacks.size(); i++) leftover.put(i, stacks.get(i));
        }
        if (leftover.isEmpty()) return;
        List<ItemStack> rest = new ArrayList<>(leftover.values());
        List<MailAttachment> items = new ArrayList<>();
        for (ItemStack s : rest) items.add(attach.apply(s));
        Location where = p.getLocation();
        mail.overflow(p.getUniqueId(), MailRules.overflowSubject(reason), items)
                .exceptionally(e -> false)
                .thenAccept(ok -> mainThread.accept(() -> afterOverflow(p, rest, where, ok)));
    }

    private void afterOverflow(Player p, List<ItemStack> rest, @Nullable Location where, boolean ok) {
        if (ok) {
            if (p.isOnline()) notice.send(p, "overflow.notice", rest.size());
            return;
        }
        log.warning("[Mail] overflow failed for " + p.getName() + ": " + rest.size() + " stack(s) dropped instead");
        Location at = p.isOnline() ? p.getLocation() : where;
        if (p.isOnline()) {
            Map<Integer, ItemStack> still = p.getInventory().addItem(rest.toArray(new ItemStack[0]));
            rest = new ArrayList<>(still.values());
        }
        if (at != null && at.getWorld() != null) {
            for (ItemStack s : rest) at.getWorld().dropItemNaturally(at, s);
        }
        if (p.isOnline()) notice.send(p, "overflow.dropped", rest.size());
    }

    /**
     * The custom items that went into the inventory, at least in part (citem.obtained; mailed ones count when
     * claimed). {@code before} holds each stack's amount from before addItem.
     */
    static Set<String> obtained(List<ItemStack> stacks, int[] before, Map<Integer, ItemStack> leftover,
                                Function<ItemStack, String> citemOf) {
        Set<String> obtained = new LinkedHashSet<>();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack left = leftover.get(i);
            if (left != null && left.getAmount() >= before[i]) continue; // nothing of it fitted
            String id = citemOf.apply(stacks.get(i));
            if (id != null) obtained.add(id);
        }
        return obtained;
    }
}
