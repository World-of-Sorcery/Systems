package me.hektortm.woSSystems.systems.leaderboards;

import me.hektortm.woSSystems.utils.model.Leaderboard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardLinesTest {
    private static final String FORMAT = "&e#%rank% &f%name% &7- &a%value%";
    private static final String EMPTY = "&7#%rank% -";

    private static Board twoEntries(long first, long second) {
        return new Board(false, List.of(new Board.Entry(1, UUID.randomUUID(), "Alex", first), new Board.Entry(2, UUID.randomUUID(), "Sam", second)));
    }

    @Test
    void fillsEveryRankUpToTheSize() {
        Leaderboard def = new Leaderboard("b", "stat", "brew", 3, FORMAT, EMPTY);
        assertThat(LeaderboardLines.lines(def, twoEntries(12, 5)))
                .isEqualTo("&e#1 &fAlex &7- &a12\n&e#2 &fSam &7- &a5\n&7#3 -");
    }

    @Test
    void timerValuesAreTimes() {
        Leaderboard def = new Leaderboard("pk", "timer", "parkour1", 1, FORMAT, EMPTY);
        assertThat(LeaderboardLines.value(def, 83459)).isEqualTo("1:23.45");
        assertThat(LeaderboardLines.lines(def, twoEntries(83459, 90000))).isEqualTo("&e#1 &fAlex &7- &a1:23.45");
    }

    @Test
    void statValuesAreNumbers() {
        assertThat(LeaderboardLines.value(new Leaderboard("b", "stat", "brew", 3, FORMAT, EMPTY), 12)).isEqualTo("12");
    }

    @Test
    void customFormat() {
        Leaderboard def = new Leaderboard("b", "stat", "brew", 1, "%rank%. %name%", EMPTY);
        assertThat(LeaderboardLines.lines(def, twoEntries(1, 1))).isEqualTo("1. Alex");
    }
}
