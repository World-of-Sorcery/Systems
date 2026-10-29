package me.hektortm.woSSystems.systems.magic.cmd;

import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicRules;
import me.hektortm.woSSystems.systems.magic.MagicSystem;
import me.hektortm.woSSystems.systems.magic.MagicText;
import me.hektortm.woSSystems.systems.magic.cast.CastService;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.systems.magic.model.WandSkin;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

/**
 * The magic commands (spec §10). Content runs them as the console ({@code spell learn @p
 * ember} …), so they answer only a player who typed them, except for errors.
 * <ul>
 *   <li>{@code /spell learn|forget <player> <id>}, {@code /spell cast <player> <id> [free]},
 *       {@code /spell test <id> [level]} (the draft), {@code /spell list [player]},
 *       {@code /spell xp give|take <player> <id> <amount>}, {@code /spell level set <player> <id> <level>};</li>
 *   <li>{@code /mana set|give|take <player> <amount>}, {@code /mana max set|give|take <player> <amount>};</li>
 *   <li>{@code /wand give|restore <player>}, {@code /wand colour <player> <#RRGGBB|random>},
 *       {@code /wand skin <player> <skin|default>}, {@code /wand} (get it back, pick a skin);</li>
 *   <li>{@code /spells} (the spellbook).</li>
 * </ul>
 */
public final class MagicCommands {

    private MagicCommands() { }

    public static CommandExecutor spell(Plugin plugin, MagicSystem magic) {
        TabArg spells = TabArg.content("spells");
        return new MagicCommand("error.usage.spell")
                .sub("learn", Permissions.SPELL_LEARN, List.of(TabArg.PLAYER, spells), (s, a) -> learn(magic, s, a, true))
                .sub("forget", Permissions.SPELL_LEARN, List.of(TabArg.PLAYER, spells), (s, a) -> learn(magic, s, a, false))
                .sub("cast", Permissions.SPELL_CAST, List.of(TabArg.PLAYER, spells, TabArg.of("free")), (s, a) -> cast(magic, s, a))
                .sub("test", Permissions.SPELL_TEST, List.of(spells, TabArg.NONE), (s, a) -> test(plugin, magic, s, a))
                .sub("list", Permissions.SPELL_LIST, List.of(TabArg.PLAYER), (s, a) -> list(magic, s, a))
                .sub("xp", Permissions.SPELL_PROGRESS, List.of(TabArg.of("give", "take"), TabArg.PLAYER, spells, TabArg.NONE), (s, a) -> xp(magic, s, a))
                .sub("level", Permissions.SPELL_PROGRESS, List.of(TabArg.of("set"), TabArg.PLAYER, spells, TabArg.NONE), (s, a) -> level(magic, s, a))
                .withHelp("magic_spell");
    }

    private static void learn(MagicSystem magic, CommandSender s, String[] a, boolean learn) {
        if (a.length < 2) {
            Utils.info(s, MagicText.FILE, learn ? "error.usage.learn" : "error.usage.forget");
            return;
        }
        Player p = MagicCommand.online(s, a[0]);
        if (p == null) return;
        String id = a[1];
        if (learn && magic.stores().spell(id) == null) {
            Utils.error(s, MagicText.FILE, "error.unknown_spell", "%id%", id);
            return;
        }
        boolean changed = learn ? magic.players().learn(p.getUniqueId(), id) : magic.players().forget(p.getUniqueId(), id);
        if (!MagicCommand.answer(s)) return;
        String key = learn ? (changed ? "learned" : "already_known") : (changed ? "forgot" : "not_known");
        Utils.info(s, MagicText.FILE, key, "%player%", p.getName(), "%spell%", name(magic, id));
    }

    private static String name(MagicSystem magic, String id) {
        SpellDef d = magic.stores().spell(id);
        return d == null ? id : d.name();
    }

    private static void cast(MagicSystem magic, CommandSender s, String[] a) {
        if (a.length < 2) {
            Utils.info(s, MagicText.FILE, "error.usage.cast");
            return;
        }
        Player p = MagicCommand.online(s, a[0]);
        if (p == null) return;
        SpellDef d = magic.stores().spell(a[1]);
        if (d == null) {
            Utils.error(s, MagicText.FILE, "error.unknown_spell", "%id%", a[1]);
            return;
        }
        boolean free = a.length > 2 && "free".equalsIgnoreCase(a[2]);
        CastService.Result r = magic.cast().cast(p, d, free ? CastService.Mode.FREE : CastService.Mode.CONTENT);
        magic.cast().explain(p, d, r); // the player learns why nothing happened
        if (MagicCommand.answer(s) && s != p && r != CastService.Result.CAST && r != CastService.Result.CHARGING) {
            Utils.error(s, MagicText.FILE, "cast_failed", "%spell%", d.name(), "%result%", r.name().toLowerCase(Locale.ROOT));
        }
    }

    private static void test(Plugin plugin, MagicSystem magic, CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            Utils.error(s, MagicText.FILE, "error.players_only");
            return;
        }
        if (a.length < 1) {
            Utils.info(s, MagicText.FILE, "error.usage.test");
            return;
        }
        String id = a[0];
        int level = 0;
        if (a.length > 1) {
            try {
                level = Integer.parseInt(a[1]);
            } catch (NumberFormatException e) {
                level = -1;
            }
            if (level < 1) {
                Utils.error(p, MagicText.FILE, "error.level", "%level%", a[1]);
                return;
            }
        }
        int testLevel = level;
        Utils.info(p, MagicText.FILE, "test.loading", "%id%", id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SpellDef d;
            try {
                d = magic.stores().fetchDraft(id);
            } catch (Exception e) {
                d = null;
            }
            SpellDef draft = d;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                if (draft == null) {
                    Utils.error(p, MagicText.FILE, "error.unknown_spell", "%id%", id);
                    return;
                }
                CastService.Result r = magic.cast().cast(p, draft, CastService.Mode.TEST, testLevel);
                if (r == CastService.Result.CAST || r == CastService.Result.CHARGING) {
                    Utils.info(p, MagicText.FILE, "test.cast", "%spell%", draft.name(), "%skipped%", String.valueOf(draft.warnings().size()),
                            "%level%", String.valueOf(draft.at(testLevel > 0 ? testLevel : 1).level()), "%max%", String.valueOf(draft.maxLevel()));
                } else {
                    magic.cast().explain(p, draft, r);
                }
            });
        });
    }

    private static void list(MagicSystem magic, CommandSender s, String[] a) {
        Player p = a.length > 0 ? MagicCommand.online(s, a[0]) : s instanceof Player self ? self : null;
        if (p == null) {
            if (a.length == 0) {
                List<String> all = new ArrayList<>();
                for (SpellDef d : magic.stores().spells()) all.add(d.name() + " (" + d.id() + ")");
                Utils.info(s, MagicText.FILE, "list.all", "%spells%", all.isEmpty() ? "-" : String.join(", ", all));
            }
            return;
        }
        MagicData d = magic.players().data(p);
        List<String> names = new ArrayList<>();
        if (d != null) for (String id : d.spells()) names.add(name(magic, id));
        Utils.info(s, MagicText.FILE, names.isEmpty() ? "list.none" : "list.known", "%player%", p.getName(),
                "%spells%", String.join(", ", names), "%mana%", d == null ? "0" : MagicRules.manaText(d.mana()),
                "%max%", d == null ? "0" : MagicRules.manaText(d.max()));
    }

    /** {@code spell xp give|take <player> <spell> <amount>} (spec §3.5). */
    private static void xp(MagicSystem magic, CommandSender s, String[] a) {
        if (a.length < 4 || !("give".equalsIgnoreCase(a[0]) || "take".equalsIgnoreCase(a[0]))) {
            Utils.info(s, MagicText.FILE, "error.usage.xp");
            return;
        }
        Player p = MagicCommand.online(s, a[1]);
        SpellDef d = p == null ? null : knownSpell(magic, s, p, a[2]);
        if (d == null) return;
        double amount = amount(a[3]);
        if (amount < 0) {
            Utils.error(s, MagicText.FILE, "error.amount", "%amount%", a[3]);
            return;
        }
        if ("give".equalsIgnoreCase(a[0])) magic.levels().giveXp(p, d, amount);
        else magic.levels().takeXp(p, d, amount);
        progressAnswer(magic, s, p, d);
    }

    /** {@code spell level set <player> <spell> <level>}: 1 .. the spell's highest level (spec §3.5). */
    private static void level(MagicSystem magic, CommandSender s, String[] a) {
        if (a.length < 4 || !"set".equalsIgnoreCase(a[0])) {
            Utils.info(s, MagicText.FILE, "error.usage.level");
            return;
        }
        Player p = MagicCommand.online(s, a[1]);
        SpellDef d = p == null ? null : knownSpell(magic, s, p, a[2]);
        if (d == null) return;
        int level;
        try {
            level = Integer.parseInt(a[3]);
        } catch (NumberFormatException e) {
            level = -1;
        }
        if (!magic.levels().setLevel(p, d, level)) {
            Utils.error(s, MagicText.FILE, "error.bad_level", "%level%", a[3], "%spell%", d.name(), "%max%", String.valueOf(d.maxLevel()));
            return;
        }
        progressAnswer(magic, s, p, d);
    }

    /** The published spell when the player knows it; else an error and null. */
    private static SpellDef knownSpell(MagicSystem magic, CommandSender s, Player p, String id) {
        SpellDef d = magic.stores().spell(id);
        if (d == null) {
            Utils.error(s, MagicText.FILE, "error.unknown_spell", "%id%", id);
            return null;
        }
        MagicData data = magic.players().data(p);
        if (data == null || !data.knows(id)) {
            Utils.error(s, MagicText.FILE, "not_known", "%player%", p.getName(), "%spell%", d.name());
            return null;
        }
        return d;
    }

    private static double amount(String raw) {
        try {
            double x = Double.parseDouble(raw);
            return Double.isNaN(x) || Double.isInfinite(x) ? -1 : x;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void progressAnswer(MagicSystem magic, CommandSender s, Player p, SpellDef d) {
        magic.players().flush(p.getUniqueId());
        if (!MagicCommand.answer(s)) return;
        MagicData data = magic.players().data(p);
        MagicData.Progress pr = data == null ? MagicData.Progress.START : data.progress(d.id());
        Utils.info(s, MagicText.FILE, "progress", "%player%", p.getName(), "%spell%", d.name(),
                "%level%", String.valueOf(Math.min(pr.level(), d.maxLevel())), "%max%", String.valueOf(d.maxLevel()),
                "%xp%", MagicRules.manaText(pr.xp()), "%next%", MagicRules.manaText(d.xpToNext(pr.level())));
    }

    // ── /mana ──

    public static CommandExecutor mana(MagicSystem magic) {
        TabArg ops = TabArg.of("set", "give", "take");
        return new MagicCommand("error.usage.mana")
                .sub("set", Permissions.MANA_SET, List.of(TabArg.PLAYER, TabArg.NONE), (s, a) -> mana(magic, s, a, "set", false))
                .sub("give", Permissions.MANA_SET, List.of(TabArg.PLAYER, TabArg.NONE), (s, a) -> mana(magic, s, a, "give", false))
                .sub("take", Permissions.MANA_SET, List.of(TabArg.PLAYER, TabArg.NONE), (s, a) -> mana(magic, s, a, "take", false))
                .sub("max", Permissions.MANA_SET, List.of(ops, TabArg.PLAYER, TabArg.NONE), (s, a) -> {
                    if (a.length < 1) {
                        Utils.info(s, MagicText.FILE, "error.usage.mana");
                        return;
                    }
                    mana(magic, s, java.util.Arrays.copyOfRange(a, 1, a.length), a[0].toLowerCase(Locale.ROOT), true);
                })
                .withHelp("magic_mana");
    }

    private static void mana(MagicSystem magic, CommandSender s, String[] a, String op, boolean max) {
        if (a.length < 2) {
            Utils.info(s, MagicText.FILE, "error.usage.mana");
            return;
        }
        DoubleBinaryOperator apply;
        switch (op) {
            case "set" -> apply = (old, n) -> n;
            case "give" -> apply = Double::sum;
            case "take" -> apply = (old, n) -> old - n;
            default -> {
                Utils.info(s, MagicText.FILE, "error.usage.mana");
                return;
            }
        }
        Player p = MagicCommand.online(s, a[0]);
        if (p == null) return;
        double amount;
        try {
            amount = Double.parseDouble(a[1]);
        } catch (NumberFormatException e) {
            amount = -1;
        }
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            Utils.error(s, MagicText.FILE, "error.amount", "%amount%", a[1]);
            return;
        }
        MagicData d = magic.players().data(p);
        if (d == null) return;
        if (max) d.setBaseMax(apply.applyAsDouble(d.baseMax(), amount));
        else d.setMana(apply.applyAsDouble(d.mana(), amount));
        magic.players().flush(p.getUniqueId());
        if (MagicCommand.answer(s)) {
            Utils.info(s, MagicText.FILE, max ? "mana.max_set" : "mana.set", "%player%", p.getName(),
                    "%mana%", MagicRules.manaText(d.mana()), "%max%", MagicRules.manaText(d.max()));
        }
    }

    // ── /wand ──

    public static CommandExecutor wand(MagicSystem magic) {
        return new MagicCommand("error.usage.wand")
                .bare((p, a) -> {
                    MagicData d = magic.players().data(p);
                    if (d == null || d.wand() == null) {
                        Utils.error(p, MagicText.FILE, "wand.none");
                        return;
                    }
                    if (!magic.wands().holdsWand(p) && !hasWand(magic, p)) magic.wands().restore(p);
                    magic.wandMenu().open(p);
                })
                .sub("give", Permissions.WAND_GIVE, List.of(TabArg.PLAYER), (s, a) -> giveOrRestore(magic, s, a, true))
                .sub("restore", Permissions.WAND_GIVE, List.of(TabArg.PLAYER), (s, a) -> giveOrRestore(magic, s, a, false))
                .sub("colour", Permissions.WAND_MANAGE, List.of(TabArg.PLAYER, TabArg.of("random")), (s, a) -> {
                    if (a.length < 2) {
                        Utils.info(s, MagicText.FILE, "error.usage.wand");
                        return;
                    }
                    Player p = MagicCommand.online(s, a[0]);
                    if (p == null) return;
                    if (!magic.wands().setColor(p, a[1])) {
                        Utils.error(s, MagicText.FILE, "wand.bad_colour", "%colour%", a[1]);
                        return;
                    }
                    MagicData d = magic.players().data(p);
                    if (MagicCommand.answer(s) && d != null && d.wand() != null) {
                        Utils.info(s, MagicText.FILE, "wand.colour", "%player%", p.getName(), "%colour%", d.wand().color());
                    }
                })
                .sub("skin", Permissions.WAND_MANAGE, List.of(TabArg.PLAYER, TabArg.content("wandskins")), (s, a) -> {
                    if (a.length < 2) {
                        Utils.info(s, MagicText.FILE, "error.usage.wand");
                        return;
                    }
                    Player p = MagicCommand.online(s, a[0]);
                    if (p == null) return;
                    String skin = "default".equalsIgnoreCase(a[1]) ? "" : a[1];
                    WandSkin k = skin.isEmpty() ? null : magic.stores().skin(skin);
                    if (!skin.isEmpty() && k == null) {
                        Utils.error(s, MagicText.FILE, "wand.unknown_skin", "%skin%", a[1]);
                        return;
                    }
                    if (k != null && !magic.wands().ownsSkin(p, k)) {
                        Utils.error(s, MagicText.FILE, "wand.skin_locked", "%skin%", k.displayName(), "%player%", p.getName());
                        return;
                    }
                    if (!magic.wands().setSkin(p, skin)) {
                        Utils.error(s, MagicText.FILE, "wand.none_for", "%player%", p.getName());
                        return;
                    }
                    if (MagicCommand.answer(s)) Utils.info(s, MagicText.FILE, "wand.skin", "%player%", p.getName(), "%skin%", k == null ? "default" : k.displayName());
                })
                .withHelp("magic_wand");
    }

    private static boolean hasWand(MagicSystem magic, Player p) {
        for (var item : p.getInventory().getContents()) {
            if (magic.wands().isOwnWand(item, p)) return true;
        }
        return false;
    }

    private static void giveOrRestore(MagicSystem magic, CommandSender s, String[] a, boolean give) {
        if (a.length < 1) {
            Utils.info(s, MagicText.FILE, "error.usage.wand");
            return;
        }
        Player p = MagicCommand.online(s, a[0]);
        if (p == null) return;
        boolean ok = give ? magic.wands().give(p) : magic.wands().restore(p);
        if (!ok) {
            Utils.error(s, MagicText.FILE, give ? "wand.no_citem" : "wand.none_for", "%player%", p.getName());
            return;
        }
        if (MagicCommand.answer(s)) Utils.info(s, MagicText.FILE, give ? "wand.given" : "wand.restored", "%player%", p.getName());
    }

    // ── /spells ──

    public static CommandExecutor spells(MagicSystem magic) {
        return (sender, command, label, args) -> {
            if (sender instanceof Player p) magic.spellbook().open(p);
            else Utils.error(sender, MagicText.FILE, "error.players_only");
            return true;
        };
    }
}
