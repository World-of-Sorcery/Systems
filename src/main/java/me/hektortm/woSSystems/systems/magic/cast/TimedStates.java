package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Effects that last a while and must end cleanly (more-nodes spec §4.5): freeze, shield,
 * vanish and fly. Each ends by its own timer; what could outlive a logout or restart is
 * put right when the entity is seen again.
 */
public final class TimedStates implements Listener {

    /** When (epoch ms) a spell's freeze of the entity ends; kept with the entity, so a restart or unload can't leave it locked. */
    static final NamespacedKey FROZEN = new NamespacedKey("wos", "spell_frozen");
    /** On a player flying by a spell: the fly speed to restore. */
    static final NamespacedKey FLYING = new NamespacedKey("wos", "spell_fly");
    /** With {@link #FLYING}: the flight is the spell's and goes when it ends. */
    static final NamespacedKey FLIGHT_GIVEN = new NamespacedKey("wos", "spell_fly_given");

    private record Vanish(BukkitTask end, boolean endsOnCast, boolean endsOnDamage) { }

    private final Plugin plugin;
    private final FallGuard falls;
    private final Map<UUID, Vanish> vanished = new HashMap<>();
    private final Map<UUID, BukkitTask> flying = new HashMap<>();

    public TimedStates(Plugin plugin, FallGuard falls) {
        this.plugin = plugin;
        this.falls = falls;
    }

    // ── freeze ──

    /**
     * Freeze: fully frozen like powder snow (overlay, slowness, freeze damage by the game's
     * rules), locked until the longest running freeze of the entity ends; then it thaws as usual.
     */
    public void freeze(LivingEntity e, double seconds) {
        int ticks = (int) Math.round(seconds * 20);
        if (ticks <= 0) return;
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        Long had = pdc.get(FROZEN, PersistentDataType.LONG);
        long end = Math.max(System.currentTimeMillis() + ticks * 50L, had == null ? 0 : had);
        pdc.set(FROZEN, PersistentDataType.LONG, end);
        e.setFreezeTicks(e.getMaxFreezeTicks());
        e.lockFreezeTicks(true);
        thawLater(e, ticks);
    }

    private void thawLater(Entity e, long ticks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> thaw(e), Math.max(1, ticks));
    }

    /** Unlocks a spell's freeze once none of the entity's freezes still runs. */
    private static void thaw(Entity e) {
        if (!e.isValid()) return; // unloaded or logged out: put right when seen again
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        Long end = pdc.get(FROZEN, PersistentDataType.LONG);
        if (end == null || System.currentTimeMillis() < end - 50) return;
        pdc.remove(FROZEN);
        e.lockFreezeTicks(false);
    }

    // ── shield ──

    /**
     * Shield: {@code amount} absorption, through a transient raise of the entity's max
     * absorption (absorption above it can't be held). When it ends the raise goes and the
     * absorption is cut to the new max, so only what is left of this shield is lost.
     */
    public void shield(LivingEntity e, double amount, double seconds) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_ABSORPTION);
        if (max == null || amount <= 0) return;
        AttributeModifier raise = new AttributeModifier(new NamespacedKey("wos", "spell_shield_" + UUID.randomUUID()), amount,
                AttributeModifier.Operation.ADD_NUMBER);
        max.addTransientModifier(raise);
        e.setAbsorptionAmount(Math.min(max.getValue(), e.getAbsorptionAmount() + amount));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            AttributeInstance now = e.getAttribute(Attribute.MAX_ABSORPTION);
            if (now == null) return;
            now.removeModifier(raise);
            if (e.isValid()) e.setAbsorptionAmount(Math.min(e.getAbsorptionAmount(), now.getValue()));
        }, Math.max(1, Math.round(seconds * 20)));
    }

    // ── vanish ──

    /**
     * Vanish: hidden from every other player (joiners too) for {@code seconds}; mobs still
     * see them. A new vanish replaces a running one. Ends on time, logout, death and (when
     * set) the caster's next cast or damage.
     */
    public void vanish(Player p, double seconds, boolean endsOnCast, boolean endsOnDamage) {
        Vanish old = vanished.remove(p.getUniqueId());
        if (old != null) old.end().cancel();
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other != p) other.hidePlayer(plugin, p);
        }
        BukkitTask end = plugin.getServer().getScheduler().runTaskLater(plugin, () -> unvanish(p), Math.max(1, Math.round(seconds * 20)));
        vanished.put(p.getUniqueId(), new Vanish(end, endsOnCast, endsOnDamage));
    }

    private void unvanish(Player p) {
        Vanish v = vanished.remove(p.getUniqueId());
        if (v == null) return;
        v.end().cancel();
        for (Player other : plugin.getServer().getOnlinePlayers()) other.showPlayer(plugin, p);
    }

    // ── fly ──

    /**
     * Whether a spell's flight may be taken away again when it ends: only from a survival /
     * adventure player who couldn't fly before.
     */
    static boolean mayTakeFlight(boolean hadAllowFlight, GameMode mode) {
        return !hadAllowFlight && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    /**
     * Fly: flight at {@code speed} for {@code seconds}. At the end the old fly speed comes
     * back; flight is taken away only when the player didn't have it (then no fall damage
     * until they land). A new fly while one runs only resets its time.
     */
    public void fly(Player p, double seconds, double speed) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (!pdc.has(FLYING)) {
            pdc.set(FLYING, PersistentDataType.FLOAT, p.getFlySpeed());
            if (mayTakeFlight(p.getAllowFlight(), p.getGameMode())) pdc.set(FLIGHT_GIVEN, PersistentDataType.BYTE, (byte) 1);
        }
        BukkitTask old = flying.remove(p.getUniqueId());
        if (old != null) old.cancel();
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setFlySpeed((float) Math.max(0.05, Math.min(0.3, speed)));
        flying.put(p.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> land(p, true), Math.max(1, Math.round(seconds * 20))));
    }

    /** Ends a spell's flight: speed back, and flight away when it was the spell's (with a fall guard when {@code guard}). */
    private void land(Player p, boolean guard) {
        BukkitTask t = flying.remove(p.getUniqueId());
        if (t != null) t.cancel();
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Float speed = pdc.get(FLYING, PersistentDataType.FLOAT);
        if (speed == null) return;
        boolean given = pdc.has(FLIGHT_GIVEN);
        pdc.remove(FLYING);
        pdc.remove(FLIGHT_GIVEN);
        p.setFlySpeed(speed);
        if (given && mayTakeFlight(false, p.getGameMode())) { // switched to creative meanwhile: keep it
            boolean wasFlying = p.isFlying();
            p.setFlying(false);
            p.setAllowFlight(false);
            if (wasFlying && guard) falls.protect(p, 30);
        }
    }

    // ── events ──

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCast(SpellCastEvent e) {
        Vanish v = vanished.get(e.caster().getUniqueId());
        if (v != null && v.endsOnCast()) unvanish(e.caster());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Vanish v = vanished.get(p.getUniqueId());
        if (v != null && v.endsOnDamage()) unvanish(p);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        unvanish(e.getEntity());
        land(e.getEntity(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        unvanish(e.getPlayer());
        land(e.getPlayer(), false); // abilities are saved with the player: never log out with a spell's flight
    }

    /** An entity seen again after a logout, unload or restart: thawed now if its freeze is over, else when it is. */
    private void seen(Entity e) {
        Long end = e.getPersistentDataContainer().get(FROZEN, PersistentDataType.LONG);
        if (end != null) thawLater(e, (end - System.currentTimeMillis()) / 50);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        seen(p);
        land(p, true); // a flight a crash left behind
        for (UUID id : vanished.keySet()) {
            Player hidden = plugin.getServer().getPlayer(id);
            if (hidden != null && hidden != p) p.hidePlayer(plugin, hidden);
        }
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) seen(entity);
    }
}
