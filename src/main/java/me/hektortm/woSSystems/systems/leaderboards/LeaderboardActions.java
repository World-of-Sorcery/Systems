package me.hektortm.woSSystems.systems.leaderboards;

import me.hektortm.woSSystems.database.dao.LeaderboardDAO;
import me.hektortm.woSSystems.database.dao.TimerDAO;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSessions;
import me.hektortm.woSSystems.utils.model.Leaderboard;
import me.hektortm.wosCore.net.NetworkBus;

import java.util.UUID;
import java.util.concurrent.Executor;

import static me.hektortm.woSSystems.player.ApiWriter.seg;

/**
 * Lock, unlock, reset and end of a leaderboard (the {@code leaderboard} action
 * and {@code /leaderboard}). The API does the work in this server's realm; the
 * call goes out off the main thread and never holds up an action list. On the
 * answer this server shows the new board, a reset / end clears the source from
 * its sessions, and the other servers are told.
 */
public final class LeaderboardActions {
    private final LeaderboardDAO boards;
    private final TimerDAO timers;
    private final PlayerSessions sessions;
    private final ApiWriter writer;
    private final NetworkBus bus;
    private final String realm;
    private final Executor mainThread;

    public LeaderboardActions(LeaderboardDAO boards, TimerDAO timers, PlayerSessions sessions, ApiWriter writer,
                              NetworkBus bus, String realm, Executor mainThread) {
        this.boards = boards;
        this.timers = timers;
        this.sessions = sessions;
        this.writer = writer;
        this.bus = bus;
        this.realm = realm;
        this.mainThread = mainThread;
    }

    /** {@code verb} is lock, unlock, reset or end; false for an unknown leaderboard (nothing is sent). */
    public boolean run(String verb, String id) {
        if (boards.get(id) == null) return false;
        writer.post("/v1/server/leaderboards/" + seg(id) + "/" + verb, ApiWriter.body(), answer -> mainThread.execute(() -> {
            boards.applyChange(id, Board.parse(answer));
            if (verb.equals("reset") || verb.equals("end")) clearSource(id);
            NetworkLeaderboards.publish(bus, realm, id, verb);
        }));
        return true;
    }

    /** Removes the board's source data (the stat, or the timer's best times) from every loaded session. */
    public void clearSource(String id) {
        Leaderboard def = boards.get(id);
        if (def == null) return;
        if (def.isTimer()) {
            timers.clearBests(def.getSourceId());
            return;
        }
        for (UUID uuid : sessions.onlineIds()) {
            PlayerSession s = sessions.get(uuid);
            if (s != null) s.stats.remove(def.getSourceId());
        }
    }
}
