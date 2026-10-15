package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSessions;
import me.hektortm.woSSystems.systems.quests.QuestHooks;
import me.hektortm.woSSystems.utils.model.Cooldown;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.hektortm.woSSystems.player.ApiWriter.body;
import static me.hektortm.woSSystems.player.ApiWriter.seg;

/**
 * Cooldowns: definitions from {@code /v1/content/cooldowns}; each player's running
 * cooldowns live in their {@link PlayerSession}.
 * <ul>
 *   <li><b>Global cooldowns</b> — scoped to a player and a cooldown id.</li>
 *   <li><b>Local cooldowns</b> — additionally scoped to an {@link InteractionKey},
 *       so the same cooldown can run independently per location or NPC.</li>
 * </ul>
 * Cooldowns that ran out while the player was offline are dropped silently when
 * their session loads (end interactions only fire for online players).
 */
public class CooldownDAO {
    /** A cooldown that has just run out and been removed. */
    public record Expired(UUID player, String cooldownId) {}

    private final ContentStore<Cooldown> store;
    private final PlayerSessions sessions;
    private final ApiWriter writer;

    public CooldownDAO(ApiServices s) {
        this.sessions = s.sessions();
        this.writer = s.writer();
        this.store = s.content().register(new ContentStore<>("cooldowns", "Cooldown",
                ApiSource.flat(s.api(), "/v1/content/cooldowns", "id", j -> new Cooldown(
                        Json.str(j, "id"), Json.lng(j, "duration", 0),
                        Json.str(j, "start_interaction"), Json.str(j, "end_interaction")), s.log())));
        sessions.onLoad(session -> {
            collectExpired(session, session.cooldowns, false);
            collectExpired(session, session.localCooldowns, true);
        });
    }

    /** The cooldown definition, or {@code null} if no such cooldown is defined. */
    public Cooldown getCooldown(String id) {
        return store.get(id);
    }

    /** The cooldown's duration in seconds; {@code 0} if not defined. */
    public long getCooldownDuration(String id) {
        Cooldown c = store.get(id);
        return c == null ? 0 : c.getDuration();
    }

    // ─── Start / remove ─────────────────────────────────────────────────────────

    /** Starts (or restarts) a global cooldown now. */
    public void giveCooldown(OfflinePlayer p, String id) {
        Instant now = Instant.now();
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s != null) s.cooldowns.put(id, now);
        writer.put(path(p.getUniqueId(), id), body("started_at", now.toString()));
        QuestHooks.report(p.getUniqueId(), "cooldown", "started", Map.of("cooldown", id));
    }

    /** Starts (or restarts) a cooldown scoped to one location/NPC now. */
    public void giveLocalCooldown(OfflinePlayer p, String id, InteractionKey key) {
        Instant now = Instant.now();
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s != null) s.localCooldowns.put(PlayerSession.localCooldownKey(id, key.getKey()), now);
        writer.put(localPath(p.getUniqueId(), id, key.getKey()), body("started_at", now.toString()));
        QuestHooks.report(p.getUniqueId(), "cooldown", "started", Map.of("cooldown", id));
    }

    public void removeCooldown(OfflinePlayer p, String id) {
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s != null) s.cooldowns.remove(id);
        writer.delete(path(p.getUniqueId(), id));
    }

    public void removeLocalCooldown(OfflinePlayer p, String id, InteractionKey key) {
        PlayerSession s = sessions.get(p.getUniqueId());
        if (s != null) s.localCooldowns.remove(PlayerSession.localCooldownKey(id, key.getKey()));
        writer.delete(localPath(p.getUniqueId(), id, key.getKey()));
    }

    // ─── Queries ────────────────────────────────────────────────────────────────

    public boolean isCooldownActive(OfflinePlayer p, String id) {
        Long remaining = getRemainingSeconds(p, id);
        return remaining != null;
    }

    public boolean isLocalCooldownActive(OfflinePlayer p, String id, InteractionKey key) {
        PlayerSession s = sessions.getOrFetch(p.getUniqueId());
        Instant start = s == null ? null : s.localCooldowns.get(PlayerSession.localCooldownKey(id, key.getKey()));
        return start != null && remainingSeconds(start, id) > 0;
    }

    /** Seconds left on the player's cooldown at one location / NPC, or {@code null} if it is not running. */
    public Long getRemainingLocalSeconds(OfflinePlayer p, String id, InteractionKey key) {
        PlayerSession s = sessions.getOrFetch(p.getUniqueId());
        Instant start = s == null ? null : s.localCooldowns.get(PlayerSession.localCooldownKey(id, key.getKey()));
        if (start == null) return null;
        long remaining = remainingSeconds(start, id);
        return remaining > 0 ? remaining : null;
    }

    /** Seconds left on the player's global cooldown, or {@code null} if it is not running. */
    public Long getRemainingSeconds(OfflinePlayer p, String id) {
        PlayerSession s = sessions.getOrFetch(p.getUniqueId());
        Instant start = s == null ? null : s.cooldowns.get(id);
        if (start == null) return null;
        long remaining = remainingSeconds(start, id);
        return remaining > 0 ? remaining : null;
    }

    /**
     * Removes every run-out cooldown of the online players and returns them, so
     * the caller can fire their end interactions. Pure in-memory scan.
     */
    public List<Expired> expireDue(Iterable<? extends Player> online) {
        List<Expired> out = new ArrayList<>();
        for (Player p : online) {
            PlayerSession s = sessions.get(p.getUniqueId());
            if (s == null) continue;
            out.addAll(collectExpired(s, s.cooldowns, false));
            out.addAll(collectExpired(s, s.localCooldowns, true));
        }
        return out;
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private List<Expired> collectExpired(PlayerSession s, Map<String, Instant> running, boolean local) {
        List<Expired> out = new ArrayList<>();
        for (Iterator<Map.Entry<String, Instant>> it = running.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Instant> e = it.next();
            String id = local ? e.getKey().substring(0, e.getKey().indexOf('\u0000')) : e.getKey();
            if (remainingSeconds(e.getValue(), id) > 0) continue;
            it.remove();
            if (local) {
                writer.delete(localPath(s.uuid(), id, e.getKey().substring(id.length() + 1)));
            } else {
                writer.delete(path(s.uuid(), id));
            }
            out.add(new Expired(s.uuid(), id));
        }
        return out;
    }

    private long remainingSeconds(Instant start, String id) {
        long elapsed = (System.currentTimeMillis() - start.toEpochMilli()) / 1000;
        return getCooldownDuration(id) - elapsed;
    }

    private static String path(UUID uuid, String id) {
        return "/v1/players/" + uuid + "/cooldowns/" + seg(id);
    }

    private static String localPath(UUID uuid, String id, String key) {
        return path(uuid, id) + "/local/" + seg(key);
    }
}
