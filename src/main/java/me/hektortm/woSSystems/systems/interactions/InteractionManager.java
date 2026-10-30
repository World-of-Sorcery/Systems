package me.hektortm.woSSystems.systems.interactions;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.database.dao.CitemDAO;
import me.hektortm.woSSystems.systems.debug.DebugFormat;
import me.hektortm.woSSystems.systems.debug.DebugLabels;
import me.hektortm.woSSystems.systems.debug.DebugMode;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.woSSystems.utils.ConditionHandler;
import me.hektortm.woSSystems.utils.types.ConditionType;
import me.hektortm.woSSystems.utils.model.*;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.hektortm.woSSystems.systems.interactions.InterListener.buildKey;

/**
 * Central service for the interaction system.
 *
 * <p>Provides two main responsibilities:</p>
 * <ol>
 *   <li><b>Visual tick</b> ({@link #interactionTask()}) — a repeating
 *       {@link BukkitRunnable} that loads all interactions from the cache each
 *       second and spawns the configured particles, holograms and displays around every
 *       block location and Citizens NPC that has an interaction bound to it.</li>
 *   <li><b>Interaction execution</b> ({@link #triggerInteraction}) — evaluates
 *       each {@link InteractionAction}'s conditions using {@link ConditionHandler}
 *       and, for every passing action group, dispatches the action list to
 *       {@link ActionHandler}.</li>
 * </ol>
 */
public class InteractionManager {

    private final DAOHub hub;
    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final ConditionHandler conditions = plugin.getConditionHandler();
    private final ActionHandler actionHandler = plugin.getActionHandler();
    private final HologramManager hologramManager;
    private final DisplayManager displayManager;

    /** How far the visuals reach: the displays' range, the furthest of them (holograms and labels end sooner). */
    private static final double VISUAL_RANGE_SQUARED = DisplayManager.RENDER_DISTANCE_SQUARED;
    private static final long CLICK_COOLDOWN_MS = 250;
    /** player → (interaction id → when they last ran it by clicking). */
    private final Map<UUID, Map<String, Long>> lastClicks = new HashMap<>();
    /** Drawing failures already logged (see {@link #reportOnce}). */
    private final java.util.Set<String> reported = new java.util.HashSet<>();

    /**
     * @param hub the DAO hub used to access interaction and condition data
     */
    public InteractionManager(DAOHub hub) {
        this.hub = hub;
        this.hologramManager = new HologramManager(hub);
        this.displayManager = new DisplayManager(hub, this);
    }

    /**
     * Returns the {@link DisplayManager} used by this interaction manager.
     *
     * @return the display manager
     */
    public DisplayManager getDisplayManager() {
        return displayManager;
    }

    /**
     * Returns the {@link HologramManager} used by this interaction manager.
     *
     * @return the hologram manager
     */
    public HologramManager getHologramManager() {
        return hologramManager;
    }

    /** A player and where they stand, read once per pass of the interaction task. */
    private record Viewer(Player player, World world, double x, double y, double z) {
        static Viewer of(Player player) {
            Location at = player.getLocation();
            return new Viewer(player, at.getWorld(), at.getX(), at.getY(), at.getZ());
        }

        /** Whether the player is in the same world and close enough to see anything drawn there. */
        boolean near(Location location) {
            if (!world.equals(location.getWorld())) return false;
            double dx = x - location.getX(), dy = y - location.getY(), dz = z - location.getZ();
            return dx * dx + dy * dy + dz * dz <= VISUAL_RANGE_SQUARED;
        }
    }

    /**
     * As {@link #triggerInteraction}, for a click: a player's clicks on one
     * interaction closer together than {@link #CLICK_COOLDOWN_MS} count as one
     * (a click arrives once per hand, and again while the button is held).
     */
    public void triggerByClick(String interactionId, Player player, InteractionKey key) {
        long now = System.currentTimeMillis();
        Long last = lastClicks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(interactionId, now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        // A quest or sequence waiting on this click takes it (or the recipe menu opens): the actions don't run.
        if (plugin.getQuestEngine() != null && plugin.getQuestEngine().clicks().click(player, interactionId, key)) return;
        triggerInteraction(interactionId, player, key);
    }

    /** Forgets a player's click cooldowns (on quit). */
    public void removeClickCooldowns(Player player) {
        lastClicks.remove(player.getUniqueId());
    }

    private Interaction getInteraction(String id) {
        Interaction inter = hub.getInteractionDAO().getInteractionByID(id);
        return inter;
    }

    /**
     * Starts the repeating per-tick visual task for all interactions.
     *
     * <p>Every 20 ticks (once per second) the task loads the full interaction
     * cache asynchronously and then, back on the main thread, spawns particles
     * and manages holograms and displays for each interaction's block locations
     * and bound NPCs for every online player near them. A second, faster task animates the
     * displays and checks which players walked into one.</p>
     */
    public void interactionTask() {
        ParticleHandler particleHandler = new ParticleHandler(hub);
        plugin.getLogger().info("[InteractionManager] Starting interaction task.");

        new BukkitRunnable() {
            @Override
            public void run() {
                // Load interaction data off the main thread, then process visuals on main thread
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    List<Interaction> interactions = hub.getInteractionDAO().cache();
                    plugin.getLogger().fine("[InteractionManager] Tick — loaded " + interactions.size() + " interaction(s).");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        DebugLabels labels = plugin.getDebugLabels();
                        labels.beginPass();
                        hologramManager.beginPass();
                        displayManager.beginPass();
                        // One failing binding must not stop the pass: everything after it would not be
                        // drawn, and without endPass nothing removed (unbound, deleted) would go away.
                        try {
                            List<Viewer> viewers = new ArrayList<>();
                            for (Player player : Bukkit.getOnlinePlayers()) viewers.add(Viewer.of(player));
                            Map<String, Interaction> byId = new HashMap<>();
                            for (Interaction inter : interactions) {
                                byId.put(inter.getInteractionId(), inter);
                                for (Location location : new ArrayList<>(inter.getBlockLocations())) {
                                    if (location != null) drawAtBlock(inter, location, viewers, particleHandler, labels);
                                }
                                for (int id : new ArrayList<>(inter.getNpcIDs())) {
                                    drawAtNpc(inter, id, viewers, particleHandler, labels);
                                }
                            }
                            // Placed custom items show their placed interaction's visuals like a bound block.
                            for (CitemDAO.PlacedAction placed : hub.getCitemDAO().placedActions()) {
                                Interaction inter = byId.get(placed.interactionId());
                                if (inter != null && hasVisuals(inter)) {
                                    drawAtBlock(inter, placed.block(), viewers, particleHandler, labels);
                                }
                            }
                        } catch (RuntimeException e) {
                            reportOnce("pass", e);
                        } finally {
                            hologramManager.endPass();
                            displayManager.endPass();
                            labels.endPass();
                        }
                    });
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);

        Bukkit.getPluginManager().registerEvents(hologramManager, plugin);
        Bukkit.getPluginManager().registerEvents(displayManager, plugin);
        displayManager.listenForClicks();
        Bukkit.getScheduler().runTaskTimer(plugin, displayManager::tick, DisplayManager.TICK_STEP, DisplayManager.TICK_STEP);
    }

    /** Particles, holograms, displays and debug labels of one interaction at one block, for every player near it. */
    private void drawAtBlock(Interaction inter, Location location, List<Viewer> viewers,
                             ParticleHandler particleHandler, DebugLabels labels) {
        if (location.getWorld() == null) return;
        InteractionKey key = buildKey(location);
        for (Viewer viewer : viewers) {
            // Out of sight: nothing is drawn, and the end of the pass removes what was.
            if (!viewer.near(location)) continue;
            Player player = viewer.player();
            try {
                particleHandler.spawnParticlesForPlayer(player, inter, location, false, key);
                hologramManager.handleHolograms(player, inter, location, false, key);
                displayManager.handleDisplays(player, inter, location, false, key);
                labels.show(player, inter.getInteractionId(), key, location, false, 0);
            } catch (RuntimeException e) {
                reportOnce(inter.getInteractionId() + " at " + key.getKey(), e);
            }
        }
    }

    /** As {@link #drawAtBlock}, at a Citizens NPC (skipped while it is not spawned). */
    private void drawAtNpc(Interaction inter, int npcId, List<Viewer> viewers,
                           ParticleHandler particleHandler, DebugLabels labels) {
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null) return;
        Location at = npc.getEntity().getLocation();
        double height = npc.getEntity().getHeight();
        InteractionKey key = new InteractionKey("npc:" + npcId);
        for (Viewer viewer : viewers) {
            if (!viewer.near(at)) continue;
            Player player = viewer.player();
            // Its own copy for each player, so nothing drawn below can move another's.
            Location location = at.clone();
            try {
                particleHandler.spawnParticlesForPlayer(player, inter, location, true, key);
                hologramManager.handleHolograms(player, inter, location, true, key, height);
                displayManager.handleDisplays(player, inter, location, true, key);
                labels.show(player, inter.getInteractionId(), key, location, true, height);
            } catch (RuntimeException e) {
                reportOnce(inter.getInteractionId() + " at " + key.getKey(), e);
            }
        }
    }

    /** Logs a drawing failure with its stack trace once per place and error, not once a second. */
    private void reportOnce(String where, RuntimeException e) {
        if (reported.add(where + " " + e)) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[InteractionManager] Drawing " + where + " failed", e);
        }
    }

    /** Whether the interaction draws anything at all (placed items without visuals are skipped). */
    private static boolean hasVisuals(Interaction inter) {
        return notEmpty(inter.getParticles()) || notEmpty(inter.getHolograms()) || notEmpty(inter.getDisplays());
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    /** Debug mode's lines about a row that didn't run: which rule it has and each condition, met or not. */
    private List<String> skippedLines(Player player, String interactionId, InteractionAction action, List<Condition> conditionList,
                                      InteractionKey key, @Nullable Map<String, String> values) {
        boolean one = "one".equalsIgnoreCase(action.getMatchType());
        List<String> lines = new ArrayList<>();
        lines.add(DebugFormat.header("interaction", interactionId, "row " + action.getActionId(),
                one ? "skipped: needs one of these, none is met" : "skipped: needs all of these"));
        for (Condition condition : conditionList) {
            lines.add(DebugFormat.condition(condition.getName(), condition.getValue(), condition.getParameter(),
                    conditions.evaluate(player, condition, key, values), conditions.actual(player, condition, key, values)));
        }
        return lines;
    }

    /**
     * Triggers an interaction for a player, evaluating conditions and
     * executing the resulting action lists.
     *
     * <p>Each {@link InteractionAction} in the interaction is checked against
     * its associated {@link Condition} list.  If the match type is
     * {@code "one"}, any passing condition is sufficient; otherwise all
     * conditions must pass.  Actions whose conditions fail are skipped.
     * Action groups with behaviour {@code "continue"} allow subsequent groups
     * to also run; any other behaviour value stops processing after the first
     * passing group. Spell rows are skipped: they run only by
     * {@link #triggerBySpell}.</p>
     *
     * @param interactionId the ID of the interaction to trigger
     * @param player        the player who triggered the interaction
     * @param key           the {@link InteractionKey} scoping local cooldowns
     *                      (may be {@code null} for global triggers)
     */
    public void triggerInteraction(String interactionId, Player player, InteractionKey key) {
        triggerInteraction(interactionId, player, key, null);
    }

    /**
     * A spell hit the interaction's block, NPC or placed item (spell locks, {@link SpellHits}):
     * its spell rows run for the caster, knowing the spell. An interaction without spell rows
     * (or that doesn't exist) does nothing.
     *
     * @param level the caster's level of the spell
     */
    public void triggerBySpell(String interactionId, Player caster, InteractionKey key, String spellId, String spellName, int level) {
        if (!reactsToSpells(interactionId)) return;
        triggerInteraction(interactionId, caster, key, SpellHits.values(spellId, spellName, level));
    }

    /** Whether the interaction has a spell row. */
    public boolean reactsToSpells(String interactionId) {
        Interaction inter = getInteraction(interactionId);
        if (inter == null) return false;
        for (InteractionAction action : inter.getActions()) {
            if (SpellHits.isSpellRow(rowConditions(interactionId, action))) return true;
        }
        return false;
    }

    private List<Condition> rowConditions(String interactionId, InteractionAction action) {
        return hub.getConditionDAO().getConditions(ConditionType.INTERACTION, interactionId + ":" + action.getActionId());
    }

    /**
     * As {@link #triggerInteraction(String, Player, InteractionKey)}; {@code values} are the
     * run's local values: a spell hit's ({@link SpellHits#values}) runs only the spell rows,
     * anything else (null) only the other rows.
     */
    private void triggerInteraction(String interactionId, Player player, InteractionKey key, @Nullable Map<String, String> values) {
        Interaction inter = getInteraction(interactionId);
        if (inter == null) {
            player.sendMessage("§cThis is not configured correctly. Please message a Staff member.");
            return;
        }
        me.hektortm.woSSystems.systems.quests.QuestHooks.report(player, "interaction", "triggered", java.util.Map.of("interaction", interactionId));

        List<InteractionAction> actions = inter.getActions();
        DebugMode debug = plugin.getDebugMode();
        String hitBy = SpellHits.spell(values);

        for (InteractionAction action : actions) {
            List<Condition> conditionList = rowConditions(interactionId, action);
            if (SpellHits.isSpellRow(conditionList) != (hitBy != null)) continue;
            if (!conditionList.isEmpty()) {
                boolean shouldRun;
                if ("one".equalsIgnoreCase(action.getMatchType())) {
                    shouldRun = conditionList.stream().anyMatch(cond -> conditions.evaluate(player, cond, key, values));
                } else {
                    shouldRun = conditionList.stream().allMatch(cond -> conditions.evaluate(player, cond, key, values));
                }

                if (!shouldRun) {
                    // Scheduled like the rows that run, so the debug lines keep the rows' order.
                    if (debug.isOn(player)) {
                        List<String> lines = skippedLines(player, interactionId, action, conditionList, key, values);
                        Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(line -> debug.tell(player, line)));
                    }
                    continue;
                }
            }

            String detail = "row " + action.getActionId() + " (" + action.getBehaviour() + ")" + (hitBy == null ? "" : ", hit by spell " + hitBy);
            Bukkit.getScheduler().runTask(WoSSystems.getPlugin(WoSSystems.class), () -> {actionHandler.executeActions(player, action.getActions(), ActionHandler.SourceType.INTERACTION, interactionId, key, detail, values);});

            if ("continue".equalsIgnoreCase(action.getBehaviour())) {
                continue;
            } else {
                break;
            }
        }

    }

}
