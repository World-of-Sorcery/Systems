package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.citems.CitemManager;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.systems.quests.model.QuestNode;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Clicks on things that run an interaction (a bound block, an NPC, a placed
 * custom item) while a quest or sequence waits on them: the step's checks
 * (where, holding what), taking the item, counting clicks, wrong clicks and
 * failure rules. A click a run takes does not run the interaction's own actions.
 */
public final class QuestClicks {

    private final QuestEngine e;
    private final RecipeMenu menu;

    QuestClicks(QuestEngine engine) {
        this.e = engine;
        this.menu = new RecipeMenu(engine);
    }

    public RecipeMenu menu() {
        return menu;
    }

    /**
     * A player clicked something running {@code interactionId} at {@code key}.
     * True when a quest / sequence took the click or the recipe menu opened.
     */
    public boolean click(Player p, String interactionId, @Nullable InteractionKey key) {
        String k = key == null ? null : key.getKey();
        List<QuestRun> runs = new ArrayList<>(e.runsOf(p).values());
        runs.sort(Comparator.comparing(r -> !r.sequence())); // the sequence first: it owns its targets
        for (QuestRun run : runs) {
            QuestDef def = e.defOf(run);
            if (def == null || !run.running()) continue;
            QuestNode accepting = accepting(run, def, interactionId, k);
            if (accepting != null) {
                accept(p, run, def, accepting, k);
                return true;
            }
            if (run.sequence() && isWrongClick(run, def, interactionId, k)) {
                wrong(p, run, def, interactionId, k, "hint.wrong", "%target%", QuestRules.pretty(nextTarget(run, def)));
                return true;
            }
        }
        return e.runningSequence(p) == null && menu.open(p, interactionId, k);
    }

    @Nullable
    private QuestNode accepting(QuestRun run, QuestDef def, String interactionId, @Nullable String k) {
        for (String id : run.active()) {
            QuestNode n = def.node(id);
            if (n != null && n.is("click") && interactionId.equals(n.str("target")) && anchorAllows(run, n, k)) return n;
        }
        return null;
    }

    /** A step "at the anchor" only counts at the block / NPC the sequence started on. */
    private static boolean anchorAllows(QuestRun run, QuestNode n, @Nullable String k) {
        if (!n.flag("anchor", true) || run.anchor() == null) return true;
        return run.anchor().equals(k);
    }

    /**
     * A click on one of the sequence's targets it does not want now. A click on
     * another block of the anchor's kind (somebody else's stove) is no business
     * of this sequence.
     */
    private static boolean isWrongClick(QuestRun run, QuestDef def, String interactionId, @Nullable String k) {
        if (!def.clickTargets().contains(interactionId)) return false;
        boolean anchored = def.nodes().values().stream()
                .anyMatch(n -> n.is("click") && interactionId.equals(n.str("target")) && n.flag("anchor", true));
        return !anchored || run.anchor() == null || run.anchor().equals(k);
    }

    private static String nextTarget(QuestRun run, QuestDef def) {
        for (String id : run.active()) {
            QuestNode n = def.node(id);
            if (n != null && n.is("click")) return n.str("target");
        }
        return "";
    }

    private void accept(Player p, QuestRun run, QuestDef def, QuestNode n, @Nullable String k) {
        if (!holdingOk(p, run, def, n, k)) return;
        if (n.flag("anchor", true) && run.anchor() == null && k != null) run.anchor(k);
        run.overshoot(null);
        Location at = where(p, k);
        e.visuals().effect(p, at, true, sound(def, n, true), particles(def, n));
        int done = run.addCount(n.id(), 1);
        int clicks = Math.max(1, n.integer("clicks", 1));
        e.debug(p, "click " + n.str("target") + " " + done + "/" + clicks);
        if (done < clicks) {
            e.save(p.getUniqueId(), run);
            e.refresh(p);
            return;
        }
        if (QuestRules.overshootFails(n)) run.overshoot(n.id());
        if (!n.object("display").entrySet().isEmpty()) run.displays().add(n.id());
        e.leave(p, run, n.id(), "done");
    }

    /** The step's "holding": checked, and taken when it says so. False (a wrong click) when the hand is wrong. */
    private boolean holdingOk(Player p, QuestRun run, QuestDef def, QuestNode n, @Nullable String k) {
        JsonObject h = n.object("holding");
        String citem = Json.str(h, "citem", "");
        String material = Json.str(h, "material", "");
        if (citem.isEmpty() && material.isEmpty()) return true;
        int amount = Math.max(1, Json.integer(h, "amount", 1));
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean right = citem.isEmpty()
                ? hand.getType() == Material.matchMaterial(material) && CitemManager.citemIdOf(hand) == null
                : citem.equals(CitemManager.citemIdOf(hand));
        if (!right || hand.getAmount() < amount) {
            String what = citem.isEmpty() ? material : citem;
            wrong(p, run, def, n.str("target"), k, "hint.holding", "%amount%", String.valueOf(amount), "%item%", QuestRules.pretty(what));
            return false;
        }
        if (Json.bool(h, "take", false)) {
            hand.setAmount(hand.getAmount() - amount);
            p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
            if (!citem.isEmpty()) run.taken().merge(citem, amount, Integer::sum);
        }
        return true;
    }

    /** A wrong click: a hint, the wrong effect, and the failure rules that count wrong clicks. */
    private void wrong(Player p, QuestRun run, QuestDef def, String interactionId, @Nullable String k, String hint, String... replace) {
        e.visuals().effect(p, where(p, k), false, def.theme("wrong_sound"), "");
        hint(p, hint, replace);
        if (run.overshoot() != null) {
            QuestNode o = def.node(run.overshoot());
            if (o != null && interactionId.equals(o.str("target"))) {
                fail(p, run, o, "error.overshoot");
                return;
            }
        }
        for (String id : new ArrayList<>(run.active())) {
            QuestNode n = def.node(id);
            if (n != null && n.is("click") && QuestRules.tooManyWrong(n, run.addWrong(id))) {
                fail(p, run, n, "error.wrong-clicks");
                return;
            }
        }
        e.save(p.getUniqueId(), run);
    }

    /** A failure rule hit: the run goes on through that step's "failed" exit (none connected: it fails). */
    void fail(Player p, QuestRun run, QuestNode n, String message) {
        hint(p, message);
        e.debug(p, "failed at " + n.id() + " (" + message + ")");
        run.active().clear();
        run.overshoot(null);
        e.leave(p, run, n.id(), "failed");
    }

    private void hint(Player p, String key, String... replace) {
        String text = e.plugin().getLangManager().getMessage("quests", key);
        for (int i = 0; i + 1 < replace.length; i += 2) text = text.replace(replace[i], replace[i + 1]);
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', text)));
    }

    private static Location where(Player p, @Nullable String k) {
        Location l = QuestPlaces.ofKey(k);
        return l == null ? p.getLocation().getBlock().getLocation() : l;
    }

    private static String sound(QuestDef def, QuestNode n, boolean right) {
        String own = Json.str(n.object("effects"), "sound", "");
        if (!own.isEmpty()) return own;
        return def.theme(right ? "click_sound" : "wrong_sound");
    }

    private static String particles(QuestDef def, QuestNode n) {
        JsonObject fx = n.object("effects");
        if (Json.bool(fx, "off", false)) return "none";
        String own = Json.str(fx, "particles", "");
        return own.isEmpty() ? def.theme("particles") : own;
    }
}
