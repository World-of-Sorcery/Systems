package me.hektortm.woSSystems.database.dao;

import com.google.gson.JsonParser;
import me.hektortm.woSSystems.utils.Operations;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Global stat give / take go to the API as deltas; the answer's value replaces the cached one. */
class GlobalStatDeltaTest {

    @Test
    void giveAndTakeAreDeltasSetAndResetAreNot() {
        assertThat(StatsDAO.deltaOf(Operations.GIVE, 5)).isEqualTo(5L);
        assertThat(StatsDAO.deltaOf(Operations.TAKE, 5)).isEqualTo(-5L);
        assertThat(StatsDAO.deltaOf(Operations.SET, 5)).isNull();
        assertThat(StatsDAO.deltaOf(Operations.RESET, 5)).isNull();
    }

    @Test
    void theAnswerReplacesTheCachedValue() {
        Map<String, Long> cache = new HashMap<>(Map.of("boss_kills", 3L));
        // Another server of the realm counted too: the API's value wins over the local one.
        StatsDAO.storeAnswer(cache, "boss_kills", JsonParser.parseString("{\"value\":12}"));
        assertThat(cache).containsEntry("boss_kills", 12L);
        StatsDAO.storeAnswer(cache, "new_stat", JsonParser.parseString("{\"value\":1}"));
        assertThat(cache).containsEntry("new_stat", 1L);
    }

    @Test
    void anAnswerWithoutAValueChangesNothing() {
        Map<String, Long> cache = new HashMap<>(Map.of("boss_kills", 3L));
        StatsDAO.storeAnswer(cache, "boss_kills", null);
        StatsDAO.storeAnswer(cache, "boss_kills", JsonParser.parseString("{}"));
        StatsDAO.storeAnswer(cache, "boss_kills", JsonParser.parseString("{\"value\":\"x\"}"));
        StatsDAO.storeAnswer(cache, "boss_kills", JsonParser.parseString("[]"));
        assertThat(cache).containsEntry("boss_kills", 3L).hasSize(1);
    }
}
