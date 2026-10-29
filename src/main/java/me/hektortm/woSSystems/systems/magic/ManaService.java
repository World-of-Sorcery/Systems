package me.hektortm.woSSystems.systems.magic;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.utils.Keys;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;

/**
 * Mana (magic spec §4): regenerates once a second, follows the magic attributes of the
 * held / worn items (summed on hand and armour changes and every 5 s), and shows in the
 * action bar while the wand is in the main hand, the ring is closed and nothing charges. Every 5 s it also
 * removes wands that aren't their holder's own.
 */
public final class ManaService implements Listener {

    private final Plugin plugin;
    private final MagicSystem magic;
    private int tick;

    ManaService(Plugin plugin, MagicSystem magic) {
        this.plugin = plugin;
        this.magic = magic;
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
    }

    private void second() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            MagicData d = magic.players().data(p);
            if (d == null) continue;
            if (tick % 5 == 0) {
                d.setAttributes(attributes(p));
                magic.wands().removeForeign(p);
            }
            d.regenerate(1);
            if (magic.wands().holdsWand(p) && !magic.ring().isOpen(p) && !magic.cast().charges().isCharging(p)) showBar(p, d);
        }
    }

    /** The magic attributes of everything the player holds and wears. */
    static MagicAttributes attributes(Player p) {
        PlayerInventory inv = p.getInventory();
        MagicAttributes a = MagicAttributes.parse(component(inv.getItemInMainHand()), "main_hand")
                .plus(MagicAttributes.parse(component(inv.getItemInOffHand()), "off_hand"));
        for (ItemStack armor : inv.getArmorContents()) a = a.plus(MagicAttributes.parse(component(armor), "armor"));
        return a;
    }

    @Nullable
    private static String component(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.MAGIC.get(), PersistentDataType.STRING);
    }

    /** The action bar line: the selected spell and the mana. */
    void showBar(Player p, MagicData d) {
        String sel = d.selected();
        SpellDef spell = sel == null ? null : magic.stores().spell(sel);
        String name = spell == null ? MagicText.text("bar.none") : spell.name();
        long left = sel == null ? 0 : d.cooldownLeft(sel, System.currentTimeMillis());
        String key = left > 0 ? "bar.cooldown" : "bar.mana";
        MagicText.actionBar(p, key, "%spell%", name, "%mana%", MagicRules.manaText(d.mana()),
                "%max%", MagicRules.manaText(d.max()), "%seconds%", MagicRules.seconds(left));
    }

    private void recomputeSoon(Player p) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            MagicData d = magic.players().data(p);
            if (d != null && p.isOnline()) d.setAttributes(attributes(p));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        recomputeSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        recomputeSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmor(PlayerArmorChangeEvent e) {
        recomputeSoon(e.getPlayer());
    }
}
