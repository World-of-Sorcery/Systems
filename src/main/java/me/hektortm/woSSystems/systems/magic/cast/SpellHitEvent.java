package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * A spell's shape hit something: an entity (also an NPC, which spells never harm — spell
 * locks react to it), or a block. Spell locks are run by the cast itself ({@link SpellLocks});
 * quest events may listen (part 5).
 */
public final class SpellHitEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player caster;
    private final SpellDef spell;
    @Nullable
    private final Entity entity;
    @Nullable
    private final Block block;
    private final Location at;

    public SpellHitEvent(Player caster, SpellDef spell, @Nullable Entity entity, @Nullable Block block, Location at) {
        this.caster = caster;
        this.spell = spell;
        this.entity = entity;
        this.block = block;
        this.at = at;
    }

    public Player caster() { return caster; }

    public SpellDef spell() { return spell; }

    @Nullable
    public Entity entity() { return entity; }

    @Nullable
    public Block block() { return block; }

    public Location at() { return at.clone(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
