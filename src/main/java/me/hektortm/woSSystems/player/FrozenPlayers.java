package me.hektortm.woSSystems.player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Players whose data this server must no longer write: another server took them over
 * (the session hand-off, see {@link Handoff}). {@link ApiWriter} asks {@link #drops}
 * before queueing a write; a write for a frozen player is dropped with one log line.
 *
 * <p>A write belongs to a player when its path is {@code /v1/players/<uuid>/...}
 * (stats, unlockables, cosmetics, cooldowns, quests, profile, nickname). Economy
 * transactions and transfers ({@code /v1/economy/...}) always pass: they are idempotent
 * deltas that cannot overwrite anything, and dropping them would lose currency. Writes
 * queued <b>before</b> the freeze are not affected: they are flushed by
 * {@code AsyncWriteQueue.barrier()} before the lease is released.</p>
 *
 * <p>The freeze only lasts while the player is released but still connected here: it ends
 * when they leave this server ({@link Handoff#playerLeft}) or take the lease here again.</p>
 */
public final class FrozenPlayers {
    private static final Pattern PLAYER_PATH = Pattern.compile("^/v1/players/([0-9a-fA-F-]{36})(?:[/?].*)?$");

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Logger log;

    public FrozenPlayers(Logger log) {
        this.log = log;
    }

    public void freeze(UUID uuid) {
        if (frozen.add(uuid)) log.info("[Handoff] " + uuid + " is frozen here: further writes for this player are dropped");
    }

    public void unfreeze(UUID uuid) {
        frozen.remove(uuid);
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    /** True (and logs one line) when this write is for a frozen player and must not be sent. */
    public boolean drops(String method, String path, Object body) {
        if (frozen.isEmpty()) return false;
        UUID owner = frozenOwner(path);
        if (owner == null) return false;
        log.warning("[Handoff] dropped write for " + owner + " (held by another server): " + method + " " + path);
        return true;
    }

    private UUID frozenOwner(String path) {
        Matcher m = PLAYER_PATH.matcher(path);
        if (!m.matches()) return null;
        UUID u = parse(m.group(1));
        return u != null && frozen.contains(u) ? u : null;
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
