package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.magic.cast.CastService;
import me.hektortm.woSSystems.systems.magic.cast.FallGuard;
import me.hektortm.woSSystems.systems.magic.cast.SpellDeaths;
import me.hektortm.woSSystems.systems.magic.cast.SpellLitter;
import me.hektortm.woSSystems.systems.magic.cast.SpellLocks;
import me.hektortm.woSSystems.systems.magic.cast.SpellVisuals;
import me.hektortm.woSSystems.systems.magic.cast.TimedStates;
import me.hektortm.woSSystems.systems.magic.ring.RingService;
import me.hektortm.woSSystems.systems.magic.ring.SpellbookMenu;
import me.hektortm.woSSystems.systems.resourcepack.Glyph;
import me.hektortm.wosCore.WoSCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import javax.annotation.Nullable;

/**
 * WoS magic (spec 2026-10-19-magic-system-design): spells built in the portal, cast from
 * the HUD ring with a bound wand, paid with mana. This wires its parts together; built in
 * {@code onEnable} after the DAOs, before players can join.
 */
public final class MagicSystem {

    private final WoSSystems plugin;
    private final DAOHub hub;
    private final MagicStores stores;
    private final MagicPlayers players;
    private final WandService wands;
    private final WandMenu wandMenu;
    private final ManaService mana;
    private final LevelService levels;
    private final CastService cast;
    private final RingService ring;
    private final SpellbookMenu spellbook;

    public MagicSystem(WoSSystems plugin, DAOHub hub) {
        this.plugin = plugin;
        this.hub = hub;
        this.stores = hub.getMagicStores();
        this.players = new MagicPlayers(plugin, hub.getSessions(), hub.getWriter(), stores);
        this.wands = new WandService(plugin, hub, this);
        this.wandMenu = new WandMenu(this);
        this.mana = new ManaService(plugin, this);
        SpellDeaths deaths = new SpellDeaths();
        FallGuard falls = new FallGuard(plugin);
        this.levels = new LevelService(plugin, players);
        SpellLitter litter = new SpellLitter(plugin, hub.getCitemDAO());
        TimedStates states = new TimedStates(plugin, falls);
        SpellVisuals visuals = new SpellVisuals(plugin, litter);
        this.cast = new CastService(plugin, this, deaths, levels, falls, litter, states, visuals, new SpellLocks(plugin, hub));
        this.ring = new RingService(plugin, this);
        this.spellbook = new SpellbookMenu(this);
        for (Listener l : new Listener[]{new WandListener(plugin, wands), wandMenu, mana, deaths, ring, spellbook, cast.charges(), falls, litter, states, visuals}) {
            Bukkit.getPluginManager().registerEvents(l, plugin);
        }
        // A wand citem or skin edited in the portal: online wands are redrawn.
        hub.getCitemDAO().onChange(this::refreshWands);
        stores.skinStore().onChange(s -> refreshWands());
        mana.start();
        ring.start();
        cast.charges().start();
        falls.start();
    }

    private void refreshWands() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) wands.refresh(p);
        });
    }

    /** Magic is off on the hub unless the settings turn it on (spec §6.4). */
    public boolean enabledHere() {
        return !"hub".equalsIgnoreCase(WoSCore.identity().id()) || stores.settings().hubEnabled();
    }

    @Nullable
    public Glyph glyph(String id) {
        return hub.getGlyphDAO().get(id);
    }

    public MagicStores stores() { return stores; }

    public MagicPlayers players() { return players; }

    public WandService wands() { return wands; }

    public WandMenu wandMenu() { return wandMenu; }

    public ManaService mana() { return mana; }

    public CastService cast() { return cast; }

    public LevelService levels() { return levels; }

    public RingService ring() { return ring; }

    public SpellbookMenu spellbook() { return spellbook; }
}
