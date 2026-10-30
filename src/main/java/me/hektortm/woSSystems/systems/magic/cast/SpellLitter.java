package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.dao.CitemDAO;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Items and falling blocks a spell drops (more-nodes spec §3.3): tagged {@code wos:spell_litter}
 * on the entity and the stack, never saved with the chunk, never merged, never picked up into
 * any inventory, removed when their time ends. A tagged one found when a chunk loads (left by
 * a crash) is removed. A falling block never places a block and never drops an item.
 */
public final class SpellLitter implements Listener {

    static final NamespacedKey KEY = new NamespacedKey("wos", "spell_litter");

    /** How a "vanish" drop disappears when a player touches it. */
    private record Vanish(String particle, String sound) { }

    /** Falling blocks that shatter on landing (the others vanish quietly). */
    private final Set<UUID> shatter = new HashSet<>();
    private final Map<UUID, Vanish> vanish = new HashMap<>();
    private final Set<String> unknownItems = new HashSet<>();
    private final WoSSystems plugin;
    private final CitemDAO citems;

    public SpellLitter(WoSSystems plugin, CitemDAO citems) {
        this.plugin = plugin;
        this.citems = citems;
    }

    static boolean tagged(Entity e) {
        return e.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** The stack for an item field: a vanilla id with a colon, else a custom item; null (logged once) when unknown. */
    @Nullable
    ItemStack stack(String item) {
        ItemStack s = null;
        if (item.contains(":")) {
            Material m = Material.matchMaterial(item);
            if (m != null && m.isItem() && !m.isAir()) s = new ItemStack(m);
        } else {
            s = citems.getCitem(item);
        }
        if (s == null && unknownItems.add(item)) plugin.getLogger().warning("[Magic] unknown item '" + item + "'");
        return s;
    }

    private static Vector scatter(double spread, double lift) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Vector(r.nextDouble(-spread, spread), lift * r.nextDouble(0.6, 1.0), r.nextDouble(-spread, spread));
    }

    void dropItems(Location at, String item, int amount, double spread, double lift, int seconds, boolean vanishes,
                   String particle, String sound, boolean glow) {
        World w = at.getWorld();
        ItemStack base = w == null ? null : stack(item);
        if (base == null) return;
        for (int i = 0; i < amount; i++) {
            ItemStack one = base.clone();
            one.setAmount(1);
            ItemMeta meta = one.getItemMeta();
            if (meta != null) { // a random id per stack, so two drops never stack
                meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
                one.setItemMeta(meta);
            }
            Item e = w.dropItem(at, one, it -> {
                it.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
                it.setPersistent(false);
                it.setCanMobPickup(false);
                it.setUnlimitedLifetime(false);
                it.setGlowing(glow);
                it.setVelocity(scatter(spread, lift));
            });
            if (vanishes) vanish.put(e.getUniqueId(), new Vanish(particle, sound));
            removeLater(e, seconds);
        }
    }

    void fallingBlocks(Location at, BlockData data, int amount, double spread, double lift, boolean shatters, int seconds) {
        World w = at.getWorld();
        if (w == null) return;
        for (int i = 0; i < amount; i++) {
            FallingBlock fb = w.spawn(at, FallingBlock.class, f -> {
                f.setBlockData(data);
                f.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
                f.setPersistent(false);
                f.setDropItem(false);
                f.setCancelDrop(true);
                f.setHurtEntities(false);
                f.setVelocity(scatter(spread, lift));
            });
            if (shatters) shatter.add(fb.getUniqueId());
            removeLater(fb, seconds);
        }
    }

    private void removeLater(Entity e, int seconds) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> forget(e), Math.max(1, seconds) * 20L);
    }

    private void forget(Entity e) {
        vanish.remove(e.getUniqueId());
        shatter.remove(e.getUniqueId());
        e.remove();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickup(PlayerAttemptPickupItemEvent e) {
        Item item = e.getItem();
        if (!tagged(item)) return;
        e.setCancelled(true);
        Vanish v = vanish.get(item.getUniqueId());
        if (v == null) return; // pickup: none
        Location at = item.getLocation().add(0, 0.2, 0);
        SpellFx.particle(at, v.particle(), "#FFFFFF", 4, 0.1);
        SpellFx.sound(at, v.sound(), 0.6f, 1.2f);
        forget(item);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickup(EntityPickupItemEvent e) {
        if (tagged(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (!tagged(e.getItem())) return;
        e.setCancelled(true);
        forget(e.getItem()); // a hopper would try every tick
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMerge(ItemMergeEvent e) {
        if (tagged(e.getEntity()) || tagged(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLand(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock fb) || !tagged(fb)) return;
        e.setCancelled(true);
        if (shatter.contains(fb.getUniqueId())) {
            BlockData data = fb.getBlockData();
            Location at = fb.getLocation();
            if (at.getWorld() != null) {
                at.getWorld().spawnParticle(Particle.BLOCK, at, 20, 0.3, 0.3, 0.3, data);
                Sound s = data.getSoundGroup().getBreakSound();
                at.getWorld().playSound(at, s, 0.8f, 1f);
            }
        }
        forget(fb);
    }

    /** Litter left by a crash: not persistent, but removed here in case a chunk saved it anyway. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (tagged(entity)) entity.remove();
        }
    }
}
