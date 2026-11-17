package me.hektortm.woSSystems.systems.leaderboards;

import me.hektortm.woSSystems.utils.model.Leaderboard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardPlaceholdersTest {
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Leaderboard DEF = new Leaderboard("b", "stat", "brew", 3, "%rank% %name%", "%rank% -");
    private static final Board BOARD = new Board(false, List.of(new Board.Entry(1, ALEX, "Alex", 9), new Board.Entry(2, SAM, "Sam", 5)));

    private static final UUID ROBIN = UUID.fromString("00000000-0000-0000-0000-000000000007");

    /** The players' own ranks the API answered: Robin is 7th, below the 3 shown. */
    private static Integer rankOf(String board, UUID player) {
        return ROBIN.equals(player) ? Integer.valueOf(7) : null;
    }

    private static String value(String key, String id, UUID viewer) {
        return LeaderboardPlaceholders.value(key, id, Map.of("b", DEF)::get, x -> BOARD, LeaderboardPlaceholdersTest::rankOf, viewer);
    }

    @Test
    void ranksByNumber() {
        assertThat(value("name", "b:1", null)).isEqualTo("Alex");
        assertThat(value("uuid", "b:1", null)).isEqualTo(ALEX.toString());
        assertThat(value("value", "b:2", null)).isEqualTo("5");
    }

    @Test
    void anEmptyOrBadRankIsEmpty() {
        assertThat(value("name", "b:3", null)).isEmpty();
        assertThat(value("name", "b:x", null)).isEmpty();
        assertThat(value("name", "b:0", null)).isEmpty();
        assertThat(value("name", "b", null)).isEmpty();
    }

    @Test
    void boardWide() {
        assertThat(value("locked", "b", null)).isEqualTo("false");
        assertThat(value("lines", "b", null)).isEqualTo("1 Alex\n2 Sam\n3 -");
        assertThat(value("rank", "b", SAM)).isEqualTo("2");
        assertThat(value("rank", "b", UUID.randomUUID())).isEmpty();
    }

    @Test
    void aPlayersOwnRankShowsBelowTheShownRanks() {
        assertThat(value("rank", "b", ROBIN)).isEqualTo("7");
    }

    @Test
    void anUnknownBoardOrKeyIsLeftAsWritten() {
        assertThat(value("name", "nope:1", null)).isNull();
        assertThat(value("colour", "b", null)).isNull();
    }
}
