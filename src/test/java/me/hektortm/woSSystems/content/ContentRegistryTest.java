package me.hektortm.woSSystems.content;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentRegistryTest {

    /** A source whose content the test changes; counts what was asked of it. */
    private static final class FakeSource implements ContentStore.Source<String> {
        final Map<String, String> data = new HashMap<>();
        int loadAll;
        int loadOne;

        @Override
        public Map<String, String> loadAll() {
            loadAll++;
            return new HashMap<>(data);
        }

        @Override
        public Optional<String> loadOne(String id) {
            loadOne++;
            return Optional.ofNullable(data.get(id));
        }
    }

    @Test
    void starReloadsTheWholeStore() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        ContentRegistry registry = new ContentRegistry(plugin);
        FakeSource source = new FakeSource();
        source.data.put("a", "A");
        ContentStore<String> store = registry.register(new ContentStore<>("rpglyphs", "Glyph", source));
        store.preload();

        // A big glyph sheet was made: thousands of new glyphs, one "*" signal.
        for (int i = 0; i < 3000; i++) source.data.put("cell_" + i, "x");
        source.data.remove("a");
        registry.reload("rpglyphs", ContentRegistry.RELOAD_ALL, null);

        assertThat(store.size()).isEqualTo(3000);
        assertThat(store.exists("a")).isFalse();
        assertThat(source.loadAll).isEqualTo(2);
        assertThat(source.loadOne).isZero();
    }
}
