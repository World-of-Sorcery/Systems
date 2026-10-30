package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A player reached a new level of a spell (one event per level; quest events listen, part 5). */
public final class SpellLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SpellDef spell;
    private final int level;

    public SpellLevelUpEvent(Player player, SpellDef spell, int level) {
        this.player = player;
        this.spell = spell;
        this.level = level;
    }

    public Player player() { return player; }

    public SpellDef spell() { return spell; }

    public int level() { return level; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
