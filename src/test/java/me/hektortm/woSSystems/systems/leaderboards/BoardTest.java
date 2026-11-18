package me.hektortm.woSSystems.systems.leaderboards;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BoardTest {

    @Test
    void parsesEntries() {
        Board b = Board.parse(JsonParser.parseString("""
            {"locked": true, "entries": [
              {"rank": 1, "uuid": "00000000-0000-0000-0000-000000000001", "name": "Alex", "value": 9},
              {"rank": 2, "uuid": "00000000-0000-0000-0000-000000000002", "name": "Sam", "value": 5}
            ]}"""));
        assertThat(b.locked()).isTrue();
        assertThat(b.entries()).containsExactly(
                new Board.Entry(1, UUID.fromString("00000000-0000-0000-0000-000000000001"), "Alex", 9),
                new Board.Entry(2, UUID.fromString("00000000-0000-0000-0000-000000000002"), "Sam", 5));
        assertThat(b.entry(2).name()).isEqualTo("Sam");
        assertThat(b.entry(3)).isNull();
    }

    @Test
    void parsesPlayerRanks() {
        assertThat(Board.parseRanks(JsonParser.parseString("""
            {"ranks": [{"uuid": "00000000-0000-0000-0000-000000000003", "rank": 7, "value": 2}, {"uuid": "nope", "rank": 1}]}""")))
                .containsExactly(java.util.Map.entry(UUID.fromString("00000000-0000-0000-0000-000000000003"), 7));
        assertThat(Board.parseRanks(null)).isEmpty();
    }

    @Test
    void malformedIsEmpty() {
        assertThat(Board.parse(null)).isEqualTo(Board.EMPTY);
        assertThat(Board.parse(JsonParser.parseString("[1, 2]"))).isEqualTo(Board.EMPTY);
        assertThat(Board.parse(JsonParser.parseString("{\"entries\": [{\"rank\": 1, \"uuid\": \"nope\"}]}")).entries()).isEmpty();
    }
}
