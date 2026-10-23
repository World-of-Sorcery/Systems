package me.hektortm.woSSystems.content;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises ApiSource + the real WosApi client against an in-process HTTP server
 * that answers like wos-api (JSON bodies, error envelope, bearer token).
 */
class ApiSourceTest {
    private static final Logger LOG = Logger.getLogger("test");

    private HttpServer server;
    private final Map<String, String> routes = new ConcurrentHashMap<>();
    private volatile String lastAuth;
    private WosApi api;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastAuth = ex.getRequestHeaders().getFirst("Authorization");
            String body = routes.get(ex.getRequestURI().getRawPath());
            int status = 200;
            if (body == null) {
                status = 404;
                body = "{\"error\":{\"code\":\"not_found\",\"message\":\"no such thing\"}}";
            } else if (body.startsWith("!")) {
                status = Integer.parseInt(body.substring(1, 4));
                body = "{\"error\":{\"code\":\"boom\",\"message\":\"server error\"}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        api = new WosApi("http://127.0.0.1:" + server.getAddress().getPort(), "plugin-token", Duration.ofSeconds(2), LOG);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void flatSourceMapsEveryRowAndSendsTheToken() throws ApiException {
        routes.put("/v1/content/constants", "[{\"id\":\"a\",\"value\":\"1\"},{\"id\":\"b\",\"value\":\"2\"}]");
        ApiSource<String> src = ApiSource.flat(api, "/v1/content/constants", "id", j -> Json.str(j, "value"), LOG);

        assertThat(src.loadAll()).containsEntry("a", "1").containsEntry("b", "2");
        assertThat(lastAuth).isEqualTo("Bearer plugin-token");
    }

    @Test
    void aRowThatFailsToMapIsSkippedNotFatal() throws ApiException {
        routes.put("/v1/content/things", "[{\"id\":\"good\",\"n\":1},{\"id\":\"bad\",\"n\":\"x\"}]");
        ApiSource<Integer> src = ApiSource.flat(api, "/v1/content/things", "id", j -> j.get("n").getAsInt(), LOG);

        assertThat(src.loadAll()).containsOnlyKeys("good");
    }

    @Test
    void treeSourceFetchesEachEntity() throws ApiException {
        routes.put("/v1/content/guis", "[{\"id\":\"menu\"},{\"id\":\"shop\"}]");
        routes.put("/v1/content/guis/menu", "{\"gui\":{\"id\":\"menu\",\"title\":\"Menu\"}}");
        routes.put("/v1/content/guis/shop", "{\"gui\":{\"id\":\"shop\",\"title\":\"Shop\"}}");
        ApiSource<String> src = ApiSource.tree(api, "/v1/content/guis", j -> Json.str(Json.object(j, "gui"), "title"), LOG);

        assertThat(src.loadAll()).containsEntry("menu", "Menu").containsEntry("shop", "Shop");
    }

    @Test
    void loadOneOfADeletedEntityIsEmpty() throws ApiException {
        ApiSource<JsonObject> src = ApiSource.flat(api, "/v1/content/citems", "id", j -> j, LOG);
        assertThat(src.loadOne("gone")).isEmpty();
    }

    @Test
    void idsAreUrlEncoded() throws ApiException {
        routes.put("/v1/content/channels/team%20chat", "{\"name\":\"team chat\"}");
        ApiSource<String> src = ApiSource.flat(api, "/v1/content/channels", "name", j -> Json.str(j, "name"), LOG);
        assertThat(src.loadOne("team chat")).contains("team chat");
    }

    @Test
    void serverErrorsSurfaceAsUnavailable() {
        routes.put("/v1/content/constants", "!503");
        ApiSource<String> src = ApiSource.flat(api, "/v1/content/constants", "id", j -> "", LOG);

        assertThatThrownBy(src::loadAll)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).isUnavailable()).isTrue());
    }

    @Test
    void missingAsEmptyTurnsA404CollectionIntoNoEntitiesWithOneWarning() throws ApiException {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        Logger log = Logger.getLogger("test.missing");
        log.setUseParentHandlers(false);
        log.addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) {
                if (r.getLevel() == java.util.logging.Level.WARNING) warnings.add(r.getMessage());
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
        ContentStore.Source<String> src = MissingAsEmpty.of(
                ApiSource.flat(api, "/v1/content/rpglyphs/published", "id", j -> Json.str(j, "id"), LOG), "rpglyphs", log);

        assertThat(src.loadAll()).isEmpty();
        assertThat(src.loadAll()).isEmpty();
        assertThat(warnings).hasSize(1);

        routes.put("/v1/content/rpglyphs/published", "[{\"id\":\"coin\"}]");
        assertThat(src.loadAll()).containsOnlyKeys("coin");
    }

    @Test
    void missingAsEmptyStillThrowsOtherErrors() {
        routes.put("/v1/content/rpglyphs/published", "!503");
        ContentStore.Source<String> src = MissingAsEmpty.of(
                ApiSource.flat(api, "/v1/content/rpglyphs/published", "id", j -> "", LOG), "rpglyphs", LOG);

        assertThatThrownBy(src::loadAll)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).isUnavailable()).isTrue());
    }

    @Test
    void unreachableApiIsUnavailable() {
        WosApi dead = new WosApi("http://127.0.0.1:1", "t", Duration.ofMillis(300), LOG);
        ApiSource<String> src = ApiSource.flat(dead, "/v1/content/constants", "id", j -> "", LOG);

        assertThatThrownBy(src::loadAll)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isZero());
    }
}
