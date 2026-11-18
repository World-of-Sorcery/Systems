package me.hektortm.woSSystems.systems.leaderboards;

import me.hektortm.woSSystems.utils.model.Leaderboard;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * The text of {@code {leaderboard.<key>:<id>[:<rank>]}}, from the cached board only:
 * <ul>
 *   <li>{@code name|uuid|value:<id>:<rank>}: who holds the rank and their value; empty when nobody does</li>
 *   <li>{@code lines:<id>}: the board's lines; {@code locked:<id>}: true / false;
 *       {@code rank:<id>}: the viewing player's own rank (also below the shown ranks), empty without points</li>
 * </ul>
 * Null (left as written) for an unknown leaderboard or key.
 */
public final class LeaderboardPlaceholders {
    private LeaderboardPlaceholders() {}

    /**
     * @param rankOf a player's own rank on a board (board id, player), also below the
     *               shown ranks; null when they have none (no points yet, or not read yet)
     */
    public static @Nullable String value(String key, String id, Function<String, Leaderboard> defs,
                                         Function<String, Board> boards, java.util.function.BiFunction<String, UUID, Integer> rankOf,
                                         @Nullable UUID viewer) {
        int colon = id.indexOf(':');
        String boardId = colon < 0 ? id : id.substring(0, colon);
        Leaderboard def = defs.apply(boardId);
        if (def == null) return null;
        Board board = boards.apply(boardId);
        return switch (key) {
            case "name", "uuid", "value" -> {
                Board.Entry e = colon < 0 ? null : board.entry(rank(id.substring(colon + 1)));
                if (e == null) yield "";
                yield switch (key) {
                    case "name" -> e.name();
                    case "uuid" -> e.uuid().toString();
                    default -> LeaderboardLines.value(def, e.value());
                };
            }
            case "lines" -> LeaderboardLines.lines(def, board);
            case "locked" -> String.valueOf(board.locked());
            case "rank" -> {
                if (viewer == null) yield "";
                Integer own = rankOf.apply(boardId, viewer);
                if (own != null && own > 0) yield String.valueOf(own);
                for (Board.Entry e : board.entries()) if (e.uuid().equals(viewer)) yield String.valueOf(e.rank());
                yield "";
            }
            default -> null;
        };
    }

    /** The rank written in a token; 0 (no rank) when it isn't a number. */
    private static int rank(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
