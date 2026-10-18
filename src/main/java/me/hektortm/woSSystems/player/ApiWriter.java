package me.hektortm.woSSystems.player;

import com.google.gson.JsonElement;
import me.hektortm.woSSystems.database.AsyncWriteQueue;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;

import javax.annotation.Nullable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Persists game-state changes to wos-api in the background. Writes go through the
 * single-threaded {@link AsyncWriteQueue}, so they reach the API in the order the
 * game made them; a write that fails because the API is briefly unavailable is
 * retried before it is reported.
 */
public final class ApiWriter {
    private static final int ATTEMPTS = 4;

    /** One API write (the real one is {@link WosApi#send}); answers the JSON body, or null for none. */
    @FunctionalInterface
    public interface Sender {
        @Nullable JsonElement send(String method, String path, Object body, Map<String, String> headers) throws Exception;
    }

    private final Sender api;
    private final Logger log;
    private final FrozenPlayers frozen;

    /**
     * @param frozen players another server took over: their writes are dropped (see
     *               {@link FrozenPlayers#drops}) — checked when the write is made, so writes
     *               queued before the freeze still go out.
     */
    public ApiWriter(WosApi api, Logger log, FrozenPlayers frozen) {
        this(api::send, log, frozen);
    }

    ApiWriter(Sender api, Logger log, FrozenPlayers frozen) {
        this.api = api;
        this.log = log;
        this.frozen = frozen;
    }

    public void put(String path, Object body) { submit("PUT", path, body, Map.of()); }

    public void post(String path, Object body) { submit("POST", path, body, Map.of()); }

    public void delete(String path) { submit("DELETE", path, null, Map.of()); }

    /**
     * POST that must apply exactly once even when retried (economy transactions):
     * one idempotency key is generated here and reused by every attempt.
     */
    public void postOnce(String path, Object body) {
        submit("POST", path, body, Map.of("Idempotency-Key", java.util.UUID.randomUUID().toString()));
    }

    /**
     * POST whose answer the caller needs (a global stat delta answers the new value):
     * {@code onAnswer} gets the API's JSON answer (null when it had no body) on the write
     * thread, in queue order. Unlike the other writes it is NOT retried: it adds rather than
     * sets, so a retry after a request that did reach the API would count twice. A failure is
     * logged and reported like any failed write.
     */
    public void post(String path, Object body, Consumer<JsonElement> onAnswer) {
        if (frozen.drops("POST", path, body)) return;
        AsyncWriteQueue.submit(() -> {
            JsonElement answer;
            try {
                answer = api.send("POST", path, body, Map.of());
            } catch (ApiException e) {
                log.severe("[WosApi] write failed: POST " + path + " — " + e.getMessage());
                throw e;
            }
            onAnswer.accept(answer);
        });
    }

    /** Convenience body: {@code body("value", 5, "temp", true)} → {"value":5,"temp":true}. */
    public static Map<String, Object> body(Object... keyValues) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) m.put((String) keyValues[i], keyValues[i + 1]);
        return m;
    }

    /** URL-encodes one path segment (ids may contain spaces, commas, colons…). */
    public static String seg(Object value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void submit(String method, String path, Object body, Map<String, String> headers) {
        if (frozen.drops(method, path, body)) return; // another server holds this player now
        AsyncWriteQueue.submit(() -> {
            for (int attempt = 1; ; attempt++) {
                try {
                    api.send(method, path, body, headers);
                    return;
                } catch (ApiException e) {
                    if (!e.isUnavailable() || attempt >= ATTEMPTS) {
                        log.severe("[WosApi] write failed: " + method + " " + path + " — " + e.getMessage());
                        throw e; // AsyncWriteQueue reports it to Discord
                    }
                    Thread.sleep(500L * attempt);
                }
            }
        });
    }
}
