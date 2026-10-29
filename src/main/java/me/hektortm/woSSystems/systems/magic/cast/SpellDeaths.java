package me.hektortm.woSSystems.systems.magic.cast;

import me.hektortm.woSSystems.systems.magic.MagicText;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death messages naming the spell (magic spec §3.4, §8): "X was struck by Ember Bolt from
 * Y". Kill credit itself comes from Paper (the caster is the damage's causing entity).
 */
public final class SpellDeaths implements Listener {

    private record Hit(String caster, boolean self, String spell, long at) { }

    private final Map<UUID, Hit> last = new ConcurrentHashMap<>();

    /** Remembers a spell hit just before it is dealt. */
    void remember(LivingEntity victim, Player caster, String spellName) {
        if (victim instanceof Player) {
            last.put(victim.getUniqueId(), new Hit(caster.getName(), victim == caster, spellName, System.currentTimeMillis()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Hit hit = last.remove(victim.getUniqueId());
        EntityDamageEvent cause = victim.getLastDamageCause();
        if (hit == null || cause == null || System.currentTimeMillis() - hit.at() > 2000
                || cause.getDamageSource().getDamageType() != DamageType.MAGIC) return;
        e.deathMessage(MagicText.component(hit.self() ? "death.spell_self" : "death.spell",
                "%victim%", victim.getName(), "%spell%", hit.spell(), "%caster%", hit.caster()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
    }
}
