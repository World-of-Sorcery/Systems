package me.hektortm.woSSystems.content;

import me.hektortm.wosCore.api.ApiException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Every {@link ContentStore}, keyed by the content type the portal uses.
 *
 * <p>Owns startup loading ({@link #preloadAll()}, in registration order — register
 * stores that others depend on first) and the portal's cache-invalidation webhook
 * ({@link #reload}): any registered type reloads, so there is no per-type switch
 * to keep in sync.</p>
 */
public final class ContentRegistry {
    private static final int PRELOAD_ATTEMPTS = 5;

    private final Plugin plugin;
    private final Logger log;
    private final Map<String, ContentStore<?>> stores = new LinkedHashMap<>();
    private volatile boolean ready;

    public ContentRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /** Registers a store under its key and returns it (for field initialisation). */
    public <T> ContentStore<T> register(ContentStore<T> store) {
        if (stores.putIfAbsent(store.key(), store) != null) {
            throw new IllegalStateException("content type registered twice: " + store.key());
        }
        return store;
    }

    /**
     * Loads every store. Blocking — call off the main thread. Retries with
     * backoff while the API is unavailable; returns whether everything loaded.
     * Players are refused until content is ready (see {@link #isReady()}).
     */
    public boolean preloadAll() {
        for (int attempt = 1; attempt <= PRELOAD_ATTEMPTS; attempt++) {
            try {
                for (ContentStore<?> s : stores.values()) {
                    s.preload();
                    log.info("[Content] " + s.key() + ": " + s.size() + " loaded");
                }
                ready = true;
                return true;
            } catch (ApiException e) {
                log.severe("[Content] preload attempt " + attempt + "/" + PRELOAD_ATTEMPTS + " failed: " + e.getMessage());
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /** True once every store has loaded at least once. */
    public boolean isReady() { return ready; }

    /**
     * Handles the portal's invalidation webhook: reloads one entity and shows the
     * editor (if online) the result, as the legacy DAOs did. Blocking.
     */
    public void reload(String type, String id, @Nullable UUID editor) {
        ContentStore<?> store = stores.get(type);
        if (store == null) {
            log.warning("[Content] webhook for unknown type '" + type + "' (id " + id + ")");
            return;
        }
        String title;
        try {
            ContentStore.Reload r = store.reload(id);
            title = switch (r) {
                case CREATED -> "§aCreated " + store.label();
                case UPDATED -> "§aUpdated " + store.label();
                case DELETED -> "§cDeleted " + store.label();
            };
            log.info("[Content] " + type + ":" + id + " " + r.name().toLowerCase());
        } catch (ApiException e) {
            log.warning("[Content] reload " + type + ":" + id + " failed: " + e.getMessage());
            title = "§cReload failed: " + store.label();
        }
        notify(editor, title, id);
    }

    private void notify(@Nullable UUID editor, String title, String id) {
        if (editor == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(editor);
            if (p != null) p.sendTitle(title, "§e" + id, 10, 40, 10);
        });
    }

    @Nullable
    public ContentStore<?> store(String type) {
        return stores.get(type);
    }
}
