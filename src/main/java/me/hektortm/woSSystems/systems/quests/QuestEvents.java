package me.hektortm.woSSystems.systems.quests;

import java.util.List;
import java.util.Map;

/**
 * The events a Wait for node can wait for, and whether a reported event counts
 * for a node. Each system reports what happened ({@code source.entry} plus
 * facts named like the catalog's parameters); a node's parameters must match.
 *
 * <p>{@link #REPORTED} is what this plugin reports. The portal's event catalog
 * ({@code AdminPortal/src/modules/quest_builder/quest-events.ts}) must offer
 * exactly these; both test files hold the list.</p>
 */
public final class QuestEvents {
    private QuestEvents() {}

    public static final List<String> REPORTED = List.of(
            "npc.right_click",
            "dialog.finished", "dialog.answer_picked",
            "interaction.triggered",
            "region.entered", "region.left",
            "citem.obtained", "citem.used",
            "gui.slot_clicked",
            "stat.reached",
            "currency.balance_reached", "currency.earned",
            "unlockable.unlocked",
            "fishing.caught",
            "quest.completed", "quest.failed");

    /** Parameters that only label the node in the portal. */
    private static final List<String> LABELS = List.of("npc_name");

    /**
     * How far an event moves a node waiting for {@code source.entry} with these
     * parameters: 0 when it doesn't match, the amount earned for
     * {@code currency.earned}, else 1.
     */
    public static long progress(String source, String entry, Map<String, String> params,
                                String eventSource, String eventEntry, Map<String, String> facts) {
        if (!source.equals(eventSource) || !entry.equals(eventEntry)) return 0;
        for (Map.Entry<String, String> p : params.entrySet()) {
            String want = p.getValue() == null ? "" : p.getValue().trim();
            if (want.isEmpty() || LABELS.contains(p.getKey()) || isTotal(source + "." + entry, p.getKey())) continue;
            if (!matches(source + "." + entry, p.getKey(), want, facts.get(p.getKey()))) return 0;
        }
        if ("currency.earned".equals(source + "." + entry)) return Math.max(0, number(facts.get("amount")));
        return 1;
    }

    /** How much a node needs: the amount for {@code currency.earned}, else its count. */
    public static long target(String source, String entry, Map<String, String> params, int count) {
        if ("currency.earned".equals(source + "." + entry)) return Math.max(1, number(params.get("amount")));
        return Math.max(1, count);
    }

    private static boolean matches(String event, String key, String want, String have) {
        if (have == null) return false;
        if (isThreshold(event, key)) return number(have) >= number(want);
        return want.equalsIgnoreCase(have.trim());
    }

    /** "reaches" parameters compare at least, not equal. */
    private static boolean isThreshold(String event, String key) {
        return ("stat.reached".equals(event) && "value".equals(key))
                || ("currency.balance_reached".equals(event) && "amount".equals(key));
    }

    /** An earned amount is the total to reach (see {@link #target}), not something each event must match. */
    private static boolean isTotal(String event, String key) {
        return "currency.earned".equals(event) && "amount".equals(key);
    }

    private static long number(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
