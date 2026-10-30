package me.hektortm.woSSystems.systems.magic.cast;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClickRouterTest {

    @Test
    void newestFirst() {
        ClickRouter router = new ClickRouter();
        UUID id = UUID.randomUUID();
        AtomicInteger order = new AtomicInteger();
        router.add(id, new ClickRouter.Listener() {
            public boolean live() { return true; }
            public boolean click() { order.compareAndSet(0, 1); return true; }
        });
        router.add(id, new ClickRouter.Listener() {
            public boolean live() { return true; }
            public boolean click() { order.compareAndSet(0, 2); return true; }
        });
        boolean taken = router.offer(id, false);
        assertThat(taken).isTrue();
        assertThat(order.get()).isEqualTo(2);
    }

    @Test
    void usedUpRemoved() {
        ClickRouter router = new ClickRouter();
        UUID id = UUID.randomUUID();
        AtomicInteger clicks = new AtomicInteger();
        router.add(id, new ClickRouter.Listener() {
            public boolean live() { return true; }
            public boolean click() {
                return clicks.incrementAndGet() >= 2;
            }
        });
        assertThat(router.offer(id, false)).isTrue();
        assertThat(clicks.get()).isEqualTo(1);
        assertThat(router.offer(id, false)).isTrue();
        assertThat(clicks.get()).isEqualTo(2);
        assertThat(router.offer(id, false)).isFalse();
    }

    @Test
    void deadListenerSkipped() {
        ClickRouter router = new ClickRouter();
        UUID id = UUID.randomUUID();
        AtomicBoolean secondCalled = new AtomicBoolean();
        router.add(id, new ClickRouter.Listener() {
            public boolean live() { return true; }
            public boolean click() { secondCalled.set(true); return true; }
        });
        router.add(id, new ClickRouter.Listener() {
            public boolean live() { return false; }
            public boolean click() { return true; }
        });
        assertThat(router.offer(id, false)).isTrue();
        assertThat(secondCalled.get()).isTrue();
    }

    @Test
    void sneakAlwaysCasts() {
        ClickRouter router = new ClickRouter();
        UUID id = UUID.randomUUID();
        AtomicBoolean clicked = new AtomicBoolean();
        router.add(id, new ClickRouter.Listener() {
            public boolean live() { return true; }
            public boolean click() { clicked.set(true); return true; }
        });
        assertThat(router.offer(id, true)).isFalse();
        assertThat(clicked.get()).isFalse();
    }

    @Test
    void clearOnQuit() {
        ClickRouter router = new ClickRouter();
        UUID id = UUID.randomUUID();
        router.add(id, new ClickRouter.Listener() {
            public boolean live() { return true; }
            public boolean click() { return true; }
        });
        router.clear(id);
        assertThat(router.offer(id, false)).isFalse();
    }
}
