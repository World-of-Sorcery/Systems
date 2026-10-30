package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.database.dao.CitemDAO;
import me.hektortm.woSSystems.systems.interactions.InteractionManager;
import me.hektortm.woSSystems.systems.interactions.InterListener;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spell locks (magic spec §9): the interaction a spell touched (a bound block, a placed
 * custom item's "placed" interaction, or a Citizens NPC's bound interaction) runs its spell
 * rows for the caster, knowing the spell ({@link InteractionManager#triggerBySpell}). Each
 * lock reacts once per cast. Only interactions with a spell row react at all.
 */
public final class SpellLocks {

    /** One place an interaction is bound to, with its key (local cooldowns, once per cast). */
    record Lock(String interactionId, InteractionKey key, Location where) { }

    private final WoSSystems plugin;
    private final DAOHub hub;

    public SpellLocks(WoSSystems plugin, DAOHub hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    /** The lock at that block (a bound block or a placed item); null when there is none. */
    @Nullable
    Lock at(Block b) {
        Location loc = b.getLocation();
        String id = hub.getInteractionDAO().getBound(loc);
        if (id == null) id = hub.getCitemDAO().placedActionAt(loc);
        return id == null ? null : new Lock(id, InterListener.buildKey(loc), loc);
    }

    /** The lock on that entity (an NPC with a bound interaction); null when there is none. */
    @Nullable
    Lock of(Entity e) {
        if (!e.hasMetadata("NPC")) return null;
        try {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(e);
            if (npc == null) return null;
            String id = hub.getInteractionDAO().getNpcBound(npc.getId());
            return id == null ? null : new Lock(id, new InteractionKey("npc:" + npc.getId()), e.getLocation());
        } catch (RuntimeException ex) {
            return null; // Citizens missing
        }
    }

    /** The bound blocks and placed items within {@code reach} blocks of {@code center} (any interaction). */
    List<Lock> near(Location center, double reach) {
        List<Lock> out = new ArrayList<>();
        if (center.getWorld() == null) return out;
        double r2 = (reach + 1) * (reach + 1);
        for (Location l : hub.getInteractionDAO().getAllBlockLocations()) {
            if (l == null || l.getWorld() != center.getWorld() || l.distanceSquared(center) > r2) continue;
            String id = hub.getInteractionDAO().getBound(l);
            if (id != null) out.add(new Lock(id, InterListener.buildKey(l), l));
        }
        for (CitemDAO.PlacedAction a : hub.getCitemDAO().placedActions()) {
            Location l = a.block();
            if (l.getWorld() != center.getWorld() || l.distanceSquared(center) > r2) continue;
            out.add(new Lock(a.interactionId(), InterListener.buildKey(l), l));
        }
        return out;
    }

    /**
     * Runs the lock's spell rows for the caster, unless it already reacted in this cast
     * ({@code done}: the keys of the locks that did).
     */
    void hit(@Nullable Lock lock, Player caster, SpellDef spell, Set<String> done) {
        if (lock == null || !done.add(lock.key().getKey())) return;
        InteractionManager interactions = plugin.getInteractionManager();
        if (interactions == null) return;
        interactions.triggerBySpell(lock.interactionId(), caster, lock.key(), spell.id(), spell.name(), spell.level());
    }
}
