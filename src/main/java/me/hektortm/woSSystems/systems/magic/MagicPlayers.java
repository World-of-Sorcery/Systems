package me.hektortm.woSSystems.systems.magic;

import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSessions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every online player's {@link MagicData}: built from the session at load (start values
 * from the settings for a player without magic data), saved with {@code PUT
 * /v1/players/{uuid}/magic} when it changed — at most every 30 s, and when the player
 * leaves. Learning / forgetting is written at once.
 */
public final class MagicPlayers {

    private static final long SAVE_EVERY_TICKS = 30 * 20L;

    private final ApiWriter writer;
    private final MagicStores stores;
    private final Map<UUID, MagicData> online = new ConcurrentHashMap<>();

    public MagicPlayers(Plugin plugin, PlayerSessions sessions, ApiWriter writer, MagicStores stores) {
        this.writer = writer;
        this.stores = stores;
        sessions.onLoad(this::load);
        sessions.onReset(this::load); // "Reset my test progress": the fresh session replaces the data
        sessions.onLeaving(this::flush);
        sessions.onUnload(s -> online.remove(s.uuid()));
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> online.keySet().forEach(this::flush), SAVE_EVERY_TICKS, SAVE_EVERY_TICKS);
    }

    private void load(PlayerSession s) {
        online.put(s.uuid(), MagicData.fromSession(s.magic, s.spells, s.spellProgress, stores.settings()));
    }

    /** The player's magic, or null while their session isn't loaded. */
    @Nullable
    public MagicData data(UUID uuid) {
        return online.get(uuid);
    }

    @Nullable
    public MagicData data(Player p) {
        return data(p.getUniqueId());
    }

    /** Learns a spell (and puts it in the first free ring slot); false when already known or offline. */
    public boolean learn(UUID uuid, String spellId) {
        MagicData d = online.get(uuid);
        if (d == null || !d.learn(spellId)) return false;
        writer.put("/v1/players/" + ApiWriter.seg(uuid) + "/spells/" + ApiWriter.seg(spellId), Map.of());
        flush(uuid);
        return true;
    }

    public boolean forget(UUID uuid, String spellId) {
        MagicData d = online.get(uuid);
        if (d == null || !d.forget(spellId)) return false;
        writer.delete("/v1/players/" + ApiWriter.seg(uuid) + "/spells/" + ApiWriter.seg(spellId));
        flush(uuid);
        return true;
    }

    /** Saves the player's magic when it changed since the last save. */
    public void flush(UUID uuid) {
        MagicData d = online.get(uuid);
        if (d != null && d.takeDirty()) writer.put("/v1/players/" + ApiWriter.seg(uuid) + "/magic", d.body());
    }

    public Iterable<UUID> onlineIds() {
        return online.keySet();
    }
}
