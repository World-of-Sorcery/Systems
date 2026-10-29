package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicSystem;
import me.hektortm.woSSystems.systems.magic.MagicText;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spells charging (magic spec §3.2): ticks every charge, draws its particles at the wand
 * tip and the charge bar in the action bar, and fires it when it ends ({@link Charge}).
 * Cancels, at no cost and with a short fizzle: switching slot, the wand leaving the hand,
 * a world change or walking into a no-spell region (checked each tick), teleporting, death,
 * opening the ring ({@link #cancel}), and damage when the spell says so. Logging out
 * cancels silently. Slowing while charging is a transient speed modifier, never saved.
 */
public final class ChargeService implements Listener {

    private static final NamespacedKey SLOW = new NamespacedKey("wos", "spell_charge");
    private static final int BAR_CELLS = 12;

    private static final class Active {
        final Charge charge;
        final SpellDef spell;
        int ticks;

        Active(Charge charge, SpellDef spell) {
            this.charge = charge;
            this.spell = spell;
        }
    }

    private final WoSSystems plugin;
    private final MagicSystem magic;
    private final CastService cast;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    ChargeService(WoSSystems plugin, MagicSystem magic, CastService cast) {
        this.plugin = plugin;
        this.magic = magic;
        this.cast = cast;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean isCharging(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    /** Starts charging a tier of a spell (its checks at the click passed). */
    void begin(Player p, SpellDef spell, CastService.Mode mode) {
        long now = System.currentTimeMillis();
        long end = now + Math.round(spell.chargeTime() * 1000);
        Charge c = new Charge(spell.id(), mode, spell.level(), now, end, p.getInventory().getHeldItemSlot(), p.getWorld().getName());
        active.put(p.getUniqueId(), new Active(c, spell));
        slow(p, spell.chargeSlow());
        SpellFx.sound(p.getLocation(), spell.chargeSound(), 1f, 1f);
        bar(p, spell, 0);
    }

    /** Cancels the player's charge, if any; {@code fizzle}: with the short sound and text. */
    public void cancel(Player p, boolean fizzle) {
        Active a = active.remove(p.getUniqueId());
        if (a == null) return;
        slow(p, 0);
        if (fizzle && p.isOnline()) {
            p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.8f);
            MagicText.actionBar(p, "cast.fizzle", "%spell%", a.spell.name());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Active>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Active> e = it.next();
            Player p = plugin.getServer().getPlayer(e.getKey());
            Active a = e.getValue();
            if (p == null) {
                it.remove();
                continue;
            }
            Charge.View view = new Charge.View(p.isOnline(), magic.wands().holdsWand(p), p.getInventory().getHeldItemSlot(),
                    p.getWorld().getName(), MagicFlags.castAllowed(p));
            switch (a.charge.tick(now, view)) {
                case WAIT -> draw(p, a, now);
                case FIRE -> {
                    it.remove();
                    slow(p, 0);
                    CastService.Result r = cast.fire(p, a.spell, a.charge.mode());
                    cast.explain(p, a.spell, r);
                }
                case OFFLINE -> {
                    it.remove();
                    slow(p, 0);
                }
                default -> cancel(p, true);
            }
        }
    }

    private void draw(Player p, Active a, long now) {
        a.ticks++;
        SpellDef d = a.spell;
        if (d.chargeSoundLoop() && a.ticks % 20 == 0) SpellFx.sound(p.getLocation(), d.chargeSound(), 1f, 1f);
        if (a.ticks % 2 != 0) return;
        if (!d.chargeParticle().isBlank()) {
            MagicData data = magic.players().data(p);
            String color = "wand".equals(d.chargeColor()) || d.chargeColor().isBlank()
                    ? (data == null ? "#FFFFFF" : CastService.wandColor(data)) : d.chargeColor();
            var eye = p.getEyeLocation();
            SpellFx.particle(eye.clone().add(eye.getDirection().normalize().multiply(0.6)), d.chargeParticle(), color, 2, 0.06);
        }
        bar(p, d, a.charge.done(now));
    }

    private static void bar(Player p, SpellDef d, double done) {
        MagicText.actionBar(p, "bar.charge", "%spell%", d.name(), "%bar%", Charge.bar(done, BAR_CELLS));
    }

    /** Slows the player by {@code level} (0: back to normal). */
    private static void slow(Player p, int level) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SLOW);
        if (level > 0) speed.addTransientModifier(new AttributeModifier(SLOW, -0.15 * level, AttributeModifier.Operation.ADD_SCALAR));
    }

    // ── cancels by event ──

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Active a = active.get(p.getUniqueId());
        if (a != null && a.spell.chargeInterrupt() && e.getFinalDamage() > 0) cancel(p, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        cancel(e.getPlayer(), true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        cancel(e.getEntity(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancel(e.getPlayer(), false);
    }
}
