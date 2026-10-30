package me.hektortm.woSSystems.systems.magic.cast;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
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
import me.hektortm.woSSystems.systems.magic.WandRules;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Firework;
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
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The looks-only effects (more-nodes spec §4.4): fireworks that never hurt, lightning
 * without damage, displays sent as packets, and fake blocks only the nearby players see.
 * Nothing here changes the world.
 */
public final class SpellVisuals implements Listener {

    private static final double DISPLAY_VIEW = 64, FAKE_VIEW = 48;
    private static final int STEP = 2;
    // Metadata indexes of display entities (Minecraft 1.21.x), as in interactions/DisplayManager.
    private static final int META_FLAGS = 0, META_INTERPOLATION_DELAY = 8, META_INTERPOLATION_DURATION = 9, META_TELEPORT_DURATION = 10,
            META_SCALE = 12, META_LEFT_ROTATION = 13, META_BILLBOARD = 15, META_GLOW_COLOR = 22, META_CONTENT = 23;
    private static final byte FLAG_GLOWING = 0x40;

    private final Plugin plugin;
    private final SpellLitter litter;

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

    // ── display ──

    /** What a display shows and how. {@code item} / {@code block} / {@code text} by {@code kind}; text already filled in. */
    public record DisplayLook(String kind, String item, String block, String text, double scale, double spin, String billboard,
                              boolean glow, String glowColor, double seconds, double up, String grow) { }

    /**
     * A display entity sent to the players within 64 blocks of {@code at} for its seconds,
     * spinning / growing as set; {@code follow} (null: stays) gives where to keep it each step.
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
        if ("block".equals(look.kind())) first.add(new EntityData<>(META_CONTENT, EntityDataTypes.BLOCK_STATE, (Integer) content));
        else if ("text".equals(look.kind())) first.add(new EntityData<>(META_CONTENT, EntityDataTypes.ADV_COMPONENT, (net.kyori.adventure.text.Component) content));
        else first.add(new EntityData<>(META_CONTENT, EntityDataTypes.ITEMSTACK, (com.github.retrooper.packetevents.protocol.item.ItemStack) content));
        first.addAll(pose(look, 0, 0));
        for (User u : viewers) {
            send(u, new WrapperPlayServerSpawnEntity(id, Optional.of(UUID.randomUUID()), type,
                    new Vector3d(start.getX(), start.getY(), start.getZ()), 0f, 0f, 0f, 0, Optional.empty()));
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
                    for (User u : viewers) send(u, new WrapperPlayServerEntityTeleport(id, new Vector3d(to.getX(), to.getY() + look.up(), to.getZ()), 0f, 0f, false));
                }
            }
        }.runTaskTimer(plugin, STEP, STEP);
    }

    /** Scale and spin at {@code t} seconds, {@code life} (0–1) through, eased by the client over one step. */
    private static List<EntityData<?>> pose(DisplayLook look, double t, double life) {
        float s = (float) (look.scale() * growScale(look.grow(), life));
        double half = Math.toRadians(look.spin() * t) / 2;
        return List.of(
                new EntityData<>(META_INTERPOLATION_DELAY, EntityDataTypes.INT, 0),
                new EntityData<>(META_INTERPOLATION_DURATION, EntityDataTypes.INT, STEP),
                new EntityData<>(META_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(s, s, s)),
                new EntityData<>(META_LEFT_ROTATION, EntityDataTypes.QUATERNION, new Quaternion4f(0, (float) Math.sin(half), 0, (float) Math.cos(half))));
    }

    @Nullable
    private Object content(DisplayLook look) {
        try {
            switch (look.kind()) {
                case "block" -> {
                    Material m = Material.matchMaterial(look.block());
                    return m == null || !m.isBlock() ? null : SpigotConversionUtil.fromBukkitBlockData(m.createBlockData()).getGlobalId();
                }
                case "text" -> {
                    return LegacyComponentSerializer.legacyAmpersand().deserialize(look.text());
                }
                default -> {
                    ItemStack item = look.item().isBlank() ? null : litter.stack(look.item());
                    return item == null ? null : SpigotConversionUtil.fromBukkitItemStack(item);
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[Magic] display can't be shown: " + e.getMessage());
            return null;
        }
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
        return switch (mode) {
            case "vertical" -> 1;
            case "center" -> 3;
            default -> 0;
        };
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
