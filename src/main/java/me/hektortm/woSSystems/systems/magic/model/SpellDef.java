package me.hektortm.woSSystems.systems.magic.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.quests.model.QuestDef;
import me.hektortm.woSSystems.utils.model.Condition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A published spell version ({@code GET /v1/content/spells/published}): its name, icon,
 * settings (the root node) and the nodes that run on cast.
 * <p>
 * Levels (magic spec §3.5): {@code settings.levels} is a list of rows, row {@code i} being
 * level {@code i + 2}: {@code {"xp": <XP from the level before>, "set": {"cooldown": 1.5,
 * "bolt.damage": 6}}}. Each level is parsed once into its own tier, a SpellDef with that
 * level's numbers and only the {@code at_level} nodes it reaches; {@link #at} picks one.
 * The parsed spell itself is level 1.
 */
public final class SpellDef {

    private final String id;
    private final int version;
    private final String name;
    private final String description;
    private final String icon;
    private final JsonObject settings;
    private final SpellCatalog catalog;
    private final List<SpellNode> roots;
    private final List<String> warnings;
    private final int level;
    /** Every tier of the spell, level 1 first (shared by all of them). */
    private final List<SpellDef> tiers;
    /** {@code costs[i]}: the XP from level {@code i + 1} to {@code i + 2}. */
    private final double[] costs;
    private final boolean casterOnly;

    private SpellDef(String id, int version, String name, String description, String icon, JsonObject settings,
                     SpellCatalog catalog, List<SpellNode> roots, List<String> warnings, int level, List<SpellDef> tiers,
                     double[] costs, boolean casterOnly) {
        this.id = id;
        this.version = version;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.settings = settings;
        this.catalog = catalog;
        this.roots = roots;
        this.warnings = warnings;
        this.level = level;
        this.tiers = tiers;
        this.costs = costs;
        this.casterOnly = casterOnly;
    }

    public static SpellDef parse(JsonObject v, SpellCatalog catalog) {
        JsonObject settings = Json.object(v, "settings");
        JsonObject graph = Json.object(v, "graph");
        JsonArray rows = Json.array(settings, "levels");
        double[] costs = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            JsonElement xp = rows.get(i).isJsonObject() ? rows.get(i).getAsJsonObject().get("xp") : null;
            costs[i] = SpellNode.isNumber(xp) ? Math.max(0, xp.getAsDouble()) : 0;
        }
        boolean casterOnly = casterOnly(Json.array(graph, "on_cast"), catalog);
        List<SpellDef> tiers = new ArrayList<>();
        List<SpellDef> shared = Collections.unmodifiableList(tiers);
        Map<String, JsonElement> set = new LinkedHashMap<>();
        for (int lvl = 1; lvl <= rows.size() + 1; lvl++) {
            if (lvl >= 2 && rows.get(lvl - 2).isJsonObject()) {
                JsonObject row = Json.object(rows.get(lvl - 2).getAsJsonObject(), "set");
                for (Map.Entry<String, JsonElement> e : row.entrySet()) set.put(e.getKey(), e.getValue());
            }
            JsonObject s = settings;
            JsonObject g = graph;
            if (!set.isEmpty()) {
                s = settings.deepCopy();
                g = graph.deepCopy();
                apply(set, s, g);
            }
            List<String> warnings = new ArrayList<>();
            List<SpellNode> roots = SpellNode.parseList(Json.array(g, "on_cast"), catalog, warnings, "on_cast", 1, lvl);
            tiers.add(new SpellDef(Json.str(v, "spell_id", ""), Json.integer(v, "version", 0), Json.str(v, "name", ""),
                    Json.str(v, "description", ""), Json.str(v, "icon", ""), s, catalog, List.copyOf(roots),
                    List.copyOf(warnings), lvl, shared, costs, casterOnly));
        }
        return tiers.get(0);
    }

    /** Sets each level key: a setting ("cooldown") or a node's field ("bolt.damage", split at the last dot). */
    private static void apply(Map<String, JsonElement> set, JsonObject settings, JsonObject graph) {
        for (Map.Entry<String, JsonElement> e : set.entrySet()) {
            String key = e.getKey();
            int dot = key.lastIndexOf('.');
            if (dot < 0) {
                settings.add(key, e.getValue());
                continue;
            }
            JsonObject node = findNode(graph, key.substring(0, dot));
            if (node != null) node.add(key.substring(dot + 1), e.getValue());
        }
    }

    private static JsonObject findNode(JsonElement e, String id) {
        if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) {
                JsonObject found = findNode(c, id);
                if (found != null) return found;
            }
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && id.equals(Json.str(o, "id", null))) return o;
            for (Map.Entry<String, JsonElement> c : o.entrySet()) {
                if (!c.getValue().isJsonArray()) continue;
                JsonObject found = findNode(c.getValue(), id);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** No shape but {@code self} anywhere: every effect is on the caster (spec §3.5, XP for hitting yourself). */
    private static boolean casterOnly(JsonArray nodes, SpellCatalog catalog) {
        for (JsonElement e : nodes) {
            if (!e.isJsonObject()) continue;
            JsonObject n = e.getAsJsonObject();
            String type = Json.str(n, "type", "");
            SpellCatalog.Kind k = catalog.kind(type);
            if (k == null) continue;
            if (k.isShape() && !"self".equals(type)) return false;
            for (String outlet : k.outlets()) {
                if (!casterOnly(Json.array(n, outlet), catalog)) return false;
            }
        }
        return true;
    }

    public String id() { return id; }

    public int version() { return version; }

    /** The name players see; the id made readable when it has none. */
    public String name() {
        return name.isBlank() ? id : name;
    }

    public String description() { return description; }

    public String icon() { return icon; }

    public List<SpellNode> roots() { return roots; }

    /** What could not be read (unknown types …), for one warning at load. */
    public List<String> warnings() { return warnings; }

    public SpellCatalog catalog() { return catalog; }

    // ── levels ──

    /** The level of this tier (1 for the parsed spell). */
    public int level() { return level; }

    /** The highest level (1 without a level table). */
    public int maxLevel() { return tiers.size(); }

    /** The spell at a level, clamped to 1 .. {@link #maxLevel()} (a lowered table counts as its max). */
    public SpellDef at(int lvl) {
        return tiers.get(Math.max(1, Math.min(tiers.size(), lvl)) - 1);
    }

    /** The XP needed from {@code lvl} to the next level; 0 at or above the max. */
    public double xpToNext(int lvl) {
        int i = Math.max(1, lvl) - 1;
        return i < costs.length ? costs[i] : 0;
    }

    /** Every effect of the spell is on the caster (no shape but self). */
    public boolean casterOnly() { return casterOnly; }

    // ── settings ──

    private JsonElement setting(String key) {
        JsonElement v = settings.get(key);
        return v == null || v.isJsonNull() ? catalog.settingDefault(key) : v;
    }

    private double settingNum(String key) {
        JsonElement v = settings.get(key);
        if (SpellNode.isNumber(v)) return v.getAsDouble();
        JsonElement d = catalog.settingDefault(key);
        return SpellNode.isNumber(d) ? d.getAsDouble() : 0;
    }

    private String settingStr(String key) {
        JsonElement v = setting(key);
        if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) return v.getAsString();
        JsonElement d = catalog.settingDefault(key);
        return d != null && d.isJsonPrimitive() && d.getAsJsonPrimitive().isString() ? d.getAsString() : "";
    }

    private boolean settingBool(String key) {
        JsonElement v = setting(key);
        if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) return v.getAsBoolean();
        JsonElement d = catalog.settingDefault(key);
        return d != null && d.isJsonPrimitive() && d.getAsJsonPrimitive().isBoolean() && d.getAsBoolean();
    }

    public double manaCost() { return settingNum("mana_cost"); }

    /** Seconds, before the cooldown attribute. */
    public double cooldown() { return settingNum("cooldown"); }

    public List<Condition> conditions() {
        return QuestDef.conditions(Json.array(settings, "conditions"));
    }

    public boolean matchOne() { return "one".equals(settingStr("condition_match")); }

    public String failMessage() { return settingStr("fail_message"); }

    public String castSound() { return settingStr("cast_sound"); }

    public String castParticle() { return settingStr("cast_particle"); }

    public String castColor() { return settingStr("cast_color"); }

    /** Seconds it charges before it fires (0: at once). */
    public double chargeTime() { return settingNum("charge_time"); }

    public String chargeParticle() { return settingStr("charge_particle"); }

    public String chargeColor() { return settingStr("charge_color"); }

    public String chargeSound() { return settingStr("charge_sound"); }

    public boolean chargeSoundLoop() { return settingBool("charge_sound_loop"); }

    /** Slowness while charging: 0 off, 1–5 like Slowness I–V. */
    public int chargeSlow() { return (int) settingNum("charge_slow"); }

    public boolean chargeInterrupt() { return settingBool("charge_interrupt"); }

    public double xpCast() { return settingNum("xp_cast"); }

    public double xpHit() { return settingNum("xp_hit"); }

    public double xpKill() { return settingNum("xp_kill"); }

    /** Command lines run on each new level ({level}: the new level). */
    public List<String> levelUpActions() { return Json.strings(settings, "level_up_actions"); }
}
