package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A spell was cast (after its checks; quest events and other plugins listen). */
public final class SpellCastEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player caster;
    private final SpellDef spell;
    private final Location where;

    public SpellCastEvent(Player caster, SpellDef spell, Location where) {
        this.caster = caster;
        this.spell = spell;
        this.where = where;
    }

    public Player caster() { return caster; }

    public SpellDef spell() { return spell; }

    public Location where() { return where.clone(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
