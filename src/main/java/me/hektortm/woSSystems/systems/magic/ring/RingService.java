package me.hektortm.woSSystems.systems.magic.ring;

import me.hektortm.woSSystems.systems.magic.MagicData;
import me.hektortm.woSSystems.systems.magic.MagicRules;
import me.hektortm.woSSystems.systems.magic.MagicSystem;
import me.hektortm.woSSystems.systems.magic.MagicText;
import me.hektortm.woSSystems.systems.magic.cast.CastService;
import me.hektortm.woSSystems.systems.magic.model.SpellDef;
import me.hektortm.woSSystems.systems.resourcepack.Glyph;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The spell ring and its inputs while the wand is in the main hand (magic spec §5.1, §5.2):
 * right-click opens / closes it, a number key selects that slot's spell (the ring closes a
 * moment later), F flips the page, left-click casts the selected spell; sneak + right-click
 * opens the spellbook. The selection stays after the ring closes. The ring closes by itself
 * after 6 s without input. {@link #select} is the selection seam a Bedrock form can call.
 */
public final class RingService implements Listener {

    private static final long IDLE_CLOSE_MS = 6000;
    private static final long DOUBLE_EVENT_MS = 150;

    private static final class State {
        boolean open;
        int page;
        long lastInput;
        long lastToggle;
        long lastCast;
    }

    private final Plugin plugin;
    private final MagicSystem magic;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public RingService(Plugin plugin, MagicSystem magic) {
        this.plugin = plugin;
        this.magic = magic;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
    }

    public boolean isOpen(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && s.open;
    }

    private State state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new State());
    }

    /** Selects the spell in a ring slot; false when the slot holds no spell the player knows. */
    public boolean select(Player p, int page, int pos) {
        MagicData d = magic.players().data(p);
        if (d == null || d.slot(page, pos) == null) return false;
        d.select(page, pos);
        return true;
    }

    // ── drawing ──

    private void refresh() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            State s = e.getValue();
            if (p == null || !s.open) continue;
            if (now - s.lastInput > IDLE_CLOSE_MS || !magic.wands().holdsWand(p)) close(p);
            else draw(p, s); // cooldown shading changes while it is open
        }
    }

    private void open(Player p, State s) {
        magic.cast().charges().cancel(p, true); // opening the ring cancels a charge (spec §3.2)
        s.open = true;
        MagicData d = magic.players().data(p);
        if (d != null && d.selectedPage() >= 0) s.page = d.selectedPage();
        draw(p, s);
    }

    public void close(Player p) {
        State s = states.get(p.getUniqueId());
        if (s != null && s.open) {
            s.open = false;
            p.clearTitle();
        }
    }

    private void draw(Player p, State s) {
        MagicData d = magic.players().data(p);
        if (d == null) return;
        long now = System.currentTimeMillis();
        int wandSlot = p.getInventory().getHeldItemSlot();
        List<RingLayout.Slot> slots = new ArrayList<>();
        for (int pos = 0; pos < MagicRules.SLOTS; pos++) {
            String id = d.slot(s.page, pos);
            SpellDef spell = id == null ? null : magic.stores().spell(id);
            int icon = -1;
            boolean shaded = false;
            if (spell != null) {
                Glyph g = spell.icon().isBlank() ? null : magic.glyph(spell.icon());
                if (g != null && "icon".equals(g.kind()) && g.codepoint() > 0) icon = g.codepoint();
                shaded = d.cooldownLeft(id, now) > 0 || d.mana() < spell.at(d.level(id)).manaCost();
            }
            boolean selected = spell != null && d.selectedPage() == s.page && d.selectedPos() == pos;
            slots.add(new RingLayout.Slot(icon, selected, shaded, MagicRules.keyOf(pos, wandSlot)));
        }
        TextComponent.Builder ring = Component.text();
        for (RingLayout.Part part : RingLayout.parts(slots)) {
            ring.append(Component.text(part.text()).font(Key.key("wos", part.font())));
        }
        Component title = ring.build().color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
        p.showTitle(Title.title(title, Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofSeconds(10), Duration.ZERO)));
        String sel = d.selected();
        SpellDef spell = sel == null ? null : magic.stores().spell(sel);
        String page = String.valueOf(s.page + 1);
        if (spell == null) {
            MagicText.actionBar(p, "bar.ring_none", "%page%", page);
            return;
        }
        SpellDef tier = spell.at(d.level(sel));
        MagicText.actionBar(p, spell.maxLevel() > 1 ? "bar.ring_level" : "bar.ring", "%spell%", spell.name(),
                "%cost%", MagicRules.manaText(tier.manaCost()), "%page%", page, "%level%", String.valueOf(tier.level()));
    }

    // ── inputs ──

    private boolean active(Player p) {
        return magic.wands().holdsWand(p) && magic.players().data(p) != null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND || !active(p)) return;
        State s = state(p);
        long now = System.currentTimeMillis();
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            if (now - s.lastToggle < DOUBLE_EVENT_MS) return; // a block click and an air click for one press
            s.lastToggle = now;
            s.lastInput = now;
            if (p.isSneaking()) {
                close(p);
                magic.spellbook().open(p);
            } else if (s.open) {
                close(p);
            } else {
                open(p, s);
            }
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            cast(p, s);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !active(p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        e.setCancelled(true);
        cast(p, state(p));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamage(BlockDamageEvent e) {
        if (active(e.getPlayer())) e.setCancelled(true); // the wand never breaks blocks
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        State s = states.get(p.getUniqueId());
        if (s == null || !s.open || !active(p)) return;
        e.setCancelled(true); // the wand stays in hand; the key picks a slot
        int pos = MagicRules.posOf(e.getNewSlot(), e.getPreviousSlot());
        if (pos < 0) return;
        s.lastInput = System.currentTimeMillis();
        if (!select(p, s.page, pos)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            draw(p, s);
            return;
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
        draw(p, s);
        long picked = s.lastInput;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (s.open && s.lastInput == picked) close(p); // the pick stays visible a moment
        }, 8L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!active(p)) return;
        e.setCancelled(true); // F never swaps the wand away
        State s = state(p);
        if (!s.open) return;
        s.page = (s.page + 1) % MagicRules.PAGES;
        s.lastInput = System.currentTimeMillis();
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.2f);
        draw(p, s);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }

    private void cast(Player p, State s) {
        long now = System.currentTimeMillis();
        if (now - s.lastCast < DOUBLE_EVENT_MS) return; // an attack and a swing for one click
        s.lastCast = now;
        if (magic.cast().clicks().offer(p.getUniqueId(), p.isSneaking())) {
            close(p);
            return;
        }
        if (s.open) close(p);
        MagicData d = magic.players().data(p);
        String sel = d == null ? null : d.selected();
        SpellDef spell = sel == null ? null : magic.stores().spell(sel);
        if (spell == null) {
            MagicText.actionBar(p, "cast.none");
            return;
        }
        CastService.Result r = magic.cast().cast(p, spell, CastService.Mode.PLAYER);
        magic.cast().explain(p, spell, r);
    }
}
