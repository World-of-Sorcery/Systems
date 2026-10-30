package me.hektortm.woSSystems.systems.magic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.magic.model.MagicSettings;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One online player's magic (spec §4, §5.3, §6.1): mana, the base max / regen, the wand
 * record, the ring loadout (16 slots: page 1 then page 2) and the known spells, as stored
 * in wos-api; plus what lives only while online (selection, cooldowns, item attributes).
 */
public final class MagicData {

    /** The wand record; the item is drawn from it. */
    public record Wand(String color, String skin) { }

    /** A known spell's level and the XP gathered towards the next one (spec §3.5). */
    public record Progress(int level, double xp) {
        public static final Progress START = new Progress(1, 0);
    }

    private double mana;
    private double baseMax;
    private double baseRegen;
    @Nullable
    private Wand wand;
    private final String[] loadout = new String[MagicRules.SLOTS * MagicRules.PAGES];
    private final Set<String> spells = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<String, Progress> progress = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    // Only while online.
    private int selPage = -1;
    private int selPos = -1;
    private final Map<String, Long> cooldownEnds = new ConcurrentHashMap<>();
    private MagicAttributes attributes = MagicAttributes.NONE;

    private MagicData() {
        Arrays.fill(loadout, "");
    }

    /** From the session's {@code magic} (null for a player without magic data yet) and {@code spells}. */
    public static MagicData fromSession(@Nullable JsonObject magic, List<String> spells, MagicSettings start) {
        return fromSession(magic, spells, null, start);
    }

    /** As above, with the session's {@code spell_progress} ({id: {level, xp}}; a known spell without one starts at level 1). */
    public static MagicData fromSession(@Nullable JsonObject magic, List<String> spells, @Nullable JsonObject spellProgress, MagicSettings start) {
        MagicData d = new MagicData();
        d.spells.addAll(spells);
        for (String id : spells) {
            JsonElement p = spellProgress == null ? null : spellProgress.get(id);
            d.progress.put(id, p != null && p.isJsonObject()
                    ? new Progress(Math.max(1, Json.integer(p.getAsJsonObject(), "level", 1)), Math.max(0, num(p.getAsJsonObject(), "xp")))
                    : Progress.START);
        }
        if (magic == null) {
            d.baseMax = start.startMaxMana();
            d.baseRegen = start.startManaRegen();
            d.mana = MagicRules.clampMana(start.startMana(), d.baseMax);
            return d;
        }
        d.mana = num(magic, "mana");
        d.baseMax = num(magic, "max_mana");
        d.baseRegen = num(magic, "mana_regen");
        JsonElement w = Json.field(magic, "wand");
        if (w != null && w.isJsonObject()) {
            d.wand = new Wand(Json.str(w.getAsJsonObject(), "color", "#FFFFFF"), Json.str(w.getAsJsonObject(), "skin", ""));
        }
        List<String> l = Json.strings(magic, "loadout");
        for (int i = 0; i < l.size() && i < d.loadout.length; i++) d.loadout[i] = l.get(i) == null ? "" : l.get(i);
        return d;
    }

    private static double num(JsonObject o, String key) {
        JsonElement e = Json.field(o, key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
    }

    /** The body of {@code PUT /v1/players/{uuid}/magic}. */
    public synchronized Map<String, Object> body() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("mana", mana);
        b.put("max_mana", baseMax);
        b.put("mana_regen", baseRegen);
        Map<String, Object> w = null;
        if (wand != null) {
            w = new LinkedHashMap<>();
            w.put("color", wand.color());
            w.put("skin", wand.skin());
        }
        b.put("wand", w);
        b.put("loadout", List.of(loadout.clone()));
        Map<String, Object> sp = new LinkedHashMap<>();
        for (Map.Entry<String, Progress> e : progress.entrySet()) {
            sp.put(e.getKey(), Map.of("level", e.getValue().level(), "xp", e.getValue().xp()));
        }
        b.put("spell_progress", sp);
        return b;
    }

    // ── mana ──

    public synchronized double mana() { return mana; }

    public synchronized double baseMax() { return baseMax; }

    public synchronized double baseRegen() { return baseRegen; }

    /** The effective max (base + item bonuses). */
    public synchronized double max() { return MagicRules.effectiveMax(baseMax, attributes); }

    public synchronized double regen() { return MagicRules.effectiveRegen(baseRegen, attributes); }

    public synchronized MagicAttributes attributes() { return attributes; }

    /** New item bonuses; mana above the new max is cut. */
    public synchronized void setAttributes(MagicAttributes a) {
        this.attributes = a;
        double m = MagicRules.clampMana(mana, max());
        if (m != mana) {
            mana = m;
            dirty = true;
        }
    }

    public synchronized void setMana(double value) {
        double m = MagicRules.clampMana(value, max());
        if (m != mana) {
            mana = m;
            dirty = true;
        }
    }

    public synchronized void setBaseMax(double value) {
        baseMax = Math.max(0, value);
        mana = MagicRules.clampMana(mana, max());
        dirty = true;
    }

    /** One regen step of {@code seconds}; true when the mana changed. */
    public synchronized boolean regenerate(double seconds) {
        double m = MagicRules.regenStep(mana, max(), regen(), seconds);
        if (m == mana) return false;
        mana = m;
        dirty = true;
        return true;
    }

    // ── wand ──

    @Nullable
    public synchronized Wand wand() { return wand; }

    public synchronized void setWand(@Nullable Wand w) {
        wand = w;
        dirty = true;
    }

    // ── spells, loadout, selection ──

    /** Known spells in learn order (a copy). */
    public List<String> spells() {
        synchronized (spells) {
            return List.copyOf(spells);
        }
    }

    public boolean knows(String id) {
        return spells.contains(id);
    }

    /** Learns a spell and puts it in the first free ring slot; false when already known. */
    public synchronized boolean learn(String id) {
        if (!spells.add(id)) return false;
        progress.put(id, Progress.START);
        if (!Arrays.asList(loadout).contains(id)) {
            int free = MagicRules.firstFreeSlot(loadout);
            if (free >= 0) loadout[free] = id;
        }
        dirty = true;
        return true;
    }

    /** Forgets a spell and clears its ring slots and the selection on it; false when not known. */
    public synchronized boolean forget(String id) {
        if (!spells.remove(id)) return false;
        progress.remove(id); // forgetting resets the level (spec §3.5)
        String sel = selected();
        for (int i = 0; i < loadout.length; i++) {
            if (id.equals(loadout[i])) loadout[i] = "";
        }
        if (id.equals(sel)) select(-1, -1);
        dirty = true;
        return true;
    }

    public synchronized String[] loadout() { return loadout.clone(); }

    /** Puts a known spell (or "" to clear) in loadout slot {@code index} (0..15). */
    public synchronized void setSlot(int index, String spellId) {
        if (index < 0 || index >= loadout.length) return;
        loadout[index] = spellId == null ? "" : spellId;
        dirty = true;
    }

    /** The spell in a ring slot that the player still knows, or null. */
    @Nullable
    public synchronized String slot(int page, int pos) {
        if (page < 0 || pos < 0 || page >= MagicRules.PAGES || pos >= MagicRules.SLOTS) return null;
        String id = loadout[page * MagicRules.SLOTS + pos];
        return id.isEmpty() || !spells.contains(id) ? null : id;
    }

    public synchronized void select(int page, int pos) {
        selPage = page;
        selPos = pos;
    }

    public synchronized int selectedPage() { return selPage; }

    public synchronized int selectedPos() { return selPos; }

    /** The selected spell, or null (nothing selected, or the slot no longer holds a known spell). */
    @Nullable
    public synchronized String selected() {
        return slot(selPage, selPos);
    }

    // ── levels ──

    /** A spell's progress (level 1, no XP for a spell not known). */
    public Progress progress(String spellId) {
        return progress.getOrDefault(spellId, Progress.START);
    }

    /** The saved level of a spell (1 when not known). */
    public int level(String spellId) {
        return progress(spellId).level();
    }

    /** New progress of a known spell; nothing for one not known. */
    public void setProgress(String spellId, Progress p) {
        if (!knows(spellId)) return;
        progress.put(spellId, p);
        dirty = true;
    }

    // ── cooldowns (per spell, only while online) ──

    public void startCooldown(String spellId, long until) {
        cooldownEnds.put(spellId, until);
    }

    /** Milliseconds left on the spell's cooldown (0: ready). */
    public long cooldownLeft(String spellId, long now) {
        Long end = cooldownEnds.get(spellId);
        return end == null ? 0 : Math.max(0, end - now);
    }

    // ── saving ──

    public boolean isDirty() { return dirty; }

    public void markDirty() { dirty = true; }

    /** Clears the dirty flag; true when there was something to save. */
    public boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }
}
