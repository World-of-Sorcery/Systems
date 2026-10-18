package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSessions;
import me.hektortm.woSSystems.utils.Operations;
import me.hektortm.woSSystems.utils.model.GlobalStat;
import me.hektortm.woSSystems.utils.model.Stat;
import me.hektortm.wosCore.api.ApiException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static me.hektortm.woSSystems.player.ApiWriter.body;
import static me.hektortm.woSSystems.player.ApiWriter.seg;

/**
 * Stats: definitions ({@code /v1/content/stats}, {@code /globalstats}), each
 * player's stat values (in their {@link PlayerSession}) and the server-wide
 * global stat values. Player stat values are computed in memory and the
 * resulting absolute value is persisted, so a replayed write can never
 * double-count. Global stats are shared by every server of a realm: a give or
 * take is sent as a delta (see {@link #modifyGlobalStatValue}).
 */
public class StatsDAO {
    private final ContentStore<Stat> statDefinitions;
    private final ContentStore<GlobalStat> globalStatDefinitions;
    private final ConcurrentHashMap<String, Long> globalValues = new ConcurrentHashMap<>();
    private final PlayerSessions sessions;
    private final ApiWriter writer;
    private final Logger log;

    public StatsDAO(ApiServices s) {
        this.sessions = s.sessions();
        this.writer = s.writer();
        this.log = s.log();
        this.statDefinitions = s.content().register(new ContentStore<>("stats", "Stat",
                ApiSource.flat(s.api(), "/v1/content/stats", "id", j -> new Stat(
                        Json.str(j, "id"), Json.lng(j, "max", 0), Json.bool(j, "capped", false)), log)));
        this.globalStatDefinitions = s.content().register(new ContentStore<GlobalStat>("globalstats", "Global Stat",
                ApiSource.flat(s.api(), "/v1/content/globalstats", "id", j -> new GlobalStat(
                        Json.str(j, "id"), Json.lng(j, "value", 0), Json.lng(j, "max", 0), Json.bool(j, "capped", false)), log))
                // The API's value (this server's realm) is the truth: every (re)load takes it over.
                // Between loads each give / take stores the value its delta answer returns.
                .onChange(store -> store.all().forEach(g -> globalValues.put(g.getId(), g.getValue()))));
    }

    // ─── Player stats ───────────────────────────────────────────────────────────

    public long getPlayerStatValue(UUID uuid, String statId) {
        PlayerSession s = sessions.getOrFetch(uuid);
        return s == null ? 0 : s.stats.getOrDefault(statId, 0L);
    }

    public boolean isStatLimitReached(UUID uuid, String statId) {
        Stat stat = statDefinitions.get(statId);
        if (stat == null || !stat.getCapped()) return false;
        return getPlayerStatValue(uuid, statId) >= stat.getMax();
    }

    public void modifyPlayerStat(UUID uuid, String statId, long amount, Operations operation) {
        Stat stat = statDefinitions.get(statId);
        long max = stat != null && stat.getCapped() ? stat.getMax() : Long.MAX_VALUE;
        PlayerSession s = sessions.getOrFetch(uuid);
        if (s == null) {
            log.warning("[Stats] cannot modify " + statId + " for unknown player " + uuid);
            return;
        }
        long value = s.stats.compute(statId, (k, current) -> apply(current == null ? 0 : current, amount, operation, max));
        writer.put("/v1/players/" + uuid + "/stats/" + seg(statId), body("value", value));
        me.hektortm.woSSystems.systems.quests.QuestHooks.report(uuid, "stat", "reached", java.util.Map.of("stat", statId, "value", String.valueOf(value)));
    }

    /** Clears every {@code daily_*} stat and unlockable for all players. */
    public void resetDailyStats() {
        for (PlayerSession s : onlineSessions()) s.stats.keySet().removeIf(k -> k.startsWith("daily_"));
        // One API call resets stats AND unlockables of this server's realm, so only this
        // method posts; UnlockableDAO.resetDailyUnlockables just clears its cache.
        if (me.hektortm.woSSystems.JobRules.runsScheduled()) writer.post("/v1/server/daily-reset", null);
    }

    // ─── Global stats ───────────────────────────────────────────────────────────

    public long getGlobalStatValue(String statId) {
        return globalValues.getOrDefault(statId, 0L);
    }

    public boolean isGlobalStatLimitReached(String statId) {
        GlobalStat stat = globalStatDefinitions.get(statId);
        if (stat == null || !stat.getCapped()) return false;
        return getGlobalStatValue(statId) >= stat.getMax();
    }

    /**
     * Give and take go to the API as a delta ({@code POST …/delta}, added atomically in this
     * server's realm), so the servers of one realm never overwrite each other's changes; the
     * answer is the realm's new value, which replaces the cached one. Set and reset are
     * explicit staff actions and stay an absolute PUT. The cache is updated at once as well,
     * so a read right after the change sees it.
     */
    public void modifyGlobalStatValue(String statId, long amount, Operations operation) {
        GlobalStat stat = globalStatDefinitions.get(statId);
        long max = stat != null && stat.getCapped() ? stat.getMax() : Long.MAX_VALUE;
        long value = globalValues.compute(statId, (k, current) -> apply(current == null ? 0 : current, amount, operation, max));
        Long delta = deltaOf(operation, amount);
        if (delta == null) {
            writer.put("/v1/server/globalstats/" + seg(statId), body("value", value));
        } else {
            writer.post("/v1/server/globalstats/" + seg(statId) + "/delta", body("delta", delta),
                    answer -> storeAnswer(globalValues, statId, answer));
        }
    }

    /** The delta a give / take sends; null for set and reset (absolute). */
    static Long deltaOf(Operations operation, long amount) {
        return switch (operation) {
            case GIVE -> amount;
            case TAKE -> -amount;
            case SET, RESET -> null;
        };
    }

    /** Puts the realm's value from a delta answer ({@code {"value": n}}) into the cache; anything else is ignored. */
    static void storeAnswer(Map<String, Long> cache, String statId, com.google.gson.JsonElement answer) {
        if (answer == null || !answer.isJsonObject()) return;
        com.google.gson.JsonElement v = answer.getAsJsonObject().get("value");
        if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) cache.put(statId, v.getAsLong());
    }

    // ─── Definitions ────────────────────────────────────────────────────────────

    public Map<String, Stat> getAllStats() {
        return statDefinitions.asMap();
    }

    public Map<String, GlobalStat> getAllGlobalStats() {
        return globalStatDefinitions.asMap();
    }

    /** Force-refresh stat definitions from the API. Blocking — call off the main thread. */
    public void reloadStatDefinitions() {
        try {
            statDefinitions.preload();
            globalStatDefinitions.preload();
        } catch (ApiException e) {
            log.severe("[Stats] reloading definitions failed: " + e.getMessage());
        }
    }

    private static long apply(long current, long amount, Operations operation, long max) {
        return switch (operation) {
            case GIVE -> Math.min(current + amount, max);
            case TAKE -> Math.max(0L, current - amount);
            case SET -> amount;
            case RESET -> 0L;
        };
    }

    private Iterable<PlayerSession> onlineSessions() {
        return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(p -> sessions.get(p.getUniqueId()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
