package me.hektortm.woSSystems.systems.quests;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.interactions.DisplaySettings;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a player sees of their quests and sequences: a hologram over the next
 * thing to click, a pointer, the displays a sequence left standing, click
 * effects, waypoints. Only that player sees them: the display entities are
 * hidden by default and shown to them alone, and never saved with the world.
 */
public final class QuestVisuals {

    private final Plugin plugin;
    private final DAOHub hub;
    /** Player → visual key ("<run>:<node>:holo") → entity. */
    private final Map<UUID, Map<String, Entity>> shown = new HashMap<>();

    public QuestVisuals(Plugin plugin, DAOHub hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    private static String colour(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private <T extends Entity> T spawn(Player p, Location at, Class<T> type, java.util.function.Consumer<T> setup) {
        T e = at.getWorld().spawn(at, type, x -> {
            x.setVisibleByDefault(false);
            x.setPersistent(false);
            setup.accept(x);
        });
        p.showEntity(plugin, e);
        return e;
    }

    /** A hologram (created, or its text changed). */
    public void hologram(Player p, String key, Location at, String text) {
        Map<String, Entity> mine = shown.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Entity old = mine.get(key);
        var component = LegacyComponentSerializer.legacySection().deserialize(colour(text));
        if (old instanceof TextDisplay td && old.isValid() && old.getWorld().equals(at.getWorld()) && old.getLocation().distanceSquared(at) < 0.01) {
            td.text(component);
            return;
        }
        if (old != null) old.remove();
        mine.put(key, spawn(p, at, TextDisplay.class, td -> {
            td.text(component);
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
        }));
    }

    /** A display a sequence step left standing at the anchor (block, item or custom item). */
    public void display(Player p, String key, Location anchor, JsonObject settings) {
        Map<String, Entity> mine = shown.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Entity old = mine.get(key);
        if (old != null && old.isValid()) return;
        DisplaySettings s = DisplaySettings.parse(settings.toString());
        Location at = anchor.clone().add(0.5 + s.offset().x(), s.offset().y(), 0.5 + s.offset().z());
        at.setYaw(s.yaw());
        Transformation t = new Transformation(
                new Vector3f((float) s.translation().x(), (float) s.translation().y(), (float) s.translation().z()),
                new AxisAngle4f(), new Vector3f((float) s.scale().x(), (float) s.scale().y(), (float) s.scale().z()), new AxisAngle4f());
        Entity e;
        try {
            if (s.kind() == DisplaySettings.Kind.BLOCK) {
                e = spawn(p, at, BlockDisplay.class, d -> {
                    d.setBlock(Bukkit.createBlockData(s.block()));
                    d.setTransformation(t);
                });
            } else {
                ItemStack item = itemOf(s);
                e = spawn(p, at, ItemDisplay.class, d -> {
                    d.setItemStack(item);
                    d.setTransformation(t);
                });
            }
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[Quests] display '" + key + "' skipped: " + ex.getMessage());
            return;
        }
        mine.put(key, e);
    }

    private ItemStack itemOf(DisplaySettings s) {
        if (s.kind() == DisplaySettings.Kind.CITEM) {
            ItemStack c = hub.getCitemDAO().getCitem(s.citem());
            if (c != null) return c;
        }
        Material m = Material.matchMaterial(s.item());
        return new ItemStack(m == null ? Material.BARRIER : m);
    }

    /** Removes the player's visuals whose keys are not in {@code keep}. */
    public void retain(Player p, Set<String> keep) {
        Map<String, Entity> mine = shown.get(p.getUniqueId());
        if (mine == null) return;
        mine.entrySet().removeIf(e -> {
            if (keep.contains(e.getKey()) && e.getValue().isValid()) return false;
            e.getValue().remove();
            return true;
        });
    }

    public void clearAll(Player p) {
        Map<String, Entity> mine = shown.remove(p.getUniqueId());
        if (mine != null) mine.values().forEach(Entity::remove);
    }

    public void shutdown() {
        shown.values().forEach(m -> m.values().forEach(Entity::remove));
        shown.clear();
    }

    /** A short column of particles over the next target. */
    public void pointer(Player p, Location target) {
        Location base = target.clone().add(0.5, 1.2, 0.5);
        for (int i = 0; i < 4; i++) p.spawnParticle(Particle.END_ROD, base.clone().add(0, i * 0.25, 0), 1, 0, 0, 0, 0);
    }

    /** The sound and particles of a click: right or wrong; the sequence's theme, else defaults. */
    public void effect(Player p, Location at, boolean right, String sound, String particle) {
        if ("none".equals(particle)) return; // the step switched its effect off
        Location c = at.clone().add(0.5, 1.0, 0.5);
        Sound s = sound(sound, right ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.ENTITY_VILLAGER_NO);
        p.playSound(c, s, 1f, right ? 1.4f : 1f);
        Particle part = particle(particle, right ? Particle.HAPPY_VILLAGER : Particle.SMOKE);
        p.spawnParticle(part, c, right ? 8 : 4, 0.3, 0.2, 0.3, 0);
    }

    /** A dotted line from the player towards a waypoint (a few blocks of it). */
    public void waypoint(Player p, Location to) {
        if (to.getWorld() == null || !to.getWorld().equals(p.getWorld())) return;
        Location from = p.getEyeLocation();
        Vector dir = to.clone().add(0.5, 0.5, 0.5).toVector().subtract(from.toVector());
        double length = dir.length();
        if (length < 2) return;
        dir.normalize();
        for (double d = 1.5; d < Math.min(length, 9); d += 1.0) {
            p.spawnParticle(Particle.WAX_ON, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
    }

    private static Sound sound(String key, Sound fallback) {
        if (key == null || key.isBlank()) return fallback;
        try {
            Sound s = org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.fromString(key.trim().toLowerCase()));
            return s == null ? fallback : s;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static Particle particle(String key, Particle fallback) {
        if (key == null || key.isBlank()) return fallback;
        String name = key.trim().toUpperCase().replace("MINECRAFT:", "");
        try {
            Particle p = Particle.valueOf(name);
            // dust, block, item …: spawning them without their colour / block / item throws
            return p.getDataType() == Void.class ? p : fallback;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
