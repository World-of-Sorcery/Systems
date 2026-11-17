package me.hektortm.woSSystems.systems.leaderboards;

import me.hektortm.woSSystems.utils.TimeFormat;
import me.hektortm.woSSystems.utils.model.Leaderboard;

import java.util.ArrayList;
import java.util.List;

/** The text of a leaderboard: its value format, and its lines for {@code {leaderboard.lines:<id>}}. */
public final class LeaderboardLines {
    private LeaderboardLines() {}

    /** A value as the board shows it: a time for a timer board, a number for a stat board. */
    public static String value(Leaderboard def, long value) {
        return def.isTimer() ? TimeFormat.millis(value) : String.valueOf(value);
    }

    /** {@code size} lines joined with new lines: an entry's from the line format, a free rank's from the empty line. */
    public static String lines(Leaderboard def, Board board) {
        List<String> out = new ArrayList<>(def.getSize());
        for (int rank = 1; rank <= def.getSize(); rank++) {
            Board.Entry e = board.entry(rank);
            String rankText = String.valueOf(rank);
            out.add(e == null
                    ? def.getEmptyLine().replace("%rank%", rankText)
                    : def.getLineFormat().replace("%rank%", rankText).replace("%name%", e.name()).replace("%value%", value(def, e.value())));
        }
        return String.join("\n", out);
    }
}
