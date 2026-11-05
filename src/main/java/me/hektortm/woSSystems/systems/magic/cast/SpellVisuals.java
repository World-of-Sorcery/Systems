package me.hektortm.woSSystems.systems.magic.cast;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.hektortm.woSSystems.systems.interactions.DisplayRules;
import me.hektortm.woSSystems.systems.interactions.DisplaySettings.Vec3;
import me.hektortm.woSSystems.systems.magic.WandRules;
import me.hektortm.woSSystems.utils.ColorText;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The looks-only effects (more-nodes spec §4.4): fireworks that never hurt, lightning
 * without damage, displays sent as packets, fake blocks only the nearby players see, and
 * worn items sent as equipment packets (2026-11-03 spec §2). Nothing here changes the world.
 */
public final class SpellVisuals implements Listener {

    private static final double DISPLAY_VIEW = 64, FAKE_VIEW = 48;
    private static final int STEP = 2;
    private static final long WEAR_STEP = 10;
    // Metadata indexes of display entities (Minecraft 1.21.x), as in interactions/DisplayManager.
    private static final int META_FLAGS = 0, META_INTERPOLATION_DELAY = 8, META_INTERPOLATION_DURATION = 9, META_TELEPORT_DURATION = 10,
            META_TRANSLATION = 11, META_SCALE = 12, META_LEFT_ROTATION = 13, META_BILLBOARD = 15, META_BRIGHTNESS = 16, META_VIEW_RANGE = 17,
            META_SHADOW_RADIUS = 18, META_SHADOW_STRENGTH = 19, META_GLOW_COLOR = 22, META_CONTENT = 23,
            META_ITEM_DISPLAY = 24, META_LINE_WIDTH = 24, META_BACKGROUND = 25, META_TEXT_OPACITY = 26, META_TEXT_FLAGS = 27;
    private static final byte FLAG_GLOWING = 0x40;
    private static final byte TEXT_SHADOW = 0x01, TEXT_SEE_THROUGH = 0x02, TEXT_DEFAULT_BACKGROUND = 0x04, TEXT_ALIGN_LEFT = 0x08, TEXT_ALIGN_RIGHT = 0x10;

    private final Plugin plugin;
    private final SpellLitter litter;
    /** The running Wear of each entity's slot ("uuid:slot"): a newer one replaces it. */
    private final Map<String, BukkitTask> worn = new HashMap<>();

    public SpellVisuals(Plugin plugin, SpellLitter litter) {
        this.plugin = plugin;
        this.litter = litter;
    }

    // ── firework, lightning ──

    /** A firework at {@code at} that bursts at once ({@code height} 0) or after rising a little; tagged so it hurts no one. */
    public void firework(Location at, String color, String fade, String style, boolean flicker, boolean trail, double height) {
        World w = at.getWorld();
        if (w == null) return;
        int rgb = rgb(color);
        FireworkEffect.Builder b = FireworkEffect.builder().withColor(Color.fromRGB(rgb < 0 ? 0xFFFFFF : rgb))
                .with(switch (style) {
                    case "large_ball" -> FireworkEffect.Type.BALL_LARGE;
                    case "star" -> FireworkEffect.Type.STAR;
                    case "burst" -> FireworkEffect.Type.BURST;
                    case "creeper" -> FireworkEffect.Type.CREEPER;
                    default -> FireworkEffect.Type.BALL;
                }).flicker(flicker).trail(trail);
        if (rgb(fade) >= 0) b.withFade(Color.fromRGB(rgb(fade)));
        Firework fw = w.spawn(at, Firework.class, f -> {
            f.getPersistentDataContainer().set(SpellLitter.KEY, PersistentDataType.BYTE, (byte) 1);
            f.setPersistent(false);
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(b.build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
        });
        if (height <= 0) fw.detonate();
        else fw.setTicksToDetonate((int) Math.max(1, Math.round(height * 6)));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFireworkHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework && SpellLitter.tagged(e.getDamager())) e.setCancelled(true);
    }

    /** A strike that only looks and sounds like one. */
    public void lightning(Location at) {
        if (at.getWorld() != null) at.getWorld().strikeLightningEffect(at);
    }

    // ── song ──

    /**
     * Song (2026-11-03): each note at its tick, where {@code follow} says (null: at {@code at}; the
     * last place once it gives none), on the Jukebox / Note Blocks volume. Heard by everyone near,
     * or only by the caster when {@code casterOnly}. Ends with its last note, or when the caster logs out.
     */
    public void song(Location at, List<Song.Note> notes, @Nullable Supplier<Location> follow, Player caster, boolean casterOnly) {
        if (notes.isEmpty() || at.getWorld() == null) return;
        new BukkitRunnable() {
            int tick;
            int next;
            Location where = at.clone();

            @Override
            public void run() {
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }
                Location to = follow == null ? null : follow.get();
                if (to != null) where = to;
                while (next < notes.size() && notes.get(next).tick() <= tick) play(where, notes.get(next++), casterOnly ? caster : null);
                if (next >= notes.size()) cancel();
                tick++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private static void play(Location at, Song.Note n, @Nullable Player only) {
        NamespacedKey key = SpellFx.key(n.sound());
        if (key == null || at.getWorld() == null) return;
        if (only != null) only.playSound(at, key.toString(), SoundCategory.RECORDS, n.volume(), n.pitch());
        else at.getWorld().playSound(at, key.toString(), SoundCategory.RECORDS, n.volume(), n.pitch());
    }

    // ── display ──

    /**
     * What a display shows and how (2026-11-03: all of Minecraft's display settings). {@code item} (+ {@code model},
     * {@code itemTransform}) / {@code block} (with states) / {@code text} by {@code kind}; text already filled in.
     * {@code stretch} multiplies {@code scale} per axis; {@code tilt} is degrees around x, y, z; {@code offset} the
     * translation; {@code brightness} packed, or -1 for the world's light; colours are #RRGGBB or "".
     */
    public record DisplayLook(String kind, String item, String model, String itemTransform, String block, String text, TextStyle textStyle,
                              double scale, Vec3 stretch, Vec3 tilt, Vec3 offset, double up, double spin, char spinAxis, String billboard, String grow,
                              boolean glow, String glowColor, int brightness, float viewRange, float shadowRadius, float shadowStrength,
                              double seconds) { }

    /** A text display's look: line width (pixels), background ARGB, text opacity, style flags. */
    public record TextStyle(int lineWidth, int background, int opacity, byte flags) {
        static final TextStyle PLAIN = new TextStyle(200, 0x40000000, 255, (byte) 0);
    }

    /** The style flags byte of a text display. */
    static byte textFlags(boolean shadow, boolean seeThrough, boolean defaultBackground, String align) {
        int f = (shadow ? TEXT_SHADOW : 0) | (seeThrough ? TEXT_SEE_THROUGH : 0) | (defaultBackground ? TEXT_DEFAULT_BACKGROUND : 0);
        if ("left".equals(align)) f |= TEXT_ALIGN_LEFT;
        else if ("right".equals(align)) f |= TEXT_ALIGN_RIGHT;
        return (byte) f;
    }

    /** A text background as ARGB: {@code hex} ("": black, Minecraft's default) at {@code opacity} 0–255. */
    static int background(String hex, int opacity) {
        int rgb = Math.max(0, rgb(hex));
        return Math.max(0, Math.min(255, opacity)) << 24 | rgb;
    }

    /** Fixed light (0–15 each) packed for the client, or -1: lit by the world. */
    static int brightness(String mode, int block, int sky) {
        return "fixed".equals(mode) ? DisplayRules.packBrightness(Math.max(0, Math.min(15, block)), Math.max(0, Math.min(15, sky))) : -1;
    }

    /** The display's rotation at {@code t} seconds: its tilt, then its spin around the chosen axis. */
    static DisplayRules.Quat rotation(DisplayLook look, double t) {
        DisplayRules.Quat tilt = DisplayRules.fromDegrees(look.tilt());
        return look.spin() == 0 ? tilt : DisplayRules.multiply(DisplayRules.turn(look.spinAxis(), look.spin() * t), tilt);
    }

    /**
     * A display entity sent to the players within 64 blocks of {@code at} for its seconds,
     * spinning / growing as set; {@code follow} (null: stays) gives where to keep it each step,
     * and which way it turns (its yaw).
     */
    public void display(Location at, DisplayLook look, @Nullable Supplier<Location> follow) {
        World w = at.getWorld();
        if (w == null) return;
        Object content = content(look);
        if (content == null) return;
        EntityType type = switch (look.kind()) {
            case "block" -> EntityTypes.BLOCK_DISPLAY;
            case "text" -> EntityTypes.TEXT_DISPLAY;
            default -> EntityTypes.ITEM_DISPLAY;
        };
        List<User> viewers = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > DISPLAY_VIEW * DISPLAY_VIEW) continue;
            User u = user(p);
            if (u != null) viewers.add(u);
        }
        if (viewers.isEmpty()) return;
        int id = Bukkit.getUnsafe().nextEntityId();
        Location start = at.clone().add(0, look.up(), 0);
        List<EntityData<?>> first = new ArrayList<>();
        if (look.glow() && !"text".equals(look.kind())) first.add(new EntityData<>(META_FLAGS, EntityDataTypes.BYTE, FLAG_GLOWING));
        if (look.glow() && rgb(look.glowColor()) >= 0) first.add(new EntityData<>(META_GLOW_COLOR, EntityDataTypes.INT, rgb(look.glowColor())));
        first.add(new EntityData<>(META_BILLBOARD, EntityDataTypes.BYTE, billboard(look.billboard())));
        first.add(new EntityData<>(META_TELEPORT_DURATION, EntityDataTypes.INT, STEP));
        Vec3 offset = look.offset();
        first.add(new EntityData<>(META_TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f((float) offset.x(), (float) offset.y(), (float) offset.z())));
        if (look.brightness() >= 0) first.add(new EntityData<>(META_BRIGHTNESS, EntityDataTypes.INT, look.brightness()));
        first.add(new EntityData<>(META_VIEW_RANGE, EntityDataTypes.FLOAT, look.viewRange()));
        first.add(new EntityData<>(META_SHADOW_RADIUS, EntityDataTypes.FLOAT, look.shadowRadius()));
        first.add(new EntityData<>(META_SHADOW_STRENGTH, EntityDataTypes.FLOAT, look.shadowStrength()));
        if ("block".equals(look.kind())) {
            first.add(new EntityData<>(META_CONTENT, EntityDataTypes.BLOCK_STATE, (Integer) content));
        } else if ("text".equals(look.kind())) {
            TextStyle style = look.textStyle();
            first.add(new EntityData<>(META_CONTENT, EntityDataTypes.ADV_COMPONENT, (net.kyori.adventure.text.Component) content));
            first.add(new EntityData<>(META_LINE_WIDTH, EntityDataTypes.INT, style.lineWidth()));
            first.add(new EntityData<>(META_BACKGROUND, EntityDataTypes.INT, style.background()));
            first.add(new EntityData<>(META_TEXT_OPACITY, EntityDataTypes.BYTE, (byte) style.opacity()));
            first.add(new EntityData<>(META_TEXT_FLAGS, EntityDataTypes.BYTE, style.flags()));
        } else {
            first.add(new EntityData<>(META_CONTENT, EntityDataTypes.ITEMSTACK, (com.github.retrooper.packetevents.protocol.item.ItemStack) content));
            first.add(new EntityData<>(META_ITEM_DISPLAY, EntityDataTypes.BYTE, DisplayRules.itemDisplay(look.itemTransform())));
        }
        first.addAll(pose(look, 0, 0));
        for (User u : viewers) {
            send(u, new WrapperPlayServerSpawnEntity(id, Optional.of(UUID.randomUUID()), type,
                    new Vector3d(start.getX(), start.getY(), start.getZ()), 0f, follow == null ? 0f : start.getYaw(), 0f, 0, Optional.empty()));
            send(u, new WrapperPlayServerEntityMetadata(id, first));
        }
        int life = (int) Math.max(1, Math.round(look.seconds() * 20));
        new BukkitRunnable() {
            int age;

            @Override
            public void run() {
                age += STEP;
                if (age >= life) {
                    cancel();
                    for (User u : viewers) send(u, new WrapperPlayServerDestroyEntities(id));
                    return;
                }
                if (look.spin() != 0 || !"none".equals(look.grow())) {
                    List<EntityData<?>> data = pose(look, age / 20.0, (double) age / life);
                    for (User u : viewers) send(u, new WrapperPlayServerEntityMetadata(id, data));
                }
                Location to = follow == null ? null : follow.get();
                if (to != null && to.getWorld() == w) {
                    for (User u : viewers) send(u, new WrapperPlayServerEntityTeleport(id, new Vector3d(to.getX(), to.getY() + look.up(), to.getZ()), to.getYaw(), 0f, false));
                }
            }
        }.runTaskTimer(plugin, STEP, STEP);
    }

    /** Scale (stretched per axis) and rotation (tilt, then spin) at {@code t} seconds, {@code life} (0–1) through, eased by the client over one step. */
    private static List<EntityData<?>> pose(DisplayLook look, double t, double life) {
        double s = look.scale() * growScale(look.grow(), life);
        Vec3 k = look.stretch();
        DisplayRules.Quat q = rotation(look, t);
        return List.of(
                new EntityData<>(META_INTERPOLATION_DELAY, EntityDataTypes.INT, 0),
                new EntityData<>(META_INTERPOLATION_DURATION, EntityDataTypes.INT, STEP),
                new EntityData<>(META_SCALE, EntityDataTypes.VECTOR3F, new Vector3f((float) (s * k.x()), (float) (s * k.y()), (float) (s * k.z()))),
                new EntityData<>(META_LEFT_ROTATION, EntityDataTypes.QUATERNION, new Quaternion4f(q.x(), q.y(), q.z(), q.w())));
    }

    @Nullable
    private Object content(DisplayLook look) {
        try {
            switch (look.kind()) {
                case "block" -> {
                    BlockData data = SpellFx.blockData(look.block());
                    return data == null ? null : SpigotConversionUtil.fromBukkitBlockData(data).getGlobalId();
                }
                case "text" -> {
                    return ColorText.component(look.text());
                }
                default -> {
                    ItemStack item = litter.modelled(look.item(), look.model());
                    return item == null ? null : SpigotConversionUtil.fromBukkitItemStack(item);
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[Magic] display can't be shown: " + e.getMessage());
            return null;
        }
    }

    // ── wear ──

    /**
     * Wear: {@code item} shown in {@code slot} of {@code e} to the players within 64 blocks
     * (the wearer too when {@code selfView}) for {@code seconds}, sent again every half second
     * so newcomers and real equipment changes don't drop it; then everyone who saw it gets
     * the real equipment again. Ends early when the entity dies or leaves.
     */
    public void wear(LivingEntity e, String slot, ItemStack item, double seconds, boolean selfView) {
        String key = e.getUniqueId() + ":" + slot;
        BukkitTask old = worn.remove(key);
        if (old != null) old.cancel();
        EquipmentSlot packetSlot = packetSlot(slot);
        org.bukkit.inventory.EquipmentSlot realSlot = realSlot(slot);
        var shown = SpigotConversionUtil.fromBukkitItemStack(item);
        int life = (int) Math.max(1, Math.round(seconds * 20));
        Set<UUID> sawIt = new HashSet<>();
        BukkitTask task = new BukkitRunnable() {
            int age;

            @Override
            public void run() {
                boolean over = age >= life || !e.isValid() || e.isDead();
                age += WEAR_STEP;
                if (over) {
                    cancel();
                    BukkitTask mine = worn.get(key);
                    if (mine != null && mine.getTaskId() == getTaskId()) worn.remove(key);
                    var real = e.getEquipment() == null ? null : SpigotConversionUtil.fromBukkitItemStack(e.getEquipment().getItem(realSlot));
                    for (UUID id : sawIt) {
                        Player p = plugin.getServer().getPlayer(id);
                        User u = p == null ? null : user(p);
                        if (u != null && real != null) send(u, new WrapperPlayServerEntityEquipment(e.getEntityId(), List.of(new Equipment(packetSlot, real))));
                        if (p == e) p.updateInventory(); // its own armour slots come back too
                    }
                    return;
                }
                for (Player p : e.getWorld().getPlayers()) {
                    if (p == e ? !selfView : p.getLocation().distanceSquared(e.getLocation()) > DISPLAY_VIEW * DISPLAY_VIEW) continue;
                    User u = user(p);
                    if (u == null) continue;
                    send(u, new WrapperPlayServerEntityEquipment(e.getEntityId(), List.of(new Equipment(packetSlot, shown))));
                    sawIt.add(p.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 0L, WEAR_STEP);
        worn.put(key, task);
    }

    private static EquipmentSlot packetSlot(String slot) {
        return switch (slot) {
            case "chest" -> EquipmentSlot.CHEST_PLATE;
            case "legs" -> EquipmentSlot.LEGGINGS;
            case "feet" -> EquipmentSlot.BOOTS;
            case "mainhand" -> EquipmentSlot.MAIN_HAND;
            case "offhand" -> EquipmentSlot.OFF_HAND;
            default -> EquipmentSlot.HELMET;
        };
    }

    private static org.bukkit.inventory.EquipmentSlot realSlot(String slot) {
        return switch (slot) {
            case "chest" -> org.bukkit.inventory.EquipmentSlot.CHEST;
            case "legs" -> org.bukkit.inventory.EquipmentSlot.LEGS;
            case "feet" -> org.bukkit.inventory.EquipmentSlot.FEET;
            case "mainhand" -> org.bukkit.inventory.EquipmentSlot.HAND;
            case "offhand" -> org.bukkit.inventory.EquipmentSlot.OFF_HAND;
            default -> org.bukkit.inventory.EquipmentSlot.HEAD;
        };
    }

    // ── fake blocks ──

    /**
     * Shows {@code data} on the given blocks (air ones only) to the players within 48
     * blocks, and the real blocks again after {@code seconds}. A viewer who logs out or
     * changes world gets the real chunks anyway.
     *
     * @return how many blocks were shown
     */
    public int fakeBlocks(Location at, BlockData data, List<int[]> cells, double seconds) {
        World w = at.getWorld();
        if (w == null) return 0;
        Block base = at.getBlock();
        List<BlockState> fakes = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        for (int[] c : cells) {
            Block b = base.getRelative(c[0], c[1], c[2]);
            if (!b.getType().isAir()) continue;
            BlockState s = b.getState();
            s.setBlockData(data);
            fakes.add(s);
            blocks.add(b);
        }
        if (fakes.isEmpty()) return 0;
        List<Player> viewers = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= FAKE_VIEW * FAKE_VIEW) {
                p.sendBlockChanges(fakes);
                viewers.add(p);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<BlockState> real = new ArrayList<>(blocks.size());
            for (Block b : blocks) real.add(b.getState());
            for (Player p : viewers) {
                if (p.isOnline() && p.getWorld() == w) p.sendBlockChanges(real);
            }
        }, Math.max(1, Math.round(seconds * 20)));
        return fakes.size();
    }

    // ── small parts ──

    /** The scale factor of a growing display, {@code life} 0–1 through its time. */
    static double growScale(String grow, double life) {
        return switch (grow) {
            case "out" -> life;
            case "in" -> 1 - life;
            default -> 1;
        };
    }

    /** The billboard byte of a display: fixed 0, vertical 1, horizontal 2, center 3. */
    static byte billboard(String mode) {
        return DisplayRules.billboard(mode);
    }

    /** #RRGGBB as a number; -1 for anything else ("" is none). */
    static int rgb(String hex) {
        return WandRules.isColor(hex) ? WandRules.rgb(hex) : -1;
    }

    @Nullable
    private static User user(Player p) {
        var api = PacketEvents.getAPI();
        return api == null ? null : api.getPlayerManager().getUser(p);
    }

    private void send(User u, PacketWrapper<?> packet) {
        try {
            u.sendPacket(packet);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[Magic] a display packet could not be sent: " + e);
        }
    }
}
