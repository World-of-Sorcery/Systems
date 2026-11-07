package me.hektortm.woSSystems.systems.mail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.systems.mail.model.Mail;
import me.hektortm.woSSystems.systems.mail.model.MailAttachment;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * The mailbox routes of wos-api. Every call runs off the server thread ({@code async}) and completes there; callers
 * hop back to the server thread themselves. Mail the plugin creates (overflow, player mail) carries one random key
 * per request, reused by every retry, so a retry never makes a second mail.
 */
public final class MailClient {

    /** One API call (the real one is {@link WosApi}); answers the JSON body or null. */
    @FunctionalInterface
    public interface Api {
        @Nullable
        JsonElement call(String method, String path, @Nullable Object body, Map<String, String> headers) throws ApiException;
    }

    /** One page of a mailbox with its counts. */
    public record MailPage(int unclaimed, int unread, int total, List<Mail> items) {}

    /** A claim's answer: the mail (claimed now) or the API's error code (mail_claimed, nothing_to_claim …). */
    public record ClaimResult(@Nullable Mail mail, @Nullable String error) {}

    private static final int ATTEMPTS = 4;

    private final Api api;
    private final Executor async;
    private final Logger log;

    public MailClient(Api api, Executor async, Logger log) {
        this.api = api;
        this.async = async;
        this.log = log;
    }

    /** The real client: GETs through {@link WosApi#getJson} (retried reads), writes through {@link WosApi#send}. */
    public static MailClient of(WosApi wos, Executor async, Logger log) {
        return new MailClient((method, path, body, headers) ->
                "GET".equals(method) ? wos.getJson(path) : wos.send(method, path, body, headers), async, log);
    }

    private static String box(UUID player) {
        return "/v1/players/" + player + "/mail";
    }

    private <T> CompletableFuture<T> run(ThrowingSupplier<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.get();
            } catch (ApiException e) {
                throw new CompletionException(e);
            }
        }, async);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws ApiException;
    }

    public CompletableFuture<MailPage> list(UUID player, @Nullable Long before, int limit) {
        String path = box(player) + "?limit=" + limit + (before == null ? "" : "&before=" + before);
        return run(() -> page(api.call("GET", path, null, Map.of())));
    }

    static MailPage page(@Nullable JsonElement e) {
        JsonObject o = e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        List<Mail> items = new ArrayList<>();
        for (JsonElement m : Json.array(o, "items")) {
            if (m.isJsonObject()) items.add(Mail.fromJson(m.getAsJsonObject()));
        }
        return new MailPage(Json.integer(o, "unclaimed", 0), Json.integer(o, "unread", 0), Json.integer(o, "total", 0), List.copyOf(items));
    }

    public CompletableFuture<Void> read(UUID player, long id) {
        return run(() -> {
            api.call("POST", box(player) + "/" + id + "/read", null, Map.of());
            return null;
        });
    }

    public CompletableFuture<Void> delete(UUID player, long id) {
        return run(() -> {
            api.call("DELETE", box(player) + "/" + id, null, Map.of());
            return null;
        });
    }

    /** Claims a mail once: the items may be given only when this answered a mail. */
    public CompletableFuture<ClaimResult> claim(UUID player, long id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonElement e = api.call("POST", box(player) + "/" + id + "/claim", null, Map.of());
                return e != null && e.isJsonObject() ? new ClaimResult(Mail.fromJson(e.getAsJsonObject()), null) : new ClaimResult(null, "no_answer");
            } catch (ApiException e) {
                return new ClaimResult(null, e.code());
            } catch (RuntimeException e) {
                // Any failure is an answer, so the caller always frees the player's claim.
                log.warning("[Mail] claim " + id + " failed: " + e);
                return new ClaimResult(null, "failed");
            }
        }, async);
    }

    /** Writes an overflow mail; completes false when the API stayed unavailable (or refused it). */
    public CompletableFuture<Boolean> overflow(UUID player, String reason, List<MailAttachment> items) {
        Map<String, Object> body = Map.of("key", UUID.randomUUID().toString(), "reason", reason, "attachments", json(items));
        return CompletableFuture.supplyAsync(() -> retried("POST", box(player) + "/overflow", body) == null, async);
    }

    /** Sends a player's package; completes with null when sent, else the API's error code (mailbox_full …). */
    public CompletableFuture<String> sendPlayerMail(UUID recipient, UUID sender, String subject, String body, List<MailAttachment> items) {
        Map<String, Object> req = Map.of("key", UUID.randomUUID().toString(), "sender_uuid", sender.toString(),
                "subject", subject, "body", body, "attachments", json(items));
        return CompletableFuture.supplyAsync(() -> retried("POST", box(recipient) + "/player", req), async);
    }

    /** Sends a preset; completes with the number of mails made. {@code targets}: uuids, or "online" / "all". */
    public CompletableFuture<Integer> sendPreset(String preset, Object targets, String source, @Nullable UUID actor) {
        Map<String, Object> req = Map.of("preset", preset, "targets", targets, "source", source);
        Map<String, String> headers = actor == null ? Map.of() : Map.of("X-Actor-Uuid", actor.toString());
        return run(() -> {
            JsonElement e = api.call("POST", "/v1/server/mail/send", req, headers);
            return e != null && e.isJsonObject() ? Json.integer(e.getAsJsonObject(), "count", 0) : 0;
        });
    }

    /** A player's uuid by name (anyone who ever joined); fails with the API's 404 for an unknown name. */
    public CompletableFuture<UUID> uuidOf(String name) {
        return run(() -> {
            JsonElement e = api.call("GET", "/v1/players/by-name/" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8), null, Map.of());
            String uuid = e != null && e.isJsonObject() ? Json.str(e.getAsJsonObject(), "uuid") : null;
            if (uuid == null) throw new ApiException(404, "player_not_found", "no player " + name);
            return UUID.fromString(uuid);
        });
    }

    /** The mailbox's look (the settings JSON the portal saves). */
    public CompletableFuture<String> settings() {
        return run(() -> {
            JsonElement e = api.call("GET", "/v1/content/mail", null, Map.of());
            if (e == null || !e.isJsonObject()) return "{}";
            JsonElement s = e.getAsJsonObject().get("settings");
            return s == null ? "{}" : s.toString();
        });
    }

    private static JsonArray json(List<MailAttachment> items) {
        JsonArray out = new JsonArray();
        for (MailAttachment a : items) out.add(a.toJson());
        return out;
    }

    /** A keyed write, retried while the API is unavailable: null when it went through, else the error code. */
    @Nullable
    private String retried(String method, String path, Object body) {
        for (int attempt = 1; ; attempt++) {
            try {
                api.call(method, path, body, Map.of());
                return null;
            } catch (ApiException e) {
                if (!e.isUnavailable() || attempt >= ATTEMPTS) {
                    if (e.isUnavailable()) log.warning("[Mail] " + method + " " + path + " failed: " + e.getMessage());
                    return e.code() == null ? "unavailable" : e.code();
                }
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "interrupted";
                }
            }
        }
    }
}
