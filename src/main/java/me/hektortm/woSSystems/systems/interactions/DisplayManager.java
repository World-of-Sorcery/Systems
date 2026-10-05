package me.hektortm.woSSystems.systems.interactions;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.hektortm.woSSystems.WoSSystems;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.guis.GuiRules;
import me.hektortm.woSSystems.systems.guis.HeadProfiles;
import me.hektortm.woSSystems.systems.interactions.DisplayRules.Box;
import me.hektortm.woSSystems.systems.interactions.DisplayRules.Pose;
import me.hektortm.woSSystems.systems.interactions.DisplayRules.Quat;
import me.hektortm.woSSystems.systems.interactions.DisplaySettings.Kind;
import me.hektortm.woSSystems.systems.interactions.DisplaySettings.Vec3;
import me.hektortm.woSSystems.utils.ConditionHandler;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.Interaction;
import me.hektortm.woSSystems.utils.model.InteractionDisplay;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import me.hektortm.woSSystems.utils.types.ConditionType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * The display entities of interactions: block, item and custom item displays
 * shown at every block and NPC an interaction is bound to. They exist only as
 * packets, per player, like the holograms.
 *
 * <p>{@link #handleDisplays} runs once a second from the interaction task and
 * decides what a player sees; {@link #tick} runs every second tick and sends
 * animation keyframes and checks who walked into a display. A display that can
 * be clicked gets an invisible "interaction" entity as its hitbox (a display
 * entity has none); the click arrives as a packet. Everything else runs on
 * the main thread. The maths is in {@link DisplayRules}.</p>
 */
public class DisplayManager implements Listener {

    static final double RENDER_DISTANCE_SQUARED = 32.0 * 32.0;
    /** How often {@link #tick} runs, in ticks. */
    public static final int TICK_STEP = 2;
    /** Ticks the client takes to slide a display after its NPC moved (the position is sent once a second). */
    private static final int TELEPORT_DURATION = 20;

    // Metadata indexes of display entities (Minecraft 1.21.x).
    private static final int META_FLAGS = 0;
    private static final int META_INTERPOLATION_DELAY = 8;
    private static final int META_INTERPOLATION_DURATION = 9;
    private static final int META_TELEPORT_DURATION = 10;
    private static final int META_TRANSLATION = 11;
    private static final int META_SCALE = 12;
    private static final int META_LEFT_ROTATION = 13;
    private static final int META_RIGHT_ROTATION = 14;
    private static final int META_BILLBOARD = 15;
    private static final int META_BRIGHTNESS = 16;
    private static final int META_VIEW_RANGE = 17;
    private static final int META_SHADOW_RADIUS = 18;
    private static final int META_SHADOW_STRENGTH = 19;
    private static final int META_WIDTH = 20;
    private static final int META_HEIGHT = 21;
    private static final int META_GLOW_COLOR = 22;
    /** The item stack of an item display, the block state of a block display. */
    private static final int META_CONTENT = 23;
    private static final int META_ITEM_DISPLAY = 24;
    private static final byte FLAG_GLOWING = 0x40;
    // Metadata indexes of the interaction entity (the click hitbox).
    private static final int META_HITBOX_WIDTH = 8;
    private static final int META_HITBOX_HEIGHT = 9;
    private static final int META_HITBOX_RESPONSIVE = 10;
    /** Two clicks of one player closer together than this count as one (a right-click arrives several times). */
    private static final long CLICK_COOLDOWN_MS = 250;
    private static final int FIRST_ENTITY_ID = 2_000_000;

    /** One display entity a player currently sees; {@code hitboxId} is its click hitbox, 0 without one. */
    private record Shown(int entityId, int hitboxId, DisplaySettings settings, int interval, String touchKey) {}

    /** What a player sees at one binding (a block or an NPC) of one interaction. */
    private static final class Binding {
        final String interactionId;
        final InteractionKey key;
        final String fingerprint;
        final List<Shown> shown;
        double x, y, z;

        Binding(String interactionId, InteractionKey key, String fingerprint, List<Shown> shown, Location anchor) {
            this.interactionId = interactionId;
            this.key = key;
            this.fingerprint = fingerprint;
            this.shown = shown;
            this.x = anchor.getX();
            this.y = anchor.getY();
            this.z = anchor.getZ();
        }
    }

    private final DAOHub hub;
    private final InteractionManager interactions;
    private final ConditionHandler conditionHandler;
    private final HeadProfiles heads = new HeadProfiles(WoSSystems.getInstance());

    /** player → (binding key → what is shown there). */
    private final Map<UUID, Map<String, Binding>> active = new HashMap<>();
    /** player → the touch boxes they are standing in. */
    private final Map<UUID, Set<String>> inside = new HashMap<>();
    /** The bindings seen in the current pass of the interaction task; the rest is stale. */
    private final Set<String> visited = new HashSet<>();
    /** Displays already reported as unreadable, so the log gets one line and not one per second. */
    private final Set<String> warned = new HashSet<>();
    // Holograms count up from 1,000,000; displays stay clear of them.
    private final AtomicInteger entityIdCounter = new AtomicInteger(FIRST_ENTITY_ID);
    /** player → when they last clicked a display. */
    private final Map<UUID, Long> lastClick = new HashMap<>();
    /** The animation clock, shared by all players so everyone sees the same phase. */
    private long clock;

    public DisplayManager(DAOHub hub, InteractionManager interactions) {
        this.hub = hub;
        this.interactions = interactions;
        this.conditionHandler = WoSSystems.getInstance().getConditionHandler();
    }

    // ── once a second: what does the player see ────────────────────────────────

    /** Starts a pass of the interaction task. */
    public void beginPass() {
        visited.clear();
    }

    /**
     * Shows, updates or removes the displays of one interaction at one binding
     * for one player. {@code location} is the bound block or the NPC's position.
     */
    public void handleDisplays(Player player, Interaction inter, Location location, boolean npc, InteractionKey key) {
        String bindingKey = inter.getInteractionId() + "|" + key.getKey();
        visited.add(player.getUniqueId() + "|" + bindingKey);

        Binding current = binding(player, bindingKey);
        List<InteractionDisplay> displays = inter.getDisplays();
        boolean near = !displays.isEmpty()
                && player.getWorld().equals(location.getWorld())
                && player.getLocation().distanceSquared(location) <= RENDER_DISTANCE_SQUARED;
        if (!near) {
            if (current != null) remove(player, bindingKey);
            return;
        }

        Location anchor = npc ? location : new Location(location.getWorld(),
                location.getBlockX() + 0.5, location.getBlockY() + 0.5, location.getBlockZ() + 0.5);
        List<InteractionDisplay> visible = visibleDisplays(player, inter, key);
        String fingerprint = fingerprint(visible);

        if (current != null && !current.fingerprint.equals(fingerprint)) {
            remove(player, bindingKey);
            current = null;
        }
        if (current == null) {
            spawn(player, inter.getInteractionId(), key, bindingKey, visible, fingerprint, anchor);
        } else if (current.x != anchor.getX() || current.y != anchor.getY() || current.z != anchor.getZ()) {
            move(player, current, anchor);
        }
    }

    /** Ends a pass: whatever was not visited (unbound, deleted, player gone) is removed. */
    public void endPass() {
        for (Map.Entry<UUID, Map<String, Binding>> perPlayer : active.entrySet()) {
            Player player = Bukkit.getPlayer(perPlayer.getKey());
            Set<String> in = inside.get(perPlayer.getKey());
            Iterator<Map.Entry<String, Binding>> it = perPlayer.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Binding> e = it.next();
                if (visited.contains(perPlayer.getKey() + "|" + e.getKey())) continue;
                if (player != null) destroy(player, e.getValue());
                forget(in, e.getValue());
                it.remove();
            }
        }
    }

    /** The displays whose conditions pass, in order; "break" stops after the first one shown. */
    private List<InteractionDisplay> visibleDisplays(Player player, Interaction inter, InteractionKey key) {
        List<InteractionDisplay> result = new ArrayList<>();
        for (InteractionDisplay display : inter.getDisplays()) {
            List<Condition> conditions = hub.getConditionDAO().getConditions(
                    ConditionType.DISPLAY, inter.getInteractionId() + ":" + display.getDisplayID());
            if (!conditions.isEmpty()) {
                boolean passes = "one".equalsIgnoreCase(display.getMatchType())
                        ? conditions.stream().anyMatch(c -> conditionHandler.evaluate(player, c, key))
                        : conditionHandler.checkConditions(player, conditions, key);
                if (!passes) continue;
            }
            result.add(display);
            if ("break".equalsIgnoreCase(display.getBehaviour())) break;
        }
        return result;
    }

    /** Which displays are shown and with which settings; a change means removing and drawing again. */
    private String fingerprint(List<InteractionDisplay> displays) {
        StringBuilder sb = new StringBuilder();
        for (InteractionDisplay d : displays) sb.append(d.getDisplayID()).append('#').append(d.getRawSettings()).append(';');
        return sb.toString();
    }

    private void spawn(Player player, String interactionId, InteractionKey key, String bindingKey,
                       List<InteractionDisplay> displays, String fingerprint, Location anchor) {
        User user = user(player);
        if (user == null) return;

        List<Shown> shown = new ArrayList<>();
        for (InteractionDisplay display : displays) {
            DisplaySettings s = display.getSettings();
            String name = interactionId + ":" + display.getDisplayID();
            Object content = content(player, s, name);
            if (content == null) continue;

            int entityId = entityIdCounter.incrementAndGet();
            Vec3 o = s.offset();
            try {
                user.sendPacket(new WrapperPlayServerSpawnEntity(
                        entityId, Optional.of(UUID.randomUUID()),
                        s.kind() == Kind.BLOCK ? EntityTypes.BLOCK_DISPLAY : EntityTypes.ITEM_DISPLAY,
                        new Vector3d(anchor.getX() + o.x(), anchor.getY() + o.y(), anchor.getZ() + o.z()),
                        s.pitch(), s.yaw(), 0f, 0, Optional.empty()));
                List<EntityData<?>> metadata = metadata(s, content);
                send(user, new WrapperPlayServerEntityMetadata(entityId, metadata));
            } catch (RuntimeException e) {
                warnOnce(name, "could not be sent: " + e);
                continue;
            }
            int hitboxId = s.touch().click() ? spawnHitbox(user, s, anchor.getX() + o.x(), anchor.getY() + o.y(), anchor.getZ() + o.z()) : 0;
            shown.add(new Shown(entityId, hitboxId, s, DisplayRules.keyframeInterval(s), bindingKey + "|" + display.getDisplayID()));
        }
        active.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(bindingKey, new Binding(interactionId, key, fingerprint, shown, anchor));
    }

    /** The invisible entity a player clicks: as big as the touch box, standing where it is. Returns its id. */
    private int spawnHitbox(User user, DisplaySettings s, double x, double y, double z) {
        int id = entityIdCounter.incrementAndGet();
        send(user, new WrapperPlayServerSpawnEntity(id, Optional.of(UUID.randomUUID()), EntityTypes.INTERACTION,
                hitboxPosition(s, x, y, z), 0f, 0f, 0f, 0, Optional.empty()));
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(META_HITBOX_WIDTH, EntityDataTypes.FLOAT, (float) s.touch().width()));
        data.add(new EntityData<>(META_HITBOX_HEIGHT, EntityDataTypes.FLOAT, (float) s.touch().height()));
        data.add(new EntityData<>(META_HITBOX_RESPONSIVE, EntityDataTypes.BOOLEAN, true));
        send(user, new WrapperPlayServerEntityMetadata(id, data));
        return id;
    }

    /** An interaction entity stands on the middle of its box's floor. */
    private static Vector3d hitboxPosition(DisplaySettings s, double x, double y, double z) {
        Box box = DisplayRules.touchBox(s, x, y, z);
        return new Vector3d((box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2);
    }

    /** The NPC walked: slide its displays after it. */
    private void move(Player player, Binding binding, Location anchor) {
        binding.x = anchor.getX();
        binding.y = anchor.getY();
        binding.z = anchor.getZ();
        User user = user(player);
        if (user == null) return;
        for (Shown shown : binding.shown) {
            Vec3 o = shown.settings().offset();
            send(user, new WrapperPlayServerEntityTeleport(shown.entityId(),
                    new Vector3d(binding.x + o.x(), binding.y + o.y(), binding.z + o.z()),
                    shown.settings().yaw(), shown.settings().pitch(), false));
            if (shown.hitboxId() != 0) {
                send(user, new WrapperPlayServerEntityTeleport(shown.hitboxId(),
                        hitboxPosition(shown.settings(), binding.x + o.x(), binding.y + o.y(), binding.z + o.z()), 0f, 0f, false));
            }
        }
    }

    // ── what is shown ──────────────────────────────────────────────────────────

    /**
     * The block state id (an Integer) or the item stack to show to this player,
     * or null when the settings name something the server does not know.
     */
    private Object content(Player player, DisplaySettings s, String name) {
        try {
            if (s.kind() == Kind.BLOCK) {
                return SpigotConversionUtil.fromBukkitBlockData(Bukkit.createBlockData(s.block())).getGlobalId();
            }
            ItemStack item = s.kind() == Kind.CITEM ? hub.getCitemDAO().getCitem(s.citem()) : vanillaItem(player, s, name);
            if (item == null) {
                warnOnce(name, s.kind() == Kind.CITEM ? "unknown custom item '" + s.citem() + "'" : "unknown item '" + s.item() + "'");
                return null;
            }
            return SpigotConversionUtil.fromBukkitItemStack(item);
        } catch (RuntimeException e) {
            warnOnce(name, "cannot be shown: " + e.getMessage());
            return null;
        }
    }

    /** A vanilla item with its model ("wos:" unless written with a namespace) or, for a player head, its skin. */
    private ItemStack vanillaItem(Player player, DisplaySettings s, String name) {
        Material material = Material.matchMaterial(s.item());
        if (material == null || !material.isItem()) return null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (meta instanceof SkullMeta) {
            applySkin((SkullMeta) meta, player, s.head());
        } else if (!s.itemModel().isEmpty()) {
            GuiRules.Key model = GuiRules.resourceKey(s.itemModel(), "wos");
            if (model == null) warnOnce(name, "has an invalid item model '" + s.itemModel() + "', ignored");
            else meta.setItemModel(new NamespacedKey(model.namespace(), model.path()));
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * A player head's skin, as in GUIs: a texture URL / base64 value or a player
     * name; placeholders are filled in, so {player_name} is the viewer's own head.
     */
    private void applySkin(SkullMeta meta, Player player, String head) {
        GuiRules.HeadSkin skin = GuiRules.headSkin(WoSSystems.getInstance().getPlaceholderResolver().resolvePlaceholders(head, player));
        if (skin instanceof GuiRules.Owner) {
            meta.setPlayerProfile(heads.profile(((GuiRules.Owner) skin).name()));
        } else if (skin instanceof GuiRules.Texture) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", ((GuiRules.Texture) skin).value()));
            meta.setPlayerProfile(profile);
        }
    }

    /** The whole metadata of a new display, at its pose for the current tick. */
    private List<EntityData<?>> metadata(DisplaySettings s, Object content) {
        List<EntityData<?>> data = new ArrayList<>();
        Pose pose = DisplayRules.poseAt(s, clock);
        Vec3 scale = s.scale();
        Quat right = DisplayRules.fromDegrees(s.rightRotationDeg());

        if (s.glowing()) data.add(new EntityData<>(META_FLAGS, EntityDataTypes.BYTE, FLAG_GLOWING));
        data.add(new EntityData<>(META_TELEPORT_DURATION, EntityDataTypes.INT, TELEPORT_DURATION));
        data.add(new EntityData<>(META_TRANSLATION, EntityDataTypes.VECTOR3F, vector(pose.translation())));
        data.add(new EntityData<>(META_SCALE, EntityDataTypes.VECTOR3F, vector(scale)));
        data.add(new EntityData<>(META_LEFT_ROTATION, EntityDataTypes.QUATERNION, quaternion(pose.leftRotation())));
        data.add(new EntityData<>(META_RIGHT_ROTATION, EntityDataTypes.QUATERNION, quaternion(right)));
        data.add(new EntityData<>(META_BILLBOARD, EntityDataTypes.BYTE, DisplayRules.billboard(s.billboard())));
        if (s.brightness() != null) {
            data.add(new EntityData<>(META_BRIGHTNESS, EntityDataTypes.INT,
                    DisplayRules.packBrightness(s.brightness().block(), s.brightness().sky())));
        }
        data.add(new EntityData<>(META_VIEW_RANGE, EntityDataTypes.FLOAT, s.viewRange()));
        data.add(new EntityData<>(META_SHADOW_RADIUS, EntityDataTypes.FLOAT, s.shadowRadius()));
        data.add(new EntityData<>(META_SHADOW_STRENGTH, EntityDataTypes.FLOAT, s.shadowStrength()));
        data.add(new EntityData<>(META_WIDTH, EntityDataTypes.FLOAT, s.width()));
        data.add(new EntityData<>(META_HEIGHT, EntityDataTypes.FLOAT, s.height()));
        if (s.glowColor() != 0) data.add(new EntityData<>(META_GLOW_COLOR, EntityDataTypes.INT, s.glowColor()));

        if (content instanceof Integer) {
            data.add(new EntityData<>(META_CONTENT, EntityDataTypes.BLOCK_STATE, (Integer) content));
        } else {
            data.add(new EntityData<>(META_CONTENT, EntityDataTypes.ITEMSTACK,
                    (com.github.retrooper.packetevents.protocol.item.ItemStack) content));
            data.add(new EntityData<>(META_ITEM_DISPLAY, EntityDataTypes.BYTE, DisplayRules.itemDisplay(s.itemDisplay())));
        }
        return data;
    }

    // ── every second tick: animation and touch ─────────────────────────────────

    /** Sends the animation keyframes that are due and runs interactions for players who walked into a display. */
    public void tick() {
        clock += TICK_STEP;
        for (Map.Entry<UUID, Map<String, Binding>> perPlayer : active.entrySet()) {
            Player player = Bukkit.getPlayer(perPlayer.getKey());
            if (player == null) continue;
            User user = user(player);
            BoundingBox bb = player.getBoundingBox();
            Box playerBox = new Box(bb.getMinX(), bb.getMinY(), bb.getMinZ(), bb.getMaxX(), bb.getMaxY(), bb.getMaxZ());
            Set<String> in = inside.computeIfAbsent(perPlayer.getKey(), k -> new HashSet<>());

            // A copy: a triggered interaction may change what the player sees.
            for (Binding binding : new ArrayList<>(perPlayer.getValue().values())) {
                for (Shown shown : binding.shown) {
                    if (user != null && shown.interval() > 0 && clock % shown.interval() == 0) keyframe(user, shown);
                    if (shown.settings().touch().walk()) touch(player, in, binding, shown, playerBox);
                }
            }
        }
    }

    /** Tells the client where the display should be one interval from now; it moves there smoothly. */
    private void keyframe(User user, Shown shown) {
        Pose pose = DisplayRules.poseAt(shown.settings(), clock + shown.interval());
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(META_INTERPOLATION_DELAY, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(META_INTERPOLATION_DURATION, EntityDataTypes.INT, shown.interval()));
        data.add(new EntityData<>(META_TRANSLATION, EntityDataTypes.VECTOR3F, vector(pose.translation())));
        data.add(new EntityData<>(META_LEFT_ROTATION, EntityDataTypes.QUATERNION, quaternion(pose.leftRotation())));
        send(user, new WrapperPlayServerEntityMetadata(shown.entityId(), data));
    }

    private void touch(Player player, Set<String> in, Binding binding, Shown shown, Box playerBox) {
        Vec3 o = shown.settings().offset();
        Box box = DisplayRules.touchBox(shown.settings(), binding.x + o.x(), binding.y + o.y(), binding.z + o.z());
        if (DisplayRules.entered(in, shown.touchKey(), box.overlaps(playerBox))) {
            interactions.triggerInteraction(binding.interactionId, player, binding.key);
        }
    }

    // ── clicking ───────────────────────────────────────────────────────────────

    /** Starts listening for clicks on display hitboxes. */
    public void listenForClicks() {
        var api = PacketEvents.getAPI();
        if (api == null) return;
        api.getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
                WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                int entityId = packet.getEntityId();
                if (entityId <= FIRST_ENTITY_ID) return;
                // A right-click arrives as "interact at" and "interact", once per hand: count one of them.
                boolean counts = packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK
                        || (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT
                        && packet.getHand() == InteractionHand.MAIN_HAND);
                UUID uuid = event.getUser().getUUID();
                if (!counts || uuid == null) return;
                // Packets arrive off the main thread; everything here is main-thread state.
                Bukkit.getScheduler().runTask(WoSSystems.getInstance(), () -> clicked(uuid, entityId));
            }
        }, PacketListenerPriority.NORMAL);
    }

    /** A player clicked the entity with this id: if it is the hitbox of a display they see, run its interaction. */
    private void clicked(UUID uuid, int entityId) {
        Player player = Bukkit.getPlayer(uuid);
        Map<String, Binding> bindings = active.get(uuid);
        if (player == null || bindings == null) return;
        for (Binding binding : bindings.values()) {
            for (Shown shown : binding.shown) {
                if (shown.hitboxId() != entityId) continue;
                long now = System.currentTimeMillis();
                Long last = lastClick.put(uuid, now);
                if (last == null || now - last >= CLICK_COOLDOWN_MS) {
                    interactions.triggerInteraction(binding.interactionId, player, binding.key);
                }
                return;
            }
        }
    }

    // ── removing ───────────────────────────────────────────────────────────────

    // The client forgets every entity when it respawns or changes world; forget
    // them here too, so the next pass draws them again.
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        removeAllDisplays(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeAllDisplays(event.getPlayer());
    }

    /** Removes everything a player sees (on quit and when the plugin stops). */
    public void removeAllDisplays(Player player) {
        Map<String, Binding> bindings = active.remove(player.getUniqueId());
        inside.remove(player.getUniqueId());
        lastClick.remove(player.getUniqueId());
        if (bindings == null) return;
        for (Binding binding : bindings.values()) destroy(player, binding);
    }

    private void remove(Player player, String bindingKey) {
        Map<String, Binding> bindings = active.get(player.getUniqueId());
        Binding binding = bindings == null ? null : bindings.remove(bindingKey);
        if (binding == null) return;
        destroy(player, binding);
        forget(inside.get(player.getUniqueId()), binding);
    }

    /** A display that is gone can be walked into again once it is back. */
    private void forget(Set<String> in, Binding binding) {
        if (in == null) return;
        for (Shown shown : binding.shown) in.remove(shown.touchKey());
    }

    private void destroy(Player player, Binding binding) {
        User user = user(player);
        if (user == null || binding.shown.isEmpty()) return;
        int[] ids = binding.shown.stream()
                .flatMapToInt(s -> s.hitboxId() == 0 ? IntStream.of(s.entityId()) : IntStream.of(s.entityId(), s.hitboxId()))
                .toArray();
        send(user, new WrapperPlayServerDestroyEntities(ids));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Binding binding(Player player, String bindingKey) {
        Map<String, Binding> bindings = active.get(player.getUniqueId());
        return bindings == null ? null : bindings.get(bindingKey);
    }

    private User user(Player player) {
        var api = PacketEvents.getAPI();
        return api == null ? null : api.getPlayerManager().getUser(player);
    }

    private void send(User user, PacketWrapper<?> packet) {
        try {
            user.sendPacket(packet);
        } catch (RuntimeException e) {
            WoSSystems.getInstance().getLogger().warning("[Displays] A packet could not be sent: " + e);
        }
    }

    private void warnOnce(String display, String problem) {
        if (warned.add(display + " " + problem)) {
            WoSSystems.getInstance().getLogger().warning("[Displays] Display " + display + " " + problem);
        }
    }

    private static Vector3f vector(Vec3 v) {
        return new Vector3f((float) v.x(), (float) v.y(), (float) v.z());
    }

    private static Quaternion4f quaternion(Quat q) {
        return new Quaternion4f(q.x(), q.y(), q.z(), q.w());
    }
}
