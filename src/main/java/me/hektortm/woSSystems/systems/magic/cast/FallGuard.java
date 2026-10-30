package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "No fall damage" of a push (magic spec §3.3): the entity's next fall damage is cancelled,
 * for a number of seconds or until it lands (on the ground again after half a second).
 * Kept only in memory: death, logout and a restart end it.
 */
public final class FallGuard implements Listener {

    private static final long LAND_AFTER_MS = 500;

    private record Guard(long from, long until) { }

    private final Plugin plugin;
    private final Map<UUID, Guard> guards = new ConcurrentHashMap<>();

    public FallGuard(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 5L, 5L);
    }

    public void protect(LivingEntity e, double seconds) {
        long now = System.currentTimeMillis();
        guards.put(e.getUniqueId(), new Guard(now, now + Math.round(seconds * 1000)));
    }

    private void check() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Guard>> it = guards.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Guard> g = it.next();
            Entity e = plugin.getServer().getEntity(g.getKey());
            if (e == null || !e.isValid() || now > g.getValue().until()
                    || (now - g.getValue().from() > LAND_AFTER_MS && e.isOnGround())) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (guards.remove(e.getEntity().getUniqueId()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        guards.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        guards.remove(e.getPlayer().getUniqueId());
    }
}
