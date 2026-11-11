package me.hektortm.woSSystems.systems.magic.ring;

import me.hektortm.woSSystems.systems.magic.MagicRules;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Where things sit in the spellbook's two screens (spec 2026-11-11 §3, §4), and the rules of
 * binding. Pure, so it is tested without a server.
 */
public final class SpellbookLayout {

    /** The main screen's ring slots by ring position: 0 = the HUD ring's top, then clockwise. */
    static final int[] RING_SLOTS = {3, 5, 16, 43, 50, 48, 37, 10};
    static final int SKIN = 22;
    static final int WAND = 31;
    static final int PAGE_ARROW = 53;

    /** The picker: learned spells in slots 0–44, then the bottom row's controls. */
    static final int PER_PAGE = 45;
    static final int BACK = 45;
    static final int PREV = 48;
    static final int CLEAR = 49;
    static final int NEXT = 50;
    /** The search button, and the longest search a player can type. */
    static final int SEARCH = 53;
    static final int SEARCH_MAX = 32;

    private SpellbookLayout() { }

    /** The ring position (0–7) of a main screen slot, or -1. */
    static int posOfSlot(int raw) {
        for (int pos = 0; pos < RING_SLOTS.length; pos++) {
            if (RING_SLOTS[pos] == raw) return pos;
        }
        return -1;
    }

    /** The loadout index (0–15) of a ring position on a page (0 or 1). */
    static int loadoutIndex(int page, int pos) {
        return page * MagicRules.SLOTS + pos;
    }

    /** Puts the spell on {@code index}, taking it off every other slot (one slot per spell); "" empties it. */
    static void assign(String[] loadout, int index, String spellId) {
        if (!spellId.isEmpty()) {
            for (int i = 0; i < loadout.length; i++) {
                if (spellId.equals(loadout[i])) loadout[i] = "";
            }
        }
        loadout[index] = spellId;
    }

    /** How many picker pages {@code known} spells fill (at least one). */
    static int pickerPages(int known) {
        return Math.max(1, (known + PER_PAGE - 1) / PER_PAGE);
    }

    /**
     * The spells whose plain name holds {@code query} anywhere, ignoring case, in their order;
     * an empty (or blank) query keeps them all. Only names are searched (owner, 2026-11-11).
     */
    static List<String> search(List<String> ids, Function<String, String> plainNameOf, @Nullable String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return ids;
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            String name = plainNameOf.apply(id);
            if (name != null && name.toLowerCase(Locale.ROOT).contains(q)) out.add(id);
        }
        return out;
    }

    /** The spells on picker page {@code page} (empty past the last). */
    static List<String> pickerSlice(List<String> known, int page) {
        int from = page * PER_PAGE;
        if (page < 0 || from >= known.size()) return List.of();
        return known.subList(from, Math.min(known.size(), from + PER_PAGE));
    }
}
