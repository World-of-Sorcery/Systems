package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.magic.cast.SpellLevelUpEvent;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.utils.ActionHandler;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Spell XP and level-ups of online players (magic spec §3.5): casts, hits and kills give
 * XP (CastService), staff change it by command. Each new level runs the spell's level-up
 * actions (or a short message and sound when it has none) and fires
 * {@link SpellLevelUpEvent}. Progress is saved with the rest of the magic data.
 */
public final class LevelService {

    private final WoSSystems plugin;
    private final MagicPlayers players;

    LevelService(WoSSystems plugin, MagicPlayers players) {
        this.plugin = plugin;
        this.players = players;
    }

    /** Gives XP in a spell the player knows; false when they don't (or are offline). */
    public boolean giveXp(Player p, SpellDef spell, double amount) {
        MagicData d = players.data(p);
        if (d == null || !d.knows(spell.id())) return false;
        if (amount <= 0) return true;
        MagicData.Progress now = d.progress(spell.id());
        SpellLevels.Gain g = SpellLevels.add(now.level(), now.xp(), amount, spell);
        d.setProgress(spell.id(), new MagicData.Progress(g.level(), g.xp()));
        for (int level : g.reached()) levelUp(p, spell, level);
        return true;
    }

    public boolean takeXp(Player p, SpellDef spell, double amount) {
        MagicData d = players.data(p);
        if (d == null || !d.knows(spell.id())) return false;
        MagicData.Progress now = d.progress(spell.id());
        SpellLevels.Gain g = SpellLevels.take(now.level(), now.xp(), amount);
        d.setProgress(spell.id(), new MagicData.Progress(g.level(), g.xp()));
        return true;
    }

    /** Sets the level (XP back to 0); false when not known or outside 1 .. max. A higher level runs its level-up. */
    public boolean setLevel(Player p, SpellDef spell, int level) {
        MagicData d = players.data(p);
        if (d == null || !d.knows(spell.id()) || !SpellLevels.validLevel(level, spell)) return false;
        int before = Math.min(d.level(spell.id()), spell.maxLevel());
        d.setProgress(spell.id(), new MagicData.Progress(level, 0));
        for (int l = before + 1; l <= level; l++) levelUp(p, spell, l);
        return true;
    }

    private void levelUp(Player p, SpellDef spell, int level) {
        List<String> actions = spell.levelUpActions();
        if (actions.isEmpty()) {
            me.hektortm.wosCore.Utils.info(p, MagicText.FILE, "level.up", "%spell%", spell.name(), "%level%", String.valueOf(level));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        } else {
            List<String> lines = new ArrayList<>(actions.size());
            for (String line : actions) lines.add(line.replace("{level}", String.valueOf(level)));
            plugin.getActionHandler().executeActions(p, lines, ActionHandler.SourceType.SPELL, spell.id(), null);
        }
        Bukkit.getPluginManager().callEvent(new SpellLevelUpEvent(p, spell, level));
    }
}
