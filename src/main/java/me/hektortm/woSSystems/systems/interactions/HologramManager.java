package me.hektortm.woSSystems.systems.interactions;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.ConditionHandler;
import me.hektortm.woSSystems.utils.types.ConditionType;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.Interaction;
import me.hektortm.woSSystems.utils.model.InteractionHologram;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import me.hektortm.woSSystems.utils.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HologramManager implements Listener {

    private static final double RENDER_DISTANCE_SQUARED = 16.0 * 16.0;
    private static final double BLOCK_Y_OFFSET = 1.5;
    /** Gap above the top of the NPC's bounding box. */
    private static final double NPC_HEAD_OFFSET = 0.05;

    // ── Metadata index constants for Display / TextDisplay entities (MC 1.21.x) ──
    private enum Meta {
        // Display base
        INTERPOLATION_DELAY(8),
        INTERPOLATION_DURATION(9),
        TELEPORT_DURATION(10),
        TRANSLATION(11),
        SCALE(12),
        LEFT_ROTATION(13),
        RIGHT_ROTATION(14),
        BILLBOARD(15),
        BRIGHTNESS(16),
        VIEW_RANGE(17),
        SHADOW_RADIUS(18),
        SHADOW_STRENGTH(19),
        WIDTH(20),
        HEIGHT(21),
        GLOW_COLOR(22),
        // TextDisplay specific
        TEXT(23),
        LINE_WIDTH(24),
        BACKGROUND(25),
        TEXT_OPACITY(26),
        STYLE_FLAGS(27);

        final int index;
        Meta(int index) { this.index = index; }
    }

    /** Controls which axis the TextDisplay rotates around to face the player. */
    public enum Billboard {
        /** No rotation — fixed in world space. */
        FIXED((byte) 0),
        /** Rotates around the vertical (Y) axis only. */
        VERTICAL((byte) 1),
        /** Rotates around the horizontal axis only. */
        HORIZONTAL((byte) 2),
        /** Always faces the player (full billboard). */
        CENTER((byte) 3);

        private final byte value;
        Billboard(byte value) { this.value = value; }
        public byte value() { return value; }
    }

    /** Horizontal text alignment for a TextDisplay. */
    public enum TextAlignment {
        CENTER((byte) 0),
        LEFT((byte) 1),
        RIGHT((byte) 2);

        private final byte value;
        TextAlignment(byte value) { this.value = value; }
        /** Returns the alignment encoded into bits 3–4 of the style flags byte. */
        public byte styleFlags() { return (byte) (value << 3); }
    }

    // Matches both &#RRGGBB and #RRGGBB hex color codes (exactly 6 hex digits)
    private static final Pattern HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");

    private final DAOHub hub;
    private final ConditionHandler conditionHandler;

    /**
     * Tracks the current state of holograms shown to a player for a given hologram key.
     * structFingerprint  — which hologram IDs are visible; change triggers full destroy+respawn
     * contentFingerprint — resolved text of all visible lines; change triggers metadata-only update (no flicker)
     * entityIds          — the packet entity IDs currently spawned
     */
    private record HologramState(String structFingerprint, String contentFingerprint, List<Integer> entityIds) {}

    // player UUID -> (hologramKey -> state)
    private final Map<UUID, Map<String, HologramState>> activeHolograms = new HashMap<>();
    /** player UUID -> the hologram keys the running pass has drawn (or kept away as out of range). */
    private final Map<UUID, Set<String>> visited = new HashMap<>();
    private final AtomicInteger entityIdCounter = new AtomicInteger(1_000_000);

    public HologramManager(DAOHub hub) {
        this.hub = hub;
        this.conditionHandler = WoSSystems.getInstance().getConditionHandler();
    }

    /** Starts a pass of the interaction task over every binding (see {@link #endPass()}). */
    public void beginPass() {
        visited.clear();
    }

    /**
     * Removes the holograms the pass no longer asked for: the interaction lost
     * its last hologram, was unbound or deleted, its NPC despawned, or the
     * player is in another world. Without this they stayed until a relog.
     */
    public void endPass() {
        for (Map.Entry<UUID, Map<String, HologramState>> entry : activeHolograms.entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            Set<String> keep = visited.getOrDefault(entry.getKey(), Set.of());
            entry.getValue().entrySet().removeIf(hologram -> {
                if (keep.contains(hologram.getKey())) return false;
                if (player != null) destroyEntities(player, hologram.getValue().entityIds());
                return true;
            });
        }
    }

    /** Overload for block holograms — entity height is not applicable. */
    public void handleHolograms(Player player, Interaction inter, Location location, boolean npc, InteractionKey key) {
        handleHolograms(player, inter, location, npc, key, 0);
    }

    /**
     * Called every interaction task tick for each player/location pair.
     * entityHeight — the bounding-box height of the NPC entity (ignored for blocks).
     */
    public void handleHolograms(Player player, Interaction inter, Location location, boolean npc, InteractionKey key, double entityHeight) {
        List<InteractionHologram> holograms = inter.getHolograms();
        if (holograms == null || holograms.isEmpty()) return;

        if (!player.getWorld().equals(location.getWorld())) return;

        String hologramKey = buildHologramKey(inter.getInteractionId(), location);
        visited.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(hologramKey);
        boolean inRange = player.getLocation().distanceSquared(location) <= RENDER_DISTANCE_SQUARED;

        if (inRange) {
            List<InteractionHologram> visible = resolveVisibleHolograms(player, inter, holograms, key);
            String structFP   = buildStructFingerprint(visible);
            String contentFP  = buildContentFingerprint(player, visible, key);

            HologramState current = getState(player, hologramKey);

            if (current == null) {
                spawnHolograms(player, visible, location, npc, entityHeight, hologramKey, structFP, contentFP, key);
            } else if (!current.structFingerprint().equals(structFP)) {
                // Visible hologram set changed — full respawn needed (different settings/structure)
                destroyEntities(player, current.entityIds());
                removeState(player, hologramKey);
                spawnHolograms(player, visible, location, npc, entityHeight, hologramKey, structFP, contentFP, key);
            } else if (!current.contentFingerprint().equals(contentFP)) {
                // The text or the settings changed (a cooldown counting down, an edit in the portal):
                // update the entity in place, no destroy/spawn means no flicker. Unchanged: nothing is sent.
                sendMetadataUpdate(player, current.entityIds().get(0), visible, key);
                setState(player, hologramKey, new HologramState(structFP, contentFP, current.entityIds()));
            }
        } else {
            HologramState current = getState(player, hologramKey);
            if (current != null) {
                destroyEntities(player, current.entityIds());
                removeState(player, hologramKey);
            }
        }
    }

    /**
     * Evaluates conditions for each hologram and returns the list that should currently be displayed.
     * Respects behaviour (break/continue) in the same order as other interaction systems.
     */
    private List<InteractionHologram> resolveVisibleHolograms(Player player, Interaction inter,
                                                               List<InteractionHologram> holograms, InteractionKey key) {
        List<InteractionHologram> result = new ArrayList<>();

        for (InteractionHologram hologram : holograms) {
            List<Condition> conditionList = hub.getConditionDAO().getConditions(
                    ConditionType.HOLOGRAM,
                    inter.getInteractionId() + ":" + hologram.getHologramID()
            );

            if (!conditionList.isEmpty()) {
                boolean passes;
                if ("one".equalsIgnoreCase(hologram.getMatchType())) {
                    passes = conditionList.stream().anyMatch(c -> conditionHandler.evaluate(player, c, key));
                } else {
                    passes = conditionHandler.checkConditions(player, conditionList, key);
                }
                // Conditions failed — always skip to the next hologram regardless of behaviour.
                // behaviour only controls what happens AFTER a hologram is shown (matching particles/actions).
                if (!passes) continue;
            }

            result.add(hologram);
            if (hologram.getBehaviour().equalsIgnoreCase("break")) break;
        }

        return result;
    }

    /** Which hologram IDs are currently visible. A change here means a full entity respawn is needed. */
    private String buildStructFingerprint(List<InteractionHologram> holograms) {
        StringBuilder sb = new StringBuilder();
        for (InteractionHologram h : holograms) sb.append(h.getHologramID()).append(';');
        return sb.toString();
    }

    /**
     * Resolved text of all visible lines per player, and their settings. A change here only needs a
     * metadata update, and it is the only thing that sends one.
     */
    private String buildContentFingerprint(Player player, List<InteractionHologram> holograms, InteractionKey key) {
        StringBuilder sb = new StringBuilder();
        for (InteractionHologram h : holograms) {
            for (String line : h.getHologram()) {
                sb.append(replacePlaceholders(line, player, key)).append('\n');
            }
            sb.append(h.getSettings()).append('\u0000');
        }
        return sb.toString();
    }

    private void spawnHolograms(Player player, List<InteractionHologram> holograms, Location location, boolean npc,
                                 double entityHeight, String hologramKey, String structFP, String contentFP, InteractionKey key) {
        if (holograms.isEmpty()) return;

        // NPCs: Citizens reports a larger bounding box than the visible model, so we use a
        // fraction of entityHeight to land just above the crown. Tune NPC_HEAD_OFFSET for fine adjustment.
        // Blocks: fixed offset from the block's Y, centered on the tile.
        double yBase = npc ? location.getY() + entityHeight * 0.50 + NPC_HEAD_OFFSET : location.getY() + BLOCK_Y_OFFSET;
        double x = npc ? location.getX() : location.getX() + 0.5;
        double z = npc ? location.getZ() : location.getZ() + 0.5;

        // Collect every line from all visible holograms into one component
        List<Component> lineComponents = new ArrayList<>();
        for (InteractionHologram hologram : holograms) {
            for (String line : hologram.getHologram()) {
                lineComponents.add(buildLineComponent(line, player, key));
            }
        }
        if (lineComponents.isEmpty()) return;

        Component combined = lineComponents.get(0);
        for (int i = 1; i < lineComponents.size(); i++) {
            combined = combined.append(Component.newline()).append(lineComponents.get(i));
        }

        // Use settings from the first hologram that has a non-null settings JSON
        String settingsJson = holograms.stream()
                .map(InteractionHologram::getSettings)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);

        int entityId = entityIdCounter.incrementAndGet();
        sendSpawnTextDisplay(player, entityId, x, yBase, z, combined, settingsJson);

        activeHolograms
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(hologramKey, new HologramState(structFP, contentFP, List.of(entityId)));
    }

    /**
     * The text with its {placeholder} tokens filled in for the player at this
     * binding (so local cooldowns show); unknown ones stay as written.
     */
    private String replacePlaceholders(String text, Player player, InteractionKey key) {
        PlaceholderResolver resolver = WoSSystems.getInstance().getPlaceholderResolver();
        return resolver.resolvePlaceholders(text, player, key);
    }

    /**
     * Converts <#RRGGBB>, &#RRGGBB and #RRGGBB hex codes to the §x§R§R§G§G§B§B format that
     * LegacyComponentSerializer understands, then converts & to §.
     */
    private String parseColors(String text) {
        // Normalise <#RRGGBB> → &#RRGGBB so the existing parser handles both forms
        text = text.replaceAll("<#([A-Fa-f0-9]{6})>", "&#$1");
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString().replace('&', '§');
    }

    private Component buildLineComponent(String text, Player player, InteractionKey key) {
        return LegacyComponentSerializer.legacySection()
                .deserialize(parseColors(replacePlaceholders(text, player, key)));
    }

    /** Parses a settings JSON string into a JsonObject, or returns null if absent/invalid. */
    private JsonObject parseSettings(String settingsJson) {
        if (settingsJson == null || settingsJson.isBlank()) return null;
        try { return JsonParser.parseString(settingsJson).getAsJsonObject(); } catch (Exception ignored) { return null; }
    }

    /**
     * Builds the full metadata list for a TextDisplay entity from a text component and optional settings.
     * Used by both the initial spawn and the live metadata update, so both paths stay in sync.
     */
    private List<EntityData<?>> buildDisplayMetadata(Component text, JsonObject settings) {
        List<EntityData<?>> metadata = new ArrayList<>();

        // Text component
        metadata.add(new EntityData(Meta.TEXT.index, EntityDataTypes.ADV_COMPONENT, text));

        // Billboard — default: VERTICAL; overridden by settings
        byte billboard = Billboard.VERTICAL.value();
        if (settings != null && settings.has("billboard")) {
            billboard = switch (settings.get("billboard").getAsString()) {
                case "fixed"      -> Billboard.FIXED.value();
                case "horizontal" -> Billboard.HORIZONTAL.value();
                case "center"     -> Billboard.CENTER.value();
                default           -> Billboard.VERTICAL.value();
            };
        }
        metadata.add(new EntityData(Meta.BILLBOARD.index, EntityDataTypes.BYTE, billboard));

        if (settings != null) {
            // Style flags: shadow (bit 0), see_through (bit 1), default_background (bit 2), alignment (bits 3-4)
            byte styleFlags = 0;
            if (settings.has("shadow") && settings.get("shadow").getAsBoolean())           styleFlags |= 0x01;
            if (settings.has("see_through") && settings.get("see_through").getAsBoolean()) styleFlags |= 0x02;
            if (settings.has("default_background") && settings.get("default_background").getAsBoolean()) styleFlags |= 0x04;
            if (settings.has("alignment")) {
                byte alignBits = switch (settings.get("alignment").getAsString()) {
                    case "left"  -> (byte) 1;
                    case "right" -> (byte) 2;
                    default      -> (byte) 0; // center
                };
                styleFlags |= (byte) (alignBits << 3);
            }
            if (styleFlags != 0) {
                metadata.add(new EntityData(Meta.STYLE_FLAGS.index, EntityDataTypes.BYTE, styleFlags));
            }

            if (settings.has("line_width")) {
                metadata.add(new EntityData(Meta.LINE_WIDTH.index, EntityDataTypes.INT, settings.get("line_width").getAsInt()));
            }
            if (settings.has("background")) {
                metadata.add(new EntityData(Meta.BACKGROUND.index, EntityDataTypes.INT, settings.get("background").getAsInt()));
            }
            if (settings.has("text_opacity")) {
                metadata.add(new EntityData(Meta.TEXT_OPACITY.index, EntityDataTypes.BYTE, settings.get("text_opacity").getAsByte()));
            }
            if (settings.has("shadow_radius")) {
                metadata.add(new EntityData(Meta.SHADOW_RADIUS.index, EntityDataTypes.FLOAT, settings.get("shadow_radius").getAsFloat()));
            }
            if (settings.has("shadow_strength")) {
                metadata.add(new EntityData(Meta.SHADOW_STRENGTH.index, EntityDataTypes.FLOAT, settings.get("shadow_strength").getAsFloat()));
            }
            if (settings.has("width")) {
                metadata.add(new EntityData(Meta.WIDTH.index, EntityDataTypes.FLOAT, settings.get("width").getAsFloat()));
            }
            if (settings.has("height")) {
                metadata.add(new EntityData(Meta.HEIGHT.index, EntityDataTypes.FLOAT, settings.get("height").getAsFloat()));
            }
            if (settings.has("glow_color_override")) {
                metadata.add(new EntityData(Meta.GLOW_COLOR.index, EntityDataTypes.INT, settings.get("glow_color_override").getAsInt()));
            }
            if (settings.has("view_range")) {
                metadata.add(new EntityData(Meta.VIEW_RANGE.index, EntityDataTypes.FLOAT, settings.get("view_range").getAsFloat()));
            }
            if (settings.has("interpolation_duration")) {
                metadata.add(new EntityData(Meta.INTERPOLATION_DURATION.index, EntityDataTypes.INT, settings.get("interpolation_duration").getAsInt()));
            }
            if (settings.has("start_interpolation")) {
                metadata.add(new EntityData(Meta.INTERPOLATION_DELAY.index, EntityDataTypes.INT, settings.get("start_interpolation").getAsInt()));
            }

            // Brightness override — skipped intentionally.
            // PacketEvents encodes OPTIONAL_INT as boolean+VarInt, but MC protocol expects
            // the compact OptVarInt format (value+1). Sending it corrupts the packet stream
            // and causes the client to disconnect with a network protocol error.

            if (settings.has("transformation")) {
                JsonObject transform = settings.getAsJsonObject("transformation");
                if (transform.has("translation")) {
                    var arr = transform.getAsJsonArray("translation");
                    metadata.add(new EntityData(Meta.TRANSLATION.index, EntityDataTypes.VECTOR3F,
                            new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat())));
                }
                if (transform.has("scale")) {
                    var arr = transform.getAsJsonArray("scale");
                    metadata.add(new EntityData(Meta.SCALE.index, EntityDataTypes.VECTOR3F,
                            new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat())));
                }
                if (transform.has("left_rotation")) {
                    var arr = transform.getAsJsonArray("left_rotation");
                    metadata.add(new EntityData(Meta.LEFT_ROTATION.index, EntityDataTypes.QUATERNION,
                            new Quaternion4f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat(), arr.get(3).getAsFloat())));
                }
                if (transform.has("right_rotation")) {
                    var arr = transform.getAsJsonArray("right_rotation");
                    metadata.add(new EntityData(Meta.RIGHT_ROTATION.index, EntityDataTypes.QUATERNION,
                            new Quaternion4f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat(), arr.get(3).getAsFloat())));
                }
            }
        }

        return metadata;
    }

    private void sendSpawnTextDisplay(Player player, int entityId, double x, double y, double z, Component component, String settingsJson) {
        var api = PacketEvents.getAPI();
        if (api == null) return;
        var user = api.getPlayerManager().getUser(player);
        if (user == null) return;

        try {
            user.sendPacket(new WrapperPlayServerSpawnEntity(
                    entityId, Optional.of(UUID.randomUUID()), EntityTypes.TEXT_DISPLAY,
                    new Vector3d(x, y, z), 0f, 0f, 0f, 0, Optional.empty()
            ));
            List<EntityData<?>> metadata = buildDisplayMetadata(component, parseSettings(settingsJson));
            user.sendPacket(new WrapperPlayServerEntityMetadata(entityId, clientVersion -> metadata));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a full metadata update (text + all settings) to an existing TextDisplay entity.
     * The entity is not removed or re-spawned, so there is no flicker.
     */
    private void sendMetadataUpdate(Player player, int entityId, List<InteractionHologram> holograms, InteractionKey key) {
        List<Component> lineComponents = new ArrayList<>();
        for (InteractionHologram hologram : holograms) {
            for (String line : hologram.getHologram()) {
                lineComponents.add(buildLineComponent(line, player, key));
            }
        }
        if (lineComponents.isEmpty()) return;

        Component combined = lineComponents.get(0);
        for (int i = 1; i < lineComponents.size(); i++) {
            combined = combined.append(Component.newline()).append(lineComponents.get(i));
        }

        String settingsJson = holograms.stream()
                .map(InteractionHologram::getSettings)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);

        var api = PacketEvents.getAPI();
        if (api == null) return;
        var user = api.getPlayerManager().getUser(player);
        if (user == null) return;

        try {
            List<EntityData<?>> metadata = buildDisplayMetadata(combined, parseSettings(settingsJson));
            user.sendPacket(new WrapperPlayServerEntityMetadata(entityId, clientVersion -> metadata));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setState(Player player, String hologramKey, HologramState state) {
        activeHolograms.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(hologramKey, state);
    }

    // The client forgets every entity when it respawns or changes world; forget
    // them here too, so the next pass draws them again.
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        removeAllHolograms(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeAllHolograms(event.getPlayer());
    }

    /** Remove ALL holograms for a player (e.g. on disconnect or plugin disable). */
    public void removeAllHolograms(Player player) {
        Map<String, HologramState> playerMap = activeHolograms.remove(player.getUniqueId());
        if (playerMap == null) return;

        List<Integer> allIds = playerMap.values().stream()
                .flatMap(s -> s.entityIds().stream())
                .toList();
        destroyEntities(player, allIds);
    }

    private void destroyEntities(Player player, List<Integer> ids) {
        if (ids.isEmpty()) return;

        var api = PacketEvents.getAPI();
        if (api == null) return;

        var user = api.getPlayerManager().getUser(player);
        if (user == null) return;


        int[] arr = ids.stream().mapToInt(Integer::intValue).toArray();
        user.sendPacket(new WrapperPlayServerDestroyEntities(arr));
    }

    private HologramState getState(Player player, String hologramKey) {
        Map<String, HologramState> playerMap = activeHolograms.get(player.getUniqueId());
        return playerMap == null ? null : playerMap.get(hologramKey);
    }

    private void removeState(Player player, String hologramKey) {
        Map<String, HologramState> playerMap = activeHolograms.get(player.getUniqueId());
        if (playerMap != null) playerMap.remove(hologramKey);
    }

    private String buildHologramKey(String interactionId, Location location) {
        return interactionId + ":"
                + location.getWorld().getName() + ":"
                + location.getBlockX() + ":"
                + location.getBlockY() + ":"
                + location.getBlockZ();
    }
}
