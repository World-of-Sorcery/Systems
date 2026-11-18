package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.PlayerSessions;
import me.hektortm.woSSystems.systems.leaderboards.Board;
import me.hektortm.woSSystems.utils.model.Leaderboard;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static me.hektortm.woSSystems.player.ApiWriter.seg;

/**
 * Leaderboards: definitions from {@code /v1/content/leaderboards}, and each
 * board as this server's realm sees it ({@code /v1/server/leaderboards/<id>})
 * plus the own rank of each player online here ({@code …/ranks}), kept in
 * memory. Placeholders read only that copy, so a hologram never waits on the
 * API; the copy is refreshed off the main thread every 30 seconds and after
 * every change made on this server or announced by another.
 */
public class LeaderboardDAO {
    private final ContentStore<Leaderboard> store;
    private final WosApi api;
    private final PlayerSessions sessions;
    private final Logger log;
    private final Map<String, Board> boards = new ConcurrentHashMap<>();
    /** Board id → online player → own rank (also below the shown ranks). */
    private final Map<String, Map<UUID, Integer>> ranks = new ConcurrentHashMap<>();
    /** Runs a blocking read off the main thread (set at enable; direct in tests). */
    private volatile Executor async = Runnable::run;

    public LeaderboardDAO(ApiServices s) {
        this.api = s.api();
        this.sessions = s.sessions();
        this.log = s.log();
        this.store = s.content().register(new ContentStore<>("leaderboards", "Leaderboard",
                ApiSource.flat(s.api(), "/v1/content/leaderboards", "id", j -> new Leaderboard(
                        Json.str(j, "id"), Json.str(j, "source_type"), Json.str(j, "source_id"),
                        Json.integer(j, "size", 10), Json.str(j, "line_format"), Json.str(j, "empty_line")), s.log())));
    }

    /** Where refreshes run: an async scheduler task at enable. */
    public void useExecutor(Executor async) { this.async = async; }

    public @Nullable Leaderboard get(String id) { return store.get(id); }

    public Map<String, Leaderboard> getAll() { return store.asMap(); }

    /** The board as last read; {@link Board#EMPTY} before the first answer. Never blocks. */
    public Board board(String id) {
        return boards.getOrDefault(id, Board.EMPTY);
    }

    /**
     * The player's own rank, also below the ranks the board shows (read for the
     * players online here); null when they have none or it was not read yet.
     */
    public @Nullable Integer rankOf(String id, UUID uuid) {
        Integer own = ranks.getOrDefault(id, Map.of()).get(uuid);
        if (own != null) return own;
        for (Board.Entry e : board(id).entries()) if (e.uuid().equals(uuid)) return e.rank();
        return null;
    }

    public void apply(String id, Board board) {
        boards.put(id, board);
    }

    /** A board this server just changed (lock, reset, end …): show its answer, forget the old ranks, read them again. */
    public void applyChange(String id, Board board) {
        boards.put(id, board);
        ranks.remove(id);
        refresh(id);
    }

    /** Reads every defined board again. Blocking: call off the main thread. */
    public void refreshAll() {
        Set<String> defined = store.asMap().keySet();
        boards.keySet().retainAll(defined);
        ranks.keySet().retainAll(defined);
        for (String id : defined) read(id);
    }

    /** Reads one board again, off the main thread. */
    public void refresh(String id) {
        async.execute(() -> read(id));
    }

    /** Reads again every board that ranks this source (after a new best time here). */
    public void refreshSource(String sourceType, String sourceId) {
        for (Leaderboard def : store.asMap().values()) {
            if (sourceType.equals(def.getSourceType()) && sourceId.equals(def.getSourceId())) refresh(def.getId());
        }
    }

    private void read(String id) {
        String path = "/v1/server/leaderboards/" + seg(id);
        try {
            boards.put(id, Board.parse(api.getJson(path)));
            List<String> online = sessions.onlineIds().stream().map(UUID::toString).toList();
            if (online.isEmpty()) {
                ranks.remove(id);
            } else {
                ranks.put(id, Board.parseRanks(api.send("POST", path + "/ranks", Map.of("uuids", online))));
            }
        } catch (ApiException | IllegalStateException e) {
            log.fine("[Leaderboards] could not read " + id + ": " + e.getMessage());
        }
    }
}
