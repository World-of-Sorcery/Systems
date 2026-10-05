package me.hektortm.woSSystems.systems.interactions;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
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
                        List<Viewer> viewers = new ArrayList<>();
                        for (Player player : Bukkit.getOnlinePlayers()) viewers.add(Viewer.of(player));
                        for (Interaction inter : interactions) {
                            for (Location location : inter.getBlockLocations()) {
                                if (location != null) {
                                    InteractionKey key = buildKey(location);
                                    for (Viewer viewer : viewers) {
                                        // Out of sight: nothing is drawn, and the end of the pass removes what was.
                                        if (!viewer.near(location)) continue;
                                        Player player = viewer.player();
                                        particleHandler.spawnParticlesForPlayer(player, inter, location, false, key);
                                        hologramManager.handleHolograms(player, inter, location, false, key);
                                        displayManager.handleDisplays(player, inter, location, false, key);
                                        labels.show(player, inter.getInteractionId(), key, location, false, 0);
                                    }
                                }
                            }
                            for (int id : inter.getNpcIDs()) {
                                NPC npc1 = CitizensAPI.getNPCRegistry().getById(id);
                                if (npc1 == null || !npc1.isSpawned() || npc1.getEntity() == null) {
                                    continue;
                                }
                                Location at = npc1.getEntity().getLocation();
                                InteractionKey key = new InteractionKey("npc:" + id);
                                for (Viewer viewer : viewers) {
                                    if (!viewer.near(at)) continue;
                                    Player player = viewer.player();
                                    // Its own copy for each player: the particle handler moves the location it is given.
                                    Location location = at.clone();
                                    particleHandler.spawnParticlesForPlayer(player, inter, location, true, key);
                                    hologramManager.handleHolograms(player, inter, location, true, key, npc1.getEntity().getHeight());
                                    displayManager.handleDisplays(player, inter, location, true, key);
                                    labels.show(player, inter.getInteractionId(), key, location, true, npc1.getEntity().getHeight());
                                }
                            }
                        }
                        hologramManager.endPass();
                        displayManager.endPass();
                        labels.endPass();
                    });
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);

        Bukkit.getPluginManager().registerEvents(displayManager, plugin);
        displayManager.listenForClicks();
        Bukkit.getScheduler().runTaskTimer(plugin, displayManager::tick, DisplayManager.TICK_STEP, DisplayManager.TICK_STEP);
    }

    /** Debug mode's lines about a row that didn't run: which rule it has and each condition, met or not. */
    private List<String> skippedLines(Player player, String interactionId, InteractionAction action, List<Condition> conditionList,
                                      InteractionKey key) {
        boolean one = "one".equalsIgnoreCase(action.getMatchType());
        List<String> lines = new ArrayList<>();
        lines.add(DebugFormat.header("interaction", interactionId, "row " + action.getActionId(),
                one ? "skipped: needs one of these, none is met" : "skipped: needs all of these"));
        for (Condition condition : conditionList) {
            lines.add(DebugFormat.condition(condition.getName(), condition.getValue(), condition.getParameter(),
                    conditions.evaluate(player, condition, key), conditions.actual(player, condition, key)));
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
     * passing group.</p>
     *
     * @param interactionId the ID of the interaction to trigger
     * @param player        the player who triggered the interaction
     * @param key           the {@link InteractionKey} scoping local cooldowns
     *                      (may be {@code null} for global triggers)
     */
    public void triggerInteraction(String interactionId, Player player, InteractionKey key) {
        Interaction inter = getInteraction(interactionId);
        if (inter == null) {
            player.sendMessage("§cThis is not configured correctly. Please message a Staff member.");
            return;
        }

        List<InteractionAction> actions = inter.getActions();
        DebugMode debug = plugin.getDebugMode();

        for (InteractionAction action : actions) {
            List<Condition> conditionList = hub.getConditionDAO().getConditions(
                    ConditionType.INTERACTION,
                    interactionId + ":" + action.getActionId()
            );
            if (!conditionList.isEmpty()) {
                boolean shouldRun;
                if ("one".equalsIgnoreCase(action.getMatchType())) {
                    shouldRun = conditionList.isEmpty() || conditionList.stream().anyMatch(cond -> conditions.evaluate(player, cond, key));
                } else {
                    shouldRun = conditions.checkConditions(player, conditionList, key);
                }

                if (!shouldRun) {
                    // Scheduled like the rows that run, so the debug lines keep the rows' order.
                    if (debug.isOn(player)) {
                        List<String> lines = skippedLines(player, interactionId, action, conditionList, key);
                        Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(line -> debug.tell(player, line)));
                    }
                    continue;
                }
            }

            String detail = "row " + action.getActionId() + " (" + action.getBehaviour() + ")";
            Bukkit.getScheduler().runTask(WoSSystems.getPlugin(WoSSystems.class), () -> {actionHandler.executeActions(player, action.getActions(), ActionHandler.SourceType.INTERACTION, interactionId, key, detail);});

            if (action.getBehaviour().equalsIgnoreCase("continue")) {
                continue;
            } else {
                break;
            }
        }

    }

}
