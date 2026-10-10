package me.hektortm.woSSystems.content;

import me.hektortm.wosCore.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ContentStoreTest {

    /** In-memory source whose contents a test can change between loads. */
    static final class FakeSource implements ContentStore.Source<String> {
        final Map<String, String> data = new HashMap<>();

        @Override
        public Map<String, String> loadAll() {
            return new HashMap<>(data);
        }

        @Override
        public Optional<String> loadOne(String id) {
            return Optional.ofNullable(data.get(id));
        }
    }

    @Test
    void preloadReplacesTheWholeCache() throws ApiException {
        FakeSource src = new FakeSource();
        ContentStore<String> store = new ContentStore<>("things", "Thing", src);
        src.data.put("a", "A");
        src.data.put("b", "B");
        store.preload();
        assertThat(store.all()).containsExactlyInAnyOrder("A", "B");

        src.data.remove("a");
        store.preload();
        assertThat(store.exists("a")).isFalse(); // removed upstream → gone
        assertThat(store.get("b")).isEqualTo("B");
    }

    @Test
    void reloadUpdatesOrEvictsOneEntity() throws ApiException {
        FakeSource src = new FakeSource();
        ContentStore<String> store = new ContentStore<>("things", "Thing", src);
        src.data.put("a", "A");
        store.preload();

        src.data.put("a", "A2");
        assertThat(store.reload("a")).isEqualTo(ContentStore.Reload.UPDATED);
        assertThat(store.get("a")).isEqualTo("A2");

        src.data.remove("a");
        assertThat(store.reload("a")).isEqualTo(ContentStore.Reload.DELETED);
        assertThat(store.get("a")).isNull();
    }

    @Test
    void reloadingAnEntityTheStoreDidNotHoldIsACreate() throws ApiException {
        FakeSource src = new FakeSource();
        ContentStore<String> store = new ContentStore<>("things", "Thing", src);
        store.preload();

        src.data.put("new", "N");
        assertThat(store.reload("new")).isEqualTo(ContentStore.Reload.CREATED);
        assertThat(store.reload("new")).isEqualTo(ContentStore.Reload.UPDATED);

        // Deleted and made again under the same id: created again.
        src.data.remove("new");
        assertThat(store.reload("new")).isEqualTo(ContentStore.Reload.DELETED);
        src.data.put("new", "N2");
        assertThat(store.reload("new")).isEqualTo(ContentStore.Reload.CREATED);
    }

    @Test
    void replaceMatchingSwapsOnlyTheMatchingKeys() {
        ContentStore<String> store = new ContentStore<>("conds", "Condition", new FakeSource());
        store.replaceMatching(k -> true, Map.of("interaction:x:1", "old", "interaction:y:1", "keep"));

        store.replaceMatching(k -> k.startsWith("interaction:x:"), Map.of("interaction:x:2", "new"));

        assertThat(store.asMap()).containsOnlyKeys("interaction:x:2", "interaction:y:1");
    }

    @Test
    void listenersRunAfterEveryChange() throws ApiException {
        FakeSource src = new FakeSource();
        AtomicInteger calls = new AtomicInteger();
        ContentStore<String> store = new ContentStore<>("things", "Thing", src).onChange(s -> calls.incrementAndGet());
        store.preload();
        store.reload("missing");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void nullIdIsNeverFound() {
        ContentStore<String> store = new ContentStore<>("things", "Thing", new FakeSource());
        assertThat(store.get(null)).isNull();
        assertThat(store.exists(null)).isFalse();
    }
}
