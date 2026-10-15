package me.hektortm.woSSystems.systems.quests;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.database.dao.CitemDAO;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Where things are in the world for the quest engine: the block or NPC an
 * interaction key names, and the nearest place an interaction is bound to
 * (a block, an NPC, a placed custom item).
 */
public final class QuestPlaces {

    /** How far a step's target is looked for around the player. */
    private static final double RANGE = 48;

    private final DAOHub hub;

    public QuestPlaces(DAOHub hub) {
        this.hub = hub;
    }

    /** The location of an interaction key: "block:world:x:y:z" or "npc:7"; null when it can't be found. */
    @Nullable
    public static Location ofKey(@Nullable String key) {
        if (key == null) return null;
        if (key.startsWith("npc:")) return npcLocation(key.substring(4));
        if (!key.startsWith("block:")) return null;
        String[] p = key.substring(6).split(":");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static Location npcLocation(String id) {
        try {
            NPC npc = CitizensAPI.getNPCRegistry().getById(Integer.parseInt(id));
            return npc == null || !npc.isSpawned() || npc.getEntity() == null ? null : npc.getEntity().getLocation();
        } catch (RuntimeException e) {
            return null; // Citizens missing or a bad id
        }
    }

    /** Every place the interaction is bound to (blocks, NPCs, placed items). */
    public List<Location> placesOf(String interactionId) {
        List<Location> out = new ArrayList<>(hub.getInteractionDAO().blocksOf(interactionId));
        for (int npc : hub.getInteractionDAO().npcsOf(interactionId)) {
            Location l = npcLocation(String.valueOf(npc));
            if (l != null) out.add(l);
        }
        for (CitemDAO.PlacedAction a : hub.getCitemDAO().placedActions()) {
            if (interactionId.equals(a.interactionId())) out.add(a.block());
        }
        return out;
    }

    /** The place of the interaction nearest the player in their world, within range; null when none. */
    @Nullable
    public Location nearest(Player p, String interactionId) {
        Location best = null;
        double bestDist = RANGE * RANGE;
        for (Location l : placesOf(interactionId)) {
            if (l.getWorld() == null || !l.getWorld().equals(p.getWorld())) continue;
            double d = l.distanceSquared(p.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = l;
            }
        }
        return best;
    }
}
