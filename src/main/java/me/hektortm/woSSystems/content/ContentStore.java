package me.hektortm.woSSystems.content;

import me.hektortm.wosCore.api.ApiException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory cache of ONE kind of content definition (citems, guis, …), loaded
 * from wos-api. Reads are zero-latency map lookups; the cache is filled once at
 * startup ({@link #preload()}) and refreshed per entity when the portal signals
 * an edit ({@link #reload(String)} via {@link ContentRegistry}).
 *
 * <p>Every content DAO is a thin wrapper around one (or more) stores, so the
 * load / reload / cache logic lives here exactly once.</p>
 *
 * @param <T> the domain model the store holds (already built — e.g. an ItemStack)
 */
public final class ContentStore<T> {

    /** Where a store's entities come from (usually {@link ApiSource}). */
    public interface Source<T> {
        /** Every entity, keyed by id. Entities that fail to map are skipped (and logged) by the source. */
        Map<String, T> loadAll() throws ApiException;

        /** One entity; empty when it no longer exists. */
        Optional<T> loadOne(String id) throws ApiException;
    }

    /** Outcome of a single-entity reload: new to the cache, replaced in it, or gone from it. */
    public enum Reload { CREATED, UPDATED, DELETED }

    private final String key;
    private final String label;
    private final Source<T> source;
    private final Map<String, T> cache = new ConcurrentHashMap<>();
    private final List<Consumer<ContentStore<T>>> listeners = new ArrayList<>();

    /**
     * @param key    the content type key the portal's webhook uses ("citems", "guis", …)
     * @param label  human name shown in reload titles ("Citem", "GUI", …)
     * @param source where entities are loaded from
     */
    public ContentStore(String key, String label, Source<T> source) {
        this.key = key;
        this.label = label;
        this.source = source;
    }

    public String key() { return key; }

    public String label() { return label; }

    /** Replaces the whole cache with a fresh load. */
    public void preload() throws ApiException {
        Map<String, T> all = source.loadAll();
        cache.keySet().retainAll(all.keySet());
        cache.putAll(all);
        changed();
    }

    /** Refreshes one entity (one the cache did not hold yet counts as created); evicts it if it was deleted. */
    public Reload reload(String id) throws ApiException {
        Optional<T> fresh = source.loadOne(id);
        Reload result;
        if (fresh.isPresent()) {
            result = cache.put(id, fresh.get()) == null ? Reload.CREATED : Reload.UPDATED;
        } else {
            cache.remove(id);
            result = Reload.DELETED;
        }
        changed();
        return result;
    }

    /**
     * Replaces every cached entry whose key matches {@code keys} with {@code fresh}
     * — for stores fed by another store's tree (e.g. the conditions embedded in an
     * interaction tree replace that interaction's conditions here).
     */
    public void replaceMatching(java.util.function.Predicate<String> keys, Map<String, T> fresh) {
        cache.keySet().removeIf(keys);
        cache.putAll(fresh);
        changed();
    }

    /**
     * Local write, for the few stores whose entries the plugin itself creates
     * (world state such as placed items); the caller persists the change.
     */
    public void put(String id, T value) {
        cache.put(id, value);
        changed();
    }

    /** Local removal counterpart of {@link #put}. */
    public void remove(String id) {
        if (cache.remove(id) != null) changed();
    }

    /** The entity, or {@code null} if unknown. */
    public T get(String id) {
        return id == null ? null : cache.get(id);
    }

    public Optional<T> find(String id) {
        return Optional.ofNullable(get(id));
    }

    public boolean exists(String id) {
        return id != null && cache.containsKey(id);
    }

    /** Snapshot of every cached entity. */
    public Collection<T> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(cache.values()));
    }

    /** Live read-only view keyed by id. */
    public Map<String, T> asMap() {
        return Collections.unmodifiableMap(cache);
    }

    public int size() { return cache.size(); }

    /**
     * Runs {@code listener} after every preload/reload — for DAOs that keep a
     * derived index (e.g. fishing items grouped by rarity).
     */
    public ContentStore<T> onChange(Consumer<ContentStore<T>> listener) {
        listeners.add(listener);
        return this;
    }

    private void changed() {
        for (Consumer<ContentStore<T>> l : listeners) l.accept(this);
    }
}
