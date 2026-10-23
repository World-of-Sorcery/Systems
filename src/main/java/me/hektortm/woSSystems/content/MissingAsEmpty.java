package me.hektortm.woSSystems.content;

import me.hektortm.wosCore.api.ApiException;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Wraps a {@link ContentStore.Source} whose collection may not exist on an older wos-api: a 404
 * on {@link #loadAll()} gives no entities (and one warning until the collection appears) instead
 * of failing the preload, so an API without that feature does not stop players from joining.
 * Every other error is thrown as before.
 */
public final class MissingAsEmpty<T> implements ContentStore.Source<T> {

    private final ContentStore.Source<T> inner;
    private final String label;
    private final Logger log;
    private volatile boolean warned;

    private MissingAsEmpty(ContentStore.Source<T> inner, String label, Logger log) {
        this.inner = inner;
        this.label = label;
        this.log = log;
    }

    public static <T> ContentStore.Source<T> of(ContentStore.Source<T> inner, String label, Logger log) {
        return new MissingAsEmpty<>(inner, label, log);
    }

    @Override
    public Map<String, T> loadAll() throws ApiException {
        try {
            Map<String, T> all = inner.loadAll();
            warned = false;
            return all;
        } catch (ApiException e) {
            if (!e.isNotFound()) throw e;
            if (!warned) {
                warned = true;
                log.warning("[Content] " + label + ": the API does not serve this yet (404); loading none.");
            }
            return Map.of();
        }
    }

    @Override
    public Optional<T> loadOne(String id) throws ApiException {
        return inner.loadOne(id);
    }
}
