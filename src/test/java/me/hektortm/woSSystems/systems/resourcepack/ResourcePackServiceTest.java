package me.hektortm.woSSystems.systems.resourcepack;

import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcePackServiceTest {

    /** Manual clock: tasks run when advance() passes their time. */
    static final class FakeScheduler implements ResourcePackService.Scheduler {
        long now;
        final List<Entry> pending = new ArrayList<>();

        static final class Entry implements ResourcePackService.Task {
            long at; Runnable r; boolean cancelled;
            public void cancel() { cancelled = true; }
        }

        public ResourcePackService.Task later(Runnable r, long delayMs) {
            Entry e = new Entry();
            e.at = now + delayMs;
            e.r = r;
            pending.add(e);
            return e;
        }

        void advance(long ms) {
            long target = now + ms;
            while (true) {
                Entry next = null;
                for (Entry e : pending) if (!e.cancelled && e.at <= target && (next == null || e.at < next.at)) next = e;
                if (next == null) break;
                pending.remove(next);
                now = Math.max(now, next.at);
                next.r.run();
            }
            now = target;
        }
    }

    static final class FakeSink implements ResourcePackService.Sink {
        final Set<UUID> online = new java.util.LinkedHashSet<>();
        final List<UUID> sent = new ArrayList<>();
        final List<String> kicks = new ArrayList<>();
        public Set<UUID> online() { return online; }
        public void send(UUID player, ResourcePackService.Pack pack) { sent.add(player); }
        public void kick(UUID player, String key) { kicks.add(key); }
    }

    FakeScheduler sched;
    FakeSink sink;
    int fetches;
    String sha;
    boolean down;
    Set<UUID> bedrock = new java.util.HashSet<>();
    int protocol = -1;
    Runnable duringFetch;
    ResourcePackService service;

    @BeforeEach
    void setUp() {
        sched = new FakeScheduler();
        sink = new FakeSink();
        fetches = 0;
        sha = "a".repeat(40);
        down = false;
        duringFetch = null;
        service = new ResourcePackService(() -> {
            fetches++;
            if (duringFetch != null) duringFetch.run();
            if (down) throw new Exception("down");
            return new ResourcePackService.Pack("https://x/p.zip", sha, ResourcePackService.PACK_ID);
        }, sched, new ClientInfo() {
            public int protocol(UUID u) { return protocol; }
            public boolean bedrock(UUID u) { return bedrock.contains(u); }
        }, sink);
    }

    @Test
    void batchesChangesIntoOneRefresh() {
        service.start();
        sched.advance(0);
        assertThat(fetches).isEqualTo(1);
        for (int i = 0; i < 30; i++) {
            service.changed();
            sched.advance(300);
        }
        assertThat(fetches).isEqualTo(1);
        sched.advance(10_000);
        assertThat(fetches).isEqualTo(2);
        sched.advance(60_000);
        assertThat(fetches).isEqualTo(2);
    }

    @Test
    void sendsOnlyWhenShaChanged() {
        UUID p = UUID.randomUUID();
        service.start();
        sched.advance(0);
        sink.online.add(p);
        service.changed();
        sched.advance(10_000);
        assertThat(sink.sent).isEmpty(); // same sha
        sha = "b".repeat(40);
        service.changed();
        sched.advance(10_000);
        assertThat(sink.sent).containsExactly(p);
    }

    @Test
    void bedrockIsSkipped() {
        UUID java = UUID.randomUUID();
        UUID bed = UUID.randomUUID();
        bedrock.add(bed);
        service.start();
        sched.advance(0);
        service.join(bed);
        service.join(java);
        assertThat(sink.sent).containsExactly(java);
        sink.online.add(java);
        sink.online.add(bed);
        sha = "c".repeat(40);
        service.changed();
        sched.advance(10_000);
        assertThat(sink.sent).containsExactly(java, java);
    }

    @Test
    void tooOldIsKickedAndNothingSent() {
        protocol = 771;
        service.start();
        sched.advance(0);
        service.join(UUID.randomUUID());
        assertThat(sink.kicks).containsExactly("too-old");
        assertThat(sink.sent).isEmpty();
    }

    @Test
    void retriesEverySixtySecondsUntilKnown() {
        down = true;
        service.start();
        sched.advance(0);
        assertThat(fetches).isEqualTo(1);
        UUID p = UUID.randomUUID();
        service.join(p);
        assertThat(sink.sent).isEmpty();
        sched.advance(60_000);
        assertThat(fetches).isEqualTo(2);
        down = false;
        sink.online.add(p);
        sched.advance(60_000);
        assertThat(fetches).isEqualTo(3);
        assertThat(sink.sent).containsExactly(p);
        sched.advance(120_000);
        assertThat(fetches).isEqualTo(3);
    }

    @Test
    void statusKicks() {
        UUID p = UUID.randomUUID();
        service.status(p, ResourcePackService.PACK_ID, org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.DECLINED);
        service.status(p, ResourcePackService.PACK_ID, org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD);
        service.status(p, ResourcePackService.PACK_ID, org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        service.status(p, UUID.randomUUID(), org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.DECLINED);
        assertThat(sink.kicks).containsExactly("declined", "failed");
    }

    @Test
    void aJoinRequestThatFailsKicks() {
        UUID p = UUID.randomUUID();
        service.start();
        sched.advance(0);
        service.join(p);
        service.status(p, ResourcePackService.PACK_ID, Status.FAILED_DOWNLOAD);
        assertThat(sink.kicks).containsExactly("failed");
    }

    @Test
    void aRefreshThatFailsKeepsThePlayer() {
        UUID p = UUID.randomUUID();
        service.start();
        sched.advance(0);
        service.join(p);
        service.status(p, ResourcePackService.PACK_ID, Status.SUCCESSFULLY_LOADED);
        sink.online.add(p);
        sha = "d".repeat(40);
        service.changed();
        sched.advance(10_000);
        assertThat(sink.sent).containsExactly(p, p);
        for (Status s : new Status[]{Status.FAILED_DOWNLOAD, Status.INVALID_URL, Status.FAILED_RELOAD, Status.DISCARDED}) {
            service.status(p, ResourcePackService.PACK_ID, s);
        }
        assertThat(sink.kicks).isEmpty();
        service.status(p, ResourcePackService.PACK_ID, Status.DECLINED);
        assertThat(sink.kicks).containsExactly("declined");
    }

    @Test
    void aRefreshBeforeTheJoinPackLoadedStillCountsAsTheJoinRequest() {
        UUID p = UUID.randomUUID();
        service.start();
        sched.advance(0);
        service.join(p);
        sink.online.add(p);
        sha = "e".repeat(40);
        service.changed();
        sched.advance(10_000);
        service.status(p, ResourcePackService.PACK_ID, Status.FAILED_DOWNLOAD);
        assertThat(sink.kicks).containsExactly("failed");
    }

    @Test
    void aRejoinNeedsTheNewJoinRequestToLoad() {
        UUID p = UUID.randomUUID();
        service.start();
        sched.advance(0);
        service.join(p);
        service.status(p, ResourcePackService.PACK_ID, Status.SUCCESSFULLY_LOADED);
        service.quit(p);
        service.join(p);
        service.status(p, ResourcePackService.PACK_ID, Status.FAILED_DOWNLOAD);
        assertThat(sink.kicks).containsExactly("failed");
    }

    @Test
    void aChangeDuringAFailedFetchIsNotPushedBackByTheRetry() {
        down = true;
        duringFetch = () -> service.changed();
        service.start();
        sched.advance(0);
        assertThat(fetches).isEqualTo(1);
        duringFetch = null;
        down = false;
        sched.advance(10_000);
        assertThat(fetches).isEqualTo(2);
        sched.advance(120_000);
        assertThat(fetches).isEqualTo(2);
    }

    @Test
    void aFailedFetchWithNothingPendingStillRetries() {
        down = true;
        service.start();
        sched.advance(0);
        service.changed();
        sched.advance(10_000);
        assertThat(fetches).isEqualTo(2);
        sched.advance(60_000);
        assertThat(fetches).isEqualTo(3);
    }
}
