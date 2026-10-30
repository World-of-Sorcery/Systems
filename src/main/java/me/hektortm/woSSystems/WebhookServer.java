package me.hektortm.woSSystems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.hektortm.woSSystems.database.DAOHub;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WebhookServer {

    private final HttpServer server;
    private final DAOHub daoHub;
    private final WoSSystems plugin;
    private final String secret;
    private final WebActions actions;
    private final boolean live;

    public WebhookServer(WoSSystems plugin, DAOHub daoHub) throws IOException {
        this.plugin = plugin;
        this.daoHub = daoHub;
        this.secret = plugin.getConfig().getString("webhook.secret");
        int port    = plugin.getConfig().getInt("webhook.port", 8090);

        this.live = WebhookRules.refusesContent(me.hektortm.wosCore.WoSCore.identity());
        this.actions = new WebActions(plugin, daoHub);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/invalidate", this::handleInvalidate);
        server.createContext("/api/action", this::handleAction);
        server.setExecutor(Executors.newFixedThreadPool(2));
    }

    public void start() {
        server.start();
        plugin.getLogger().info("[Webhook] Listening on port " +
                plugin.getConfig().getInt("webhook.port", 8090));
    }

    public void stop() {
        server.stop(0);
    }

    private UUID parseUUID(String raw) {
        if (raw.contains("-")) return UUID.fromString(raw);
        // Insert dashes: 8-4-4-4-12
        String formatted = raw.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(formatted);
    }

    private void handleInvalidate(HttpExchange exchange) throws IOException {
        // Only POST
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            respond(exchange, 405, "Method Not Allowed");
            return;
        }

        // Validate secret
        String auth = exchange.getRequestHeaders().getFirst("x-webhook-secret");
        if (auth == null || !auth.equals(secret)) {
            respond(exchange, 401, "Unauthorized");
            return;
        }

        // Parse body
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            plugin.getLogger().info("[Webhook] Parsed OK");

            String type = json.get("type").getAsString();
            plugin.getLogger().info("[Webhook] Type: " + type);

            String id = json.get("id").getAsString();
            plugin.getLogger().info("[Webhook] ID: " + id);

            UUID editorUUID = WebhookRules.editor(json.has("editor_uuid") && !json.get("editor_uuid").isJsonNull() ? json.get("editor_uuid").getAsString() : null);
            plugin.getLogger().info("[Webhook] UUID: " + editorUUID);

            if (!WebhookRules.accept(type, live)) {
                plugin.getLogger().info("[Webhook] Ignored " + type + " " + id + " on live (loads at restart)");
                respond(exchange, 200, "{\"ignored\":true}");
                return;
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    daoHub.handleWebhookInvalidation(type, id, editorUUID)
            );

            respond(exchange, 200, "{\"status\":\"ok\"}");

        } catch (Exception e) {
            plugin.getLogger().warning("[Webhook] Failed at: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            respond(exchange, 400, "{\"error\":\"invalid payload\"}");
        }
    }

    /**
     * A portal button for the portal user's own player (wos-api checked the
     * permission): {"action", "id", "amount", "player_uuid"}. Runs on the main
     * thread and answers with the outcome, e.g. 409 when the player is offline.
     */
    private void handleAction(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            respond(exchange, 405, "{\"code\":\"method_not_allowed\",\"message\":\"POST only\"}");
            return;
        }
        String auth = exchange.getRequestHeaders().getFirst("x-webhook-secret");
        if (secret == null || secret.isBlank() || !secret.equals(auth)) {
            respond(exchange, 401, "{\"code\":\"unauthorized\",\"message\":\"bad webhook secret\"}");
            return;
        }

        String action, id;
        int amount;
        UUID player;
        try {
            JsonObject json = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            action = json.get("action").getAsString();
            id = json.get("id").getAsString();
            amount = json.has("amount") ? Math.max(1, Math.min(64, json.get("amount").getAsInt())) : 1;
            player = parseUUID(json.get("player_uuid").getAsString());
        } catch (Exception e) {
            respond(exchange, 400, "{\"code\":\"invalid_payload\",\"message\":\"invalid payload\"}");
            return;
        }

        WebActions.Result result;
        try {
            result = Bukkit.getScheduler().callSyncMethod(plugin, () -> actions.run(action, id, amount, player))
                    .get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            result = new WebActions.Result(504, "timeout", "The server did not answer in time");
        } catch (Exception e) {
            plugin.getLogger().warning("[Webhook] action " + action + " " + id + " failed: " + e);
            result = new WebActions.Result(500, "failed", "The action failed on the server");
        }
        plugin.getLogger().info("[Webhook] action " + action + " " + id + " for " + player + ": " + result.code());

        JsonObject out = new JsonObject();
        out.addProperty("code", result.code());
        out.addProperty("message", result.message());
        respond(exchange, result.status(), out.toString());
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}