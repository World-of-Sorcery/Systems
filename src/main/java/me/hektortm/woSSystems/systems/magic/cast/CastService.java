package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.magic.LevelService;
import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicRules;
import me.hektortm.woSSystems.systems.magic.MagicSystem;
import me.hektortm.woSSystems.systems.magic.MagicText;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Casting (magic spec §3.2, §3.4, §3.5, §4, §5.1): the checks, the costs, then a {@link SpellRun}.
 * <ul>
 *   <li>{@link Mode#PLAYER} — left-click with the wand: the spell must be known; cooldown,
 *       mana and cast conditions apply.</li>
 *   <li>{@link Mode#CONTENT} — {@code spell cast <player> <id>} from content: no wand or
 *       knowing needed; cooldown, mana and conditions apply.</li>
 *   <li>{@link Mode#FREE} — {@code … free}: no cooldown, mana, conditions or charge.</li>
 *   <li>{@link Mode#TEST} — {@code /spell test}: a draft, free, also where casting is forbidden.</li>
 * </ul>
 * Everywhere: magic must be on on this server (off on the hub unless the settings say so).
 * The caster's level of the spell picks its tier. A spell with a charge time charges first
 * ({@link ChargeService}): cooldown, region and conditions are checked at the click; region,
 * conditions and mana again when it fires, and only then is it paid for. A caster charges
 * one spell at a time; clicking again meanwhile does nothing. Casts from the wand and from
 * content give the caster XP in a spell they know.
 */
public final class CastService {

    public enum Mode { PLAYER, CONTENT, FREE, TEST }

    public enum Result { CAST, CHARGING, BUSY, UNKNOWN, DISABLED, FORBIDDEN_HERE, NO_DATA, NO_WAND, NOT_KNOWN, COOLDOWN, MANA, CONDITIONS }

    private final WoSSystems plugin;
    private final MagicSystem magic;
    private final SpellDeaths deaths;
    private final LevelService levels;
    private final FallGuard falls;
    private final ChargeService charges;
    private volatile PvpGate pvp = PvpGate.DENY;

    public CastService(WoSSystems plugin, MagicSystem magic, SpellDeaths deaths, LevelService levels, FallGuard falls) {
        this.plugin = plugin;
        this.magic = magic;
        this.deaths = deaths;
        this.levels = levels;
        this.falls = falls;
        this.charges = new ChargeService(plugin, magic, this);
    }

    /** Part 5 (the PvP plugin) replaces the gate. */
    public void usePvpGate(PvpGate gate) {
        this.pvp = gate;
    }

    public ChargeService charges() { return charges; }

    public Result cast(Player p, String spellId, Mode mode) {
        SpellDef d = magic.stores().spell(spellId);
        return d == null ? Result.UNKNOWN : cast(p, d, mode);
    }

    public Result cast(Player p, SpellDef spell, Mode mode) {
        return cast(p, spell, mode, 0);
    }

    /**
     * Casts {@code spell} (any tier of it); {@code testLevel} is the level a {@link Mode#TEST}
     * cast uses (0: the player's own). {@link Result#CHARGING}: it fires later by itself.
     */
    public Result cast(Player p, SpellDef spell, Mode mode, int testLevel) {
        if (!magic.enabledHere()) return Result.DISABLED;
        if (mode != Mode.TEST && !MagicFlags.castAllowed(p)) return Result.FORBIDDEN_HERE;
        MagicData data = magic.players().data(p);
        if (data == null) return Result.NO_DATA;
        if (mode == Mode.PLAYER) {
            if (!magic.wands().holdsWand(p)) return Result.NO_WAND;
            if (!data.knows(spell.id())) return Result.NOT_KNOWN;
        }
        if (charges.isCharging(p)) return Result.BUSY;
        SpellDef d = spell.at(mode == Mode.TEST && testLevel > 0 ? testLevel : data.level(spell.id()));
        if (costs(mode)) {
            if (data.cooldownLeft(d.id(), System.currentTimeMillis()) > 0) return Result.COOLDOWN;
            if (data.mana() < d.manaCost()) return Result.MANA;
            if (!conditionsPass(p, d)) return Result.CONDITIONS;
        }
        if (mode != Mode.FREE && d.chargeTime() > 0) {
            charges.begin(p, d, mode);
            return Result.CHARGING;
        }
        return fire(p, d, mode);
    }

    private static boolean costs(Mode mode) {
        return mode == Mode.PLAYER || mode == Mode.CONTENT;
    }

    /** Fires a tier now: region, conditions and mana again (they may have changed while it charged), then pays and runs it. */
    Result fire(Player p, SpellDef d, Mode mode) {
        if (!magic.enabledHere()) return Result.DISABLED;
        if (mode != Mode.TEST && !MagicFlags.castAllowed(p)) return Result.FORBIDDEN_HERE;
        MagicData data = magic.players().data(p);
        if (data == null) return Result.NO_DATA;
        if (costs(mode)) {
            if (data.mana() < d.manaCost()) return Result.MANA;
            if (!conditionsPass(p, d)) return Result.CONDITIONS;
            long now = System.currentTimeMillis();
            data.setMana(data.mana() - d.manaCost());
            data.startCooldown(d.id(), now + MagicRules.cooldownMillis(d.cooldown(), data.attributes().cooldownPct()));
        }
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location tip = eye.clone().add(dir.clone().multiply(0.6));
        String wandColor = wandColor(data);
        SpellFx.sound(p.getLocation(), d.castSound(), 1f, 1f);
        if (!d.castParticle().isBlank()) {
            SpellFx.particle(tip, d.castParticle(), "wand".equals(d.castColor()) ? wandColor : d.castColor(), 6, 0.08);
        }
        Bukkit.getPluginManager().callEvent(new SpellCastEvent(p, d, tip));
        boolean gainsXp = costs(mode) && data.knows(d.id());
        SpellDef base = d.at(1); // XP and levels follow the whole spell, not this tier
        SpellRun.XpSink xp = !gainsXp ? SpellRun.XpSink.NONE : new SpellRun.XpSink() {
            public void hit() { levels.giveXp(p, base, d.xpHit()); }

            public void kill() { levels.giveXp(p, base, d.xpKill()); }
        };
        new SpellRun(plugin, p, d, wandColor, data.attributes().damagePct(), pvp, deaths, xp, falls).run(d.roots(), SpellRun.Ctx.of(tip, dir, null, p, 0));
        if (gainsXp) levels.giveXp(p, base, d.xpCast());
        return Result.CAST;
    }

    static String wandColor(MagicData data) {
        return data.wand() == null ? "#FFFFFF" : data.wand().color();
    }

    private boolean conditionsPass(Player p, SpellDef d) {
        List<Condition> conds = d.conditions();
        if (conds.isEmpty()) return true;
        if (d.matchOne()) return conds.stream().anyMatch(c -> plugin.getConditionHandler().evaluate(p, c, null));
        return plugin.getConditionHandler().checkConditions(p, conds, null);
    }

    /** Tells the player why a cast didn't happen (nothing for CAST, CHARGING and BUSY). */
    public void explain(Player p, SpellDef spell, Result r) {
        long left = 0;
        MagicData data = magic.players().data(p);
        SpellDef d = data == null ? spell : spell.at(data.level(spell.id()));
        if (data != null) left = data.cooldownLeft(d.id(), System.currentTimeMillis());
        switch (r) {
            case CAST, CHARGING, BUSY -> { }
            case CONDITIONS -> {
                if (!d.failMessage().isBlank()) p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', d.failMessage()));
                else Utils.error(p, MagicText.FILE, "cast.conditions", "%spell%", d.name());
            }
            case COOLDOWN -> MagicText.actionBar(p, "cast.cooldown", "%spell%", d.name(), "%seconds%", MagicRules.seconds(left));
            case MANA -> {
                MagicText.actionBar(p, "cast.mana", "%spell%", d.name(), "%cost%", MagicRules.manaText(d.manaCost()));
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.6f);
            }
            default -> Utils.error(p, MagicText.FILE, "cast." + r.name().toLowerCase(java.util.Locale.ROOT), "%spell%", d.name());
        }
    }
}
