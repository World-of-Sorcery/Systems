package me.hektortm.woSSystems.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.database.AsyncWriteQueue;
import me.hektortm.wosCore.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ApiWriter#post(String, Object, java.util.function.Consumer)}: the answer reaches the caller; no retry. */
class ApiWriterAnswerTest {
    private static final Logger LOG = Logger.getLogger("ApiWriterAnswerTest");

    @Test
    void theAnswerIsHandedBack() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        ApiWriter w = new ApiWriter((method, path, body, headers) -> {
            calls.add(method + " " + path + " " + body);
            return JsonParser.parseString("{\"value\":42}");
        }, LOG, new FrozenPlayers(LOG));
        AtomicReference<JsonElement> got = new AtomicReference<>();

        w.post("/v1/server/globalstats/boss_kills/delta", ApiWriter.body("delta", 1), got::set);
        AsyncWriteQueue.barrier().get(5, TimeUnit.SECONDS);

        assertThat(calls).containsExactly("POST /v1/server/globalstats/boss_kills/delta {delta=1}");
        assertThat(got.get().getAsJsonObject().get("value").getAsLong()).isEqualTo(42);
    }

    @Test
    void aFailedDeltaIsNotRetried() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        ApiWriter w = new ApiWriter((method, path, body, headers) -> {
            calls.add(method + " " + path);
            throw new ApiException(0, "unavailable", "timeout");
        }, LOG, new FrozenPlayers(LOG));
        AtomicReference<JsonElement> got = new AtomicReference<>();

        w.post("/v1/server/globalstats/boss_kills/delta", Map.of("delta", 1), got::set);
        AsyncWriteQueue.barrier().get(5, TimeUnit.SECONDS);

        assertThat(calls).hasSize(1); // a retry could count the delta twice
        assertThat(got.get()).isNull();
    }
}
