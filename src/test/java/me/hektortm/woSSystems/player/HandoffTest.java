package me.hektortm.woSSystems.player;

import com.google.gson.JsonObject;
import me.hektortm.woSSystems.database.AsyncWriteQueue;
import me.hektortm.wosCore.api.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class HandoffTest {
    private static final UUID PLAYER = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID OTHER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final Logger LOG = Logger.getLogger("HandoffTest");

    /** Shared, ordered log of what reached "the API": writes and lease calls. */
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private final FrozenPlayers frozen = new FrozenPlayers(LOG);
    private final FakeClock clock = new FakeClock();
    private final FakeLeases leases = new FakeLeases();
    private final FakeBus bus = new FakeBus();
    private final Set<UUID> sessions = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void stop() {
        worker.shutdownNow();
    }

    /** What the "leaving save" does (in game: temp unlockables removed, quest runs saved). */
    private Function<UUID, CompletableFuture<Void>> leavingSave = uuid -> CompletableFuture.completedFuture(null);

    private Handoff handoff() {
        return new Handoff(leases, bus, frozen, AsyncWriteQueue::barrier, sessions::contains,
                uuid -> leavingSave.apply(uuid), worker, clock::nanos, clock::sleep, LOG);
    }

    private Handoff handoff(Handoff.Scheduler reclaims) {
        return new Handoff(leases, bus, frozen, AsyncWriteQueue::barrier, sessions::contains,
                uuid -> leavingSave.apply(uuid), worker, reclaims, clock::nanos, clock::sleep, LOG);
    }

    private ApiWriter writer(CountDownLatch gate) {
        return new ApiWriter((method, path, body, headers) -> {
            if (gate != null) gate.await(5, TimeUnit.SECONDS);
            calls.add(method + " " + path);
            return null;
        }, LOG, frozen);
    }

    private static JsonObject releaseBody(UUID uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        return o;
    }

    // ── old server: release ──

    @Test
    void writesBeforeReleaseAreFlushed() throws Exception {
        sessions.add(PLAYER);
        Handoff h = handoff();
        h.register();
        CountDownLatch slowWrite = new CountDownLatch(1);
        writer(slowWrite).put("/v1/players/" + PLAYER + "/stats/kills", Map.of("value", 3));

        CompletableFuture<JsonObject> reply = bus.handler(Handoff.RELEASE).apply(releaseBody(PLAYER));
        // The answer comes from the bus handler at once (not blocking the dispatcher) but
        // completes only after the queued write: the write is still held by the gate.
        Thread.sleep(200);
        assertThat(reply).isNotDone();
        assertThat(calls).doesNotContain("DELETE lease " + PLAYER);

        slowWrite.countDown();
        JsonObject answer = reply.get(5, TimeUnit.SECONDS);
        assertThat(answer.get("ok").getAsBoolean()).isTrue();
        assertThat(calls).containsExactly(
                "PUT /v1/players/" + PLAYER + "/stats/kills",
                "DELETE lease " + PLAYER);
    }

    @Test
    void frozenSessionDropsWrites() throws Exception {
        ApiWriter w = writer(null);
        frozen.freeze(PLAYER);

        w.put("/v1/players/" + PLAYER + "/stats/kills", Map.of("value", 1));
        w.delete("/v1/players/" + PLAYER + "/unlockables?temp=true");
        w.postOnce("/v1/economy/transactions", ApiWriter.body("uuid", PLAYER.toString(), "currency", "gold", "delta", 5));
        w.postOnce("/v1/economy/transfers", ApiWriter.body("from", OTHER.toString(), "to", PLAYER.toString(), "amount", 5));
        w.put("/v1/players/" + OTHER + "/stats/kills", Map.of("value", 2));      // another player: still written
        w.put("/v1/server/globalstats/kills", Map.of("value", 9));               // not a player write
        AsyncWriteQueue.barrier().get(5, TimeUnit.SECONDS);

        // Economy deltas pass (idempotent, cannot overwrite; dropping them loses currency).
        assertThat(calls).containsExactly(
                "POST /v1/economy/transactions",
                "POST /v1/economy/transfers",
                "PUT /v1/players/" + OTHER + "/stats/kills",
                "PUT /v1/server/globalstats/kills");

        frozen.unfreeze(PLAYER); // the session is loaded here again
        w.put("/v1/players/" + PLAYER + "/stats/kills", Map.of("value", 1));
        AsyncWriteQueue.barrier().get(5, TimeUnit.SECONDS);
        assertThat(calls).contains("PUT /v1/players/" + PLAYER + "/stats/kills");
    }

    @Test
    void releaseRunsLeavingSaveBeforeFreeze() throws Exception {
        sessions.add(PLAYER);
        ApiWriter w = writer(null);
        leavingSave = uuid -> {
            w.delete("/v1/players/" + uuid + "/unlockables?temp=true");          // quit's temp removal
            w.put("/v1/players/" + uuid + "/quests/intro", Map.of("status", "running")); // quest save
            return CompletableFuture.completedFuture(null);
        };
        Handoff h = handoff();
        h.register();

        JsonObject answer = bus.handler(Handoff.RELEASE).apply(releaseBody(PLAYER)).get(5, TimeUnit.SECONDS);
        assertThat(answer.get("ok").getAsBoolean()).isTrue();
        assertThat(calls).containsExactly(
                "DELETE /v1/players/" + PLAYER + "/unlockables?temp=true",
                "PUT /v1/players/" + PLAYER + "/quests/intro",
                "DELETE lease " + PLAYER);
        assertThat(h.wasLeavingSaved(PLAYER)).isTrue();   // the real quit skips these writes
        assertThat(frozen.isFrozen(PLAYER)).isTrue();
    }

    @Test
    void freezeEndsWhenPlayerLeaves() throws Exception {
        sessions.add(PLAYER);
        Handoff h = handoff();
        h.register();
        bus.handler(Handoff.RELEASE).apply(releaseBody(PLAYER)).get(5, TimeUnit.SECONDS);
        assertThat(frozen.isFrozen(PLAYER)).isTrue();

        sessions.remove(PLAYER);
        h.playerLeft(PLAYER).get(5, TimeUnit.SECONDS);
        assertThat(frozen.isFrozen(PLAYER)).isFalse();
        assertThat(h.wasLeavingSaved(PLAYER)).isFalse();

        // e.g. a nickname set for the (now offline) player later is written again
        writer(null).put("/v1/players/" + PLAYER + "/nickname", Map.of("nickname", "x"));
        AsyncWriteQueue.barrier().get(5, TimeUnit.SECONDS);
        assertThat(calls).contains("PUT /v1/players/" + PLAYER + "/nickname");
    }

    @Test
    void failedReleaseUnfreezes() throws Exception {
        sessions.add(PLAYER);
        leases.failRelease = true;
        Handoff h = handoff();
        h.register();
        JsonObject answer = bus.handler(Handoff.RELEASE).apply(releaseBody(PLAYER)).get(5, TimeUnit.SECONDS);
        assertThat(answer.get("ok").getAsBoolean()).isFalse();
        assertThat(frozen.isFrozen(PLAYER)).isFalse(); // still our lease: writes go on
        // The leaving save ran, but the player stays ours: the real quit must run those writes again.
        assertThat(h.wasLeavingSaved(PLAYER)).isFalse();
    }

    @Test
    void reclaimKeepsTryingWhileFrozenAndOnline() throws Exception {
        sessions.add(PLAYER);
        leases.always("server-b"); // the other server holds it (its login is still going on)
        FakeScheduler scheduler = new FakeScheduler();
        Handoff h = handoff(scheduler);
        h.register();
        JsonObject answer = bus.handler(Handoff.RELEASE).apply(releaseBody(PLAYER)).get(5, TimeUnit.SECONDS);
        assertThat(answer.get("ok").getAsBoolean()).isTrue();

        // Far more than the old limit of 10 tries: still trying every 3 s while the player is here and frozen.
        for (int i = 0; i < 25; i++) {
            assertThat(scheduler.delays).as("try %d is scheduled", i).hasSize(i + 1);
            assertThat(scheduler.delays.get(i)).isEqualTo(Handoff.RECLAIM_EVERY_MS);
            scheduler.runNext();
            assertThat(frozen.isFrozen(PLAYER)).isTrue();
        }

        leases.script(""); // the other server's login failed and released: the take succeeds
        scheduler.runNext();
        assertThat(frozen.isFrozen(PLAYER)).isFalse();
        assertThat(h.wasLeavingSaved(PLAYER)).isFalse();
        assertThat(scheduler.pending()).isZero(); // stops after taking it back
    }

    @Test
    void reclaimStopsWhenThePlayerLeaves() throws Exception {
        sessions.add(PLAYER);
        leases.always("server-b");
        FakeScheduler scheduler = new FakeScheduler();
        Handoff h = handoff(scheduler);
        h.register();
        bus.handler(Handoff.RELEASE).apply(releaseBody(PLAYER)).get(5, TimeUnit.SECONDS);
        scheduler.runNext();
        assertThat(scheduler.pending()).isEqualTo(1);

        sessions.remove(PLAYER); // the switch went through: the player is on server-b now
        int takes = leases.takes();
        scheduler.runNext();
        assertThat(scheduler.pending()).isZero();
        assertThat(leases.takes()).isEqualTo(takes); // no take for a player who left
    }

    @Test
    void releaseWithoutSessionStillReleases() throws Exception {
        Handoff h = handoff();
        h.register();
        JsonObject answer = bus.handler(Handoff.RELEASE).apply(releaseBody(PLAYER)).get(5, TimeUnit.SECONDS);
        assertThat(answer.get("ok").getAsBoolean()).isTrue();
        assertThat(calls).containsExactly("DELETE lease " + PLAYER);
        assertThat(frozen.isFrozen(PLAYER)).isFalse(); // nobody here to freeze: nothing would ever unfreeze it
    }

    @Test
    void generationIsReadWithoutWaitingForATake() throws Exception {
        CountDownLatch inTake = new CountDownLatch(1);
        CountDownLatch finishTake = new CountDownLatch(1);
        Handoff h = new Handoff(new Handoff.Leases() {
            @Override public String take(UUID uuid) throws Exception {
                inTake.countDown();
                finishTake.await(5, TimeUnit.SECONDS); // a slow POST
                return "";
            }
            @Override public void release(UUID uuid) { }
        }, bus, frozen, AsyncWriteQueue::barrier, sessions::contains, uuid -> CompletableFuture.completedFuture(null),
                worker, clock::nanos, clock::sleep, LOG);
        Thread login = new Thread(() -> h.acquire(PLAYER, "Steve"));
        login.start();
        assertThat(inTake.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Long> read = CompletableFuture.supplyAsync(() -> h.generation(PLAYER));
        assertThat(read.get(1, TimeUnit.SECONDS)).isEqualTo(1L); // the main thread never waits on the network
        finishTake.countDown();
        login.join(5000);
    }

    @Test
    void quitReleaseSkipsWhenTakenAgain() throws Exception {
        Handoff h = handoff();
        leases.script("");
        long genAtQuit = h.generation(PLAYER);
        assertThat(h.acquire(PLAYER, "Steve")).isTrue(); // came back before the quit's release ran
        h.releaseAfterQuit(PLAYER, genAtQuit).get(5, TimeUnit.SECONDS);
        assertThat(calls).containsExactly("POST lease " + PLAYER);

        h.releaseAfterQuit(PLAYER, h.generation(PLAYER)).get(5, TimeUnit.SECONDS);
        assertThat(calls).containsExactly("POST lease " + PLAYER, "DELETE lease " + PLAYER);
    }

    // ── new server: acquire ──

    @Test
    void noHolderLoadsAtOnce() {
        leases.script("");
        assertThat(handoff().acquire(PLAYER, "Steve")).isTrue();
        assertThat(bus.requests).isEmpty();
        assertThat(clock.sleeps).isEmpty();
        assertThat(calls).containsExactly("POST lease " + PLAYER);
    }

    @Test
    void holderReleasesThenLoginProceeds() {
        leases.script("server-a", "");
        bus.answer = (to, body) -> {
            calls.add("ask " + to + " " + body.get("uuid").getAsString());
            JsonObject ok = new JsonObject();
            ok.addProperty("ok", true);
            return CompletableFuture.completedFuture(ok);
        };
        assertThat(handoff().acquire(PLAYER, "Steve")).isTrue();
        assertThat(calls).containsExactly("POST lease " + PLAYER, "ask server-a " + PLAYER, "POST lease " + PLAYER);
        assertThat(bus.timeouts).containsExactly(Handoff.REQUEST_TIMEOUT);
    }

    @Test
    void timeoutRefusesLogin() {
        leases.always("server-a");
        bus.answer = (to, body) -> {
            clock.advance(Handoff.REQUEST_TIMEOUT.toMillis()); // the holder never answers
            return CompletableFuture.failedFuture(new TimeoutException());
        };
        assertThat(handoff().acquire(PLAYER, "Steve")).isFalse();
        assertThat(bus.requests).containsExactly("server-a"); // asked once, then only retried
        assertThat(clock.elapsedMs()).isBetween(Handoff.BUDGET.toMillis(), Handoff.BUDGET.toMillis() + Handoff.RETRY_MS);
    }

    @Test
    void busDisabledRetriesUntilHolderQuits() {
        bus.enabled = false;
        leases.script("server-a", "server-a", "server-a", "");
        assertThat(handoff().acquire(PLAYER, "Steve")).isTrue();
        assertThat(bus.requests).isEmpty();
        assertThat(clock.sleeps).containsExactly(Handoff.RETRY_MS, Handoff.RETRY_MS, Handoff.RETRY_MS);
    }

    @Test
    void nonConflictStatusIsRetried() {
        leases.script(new ApiException(500, "internal", "lost race"), new ApiException(0, "unreachable", "down"), "");
        assertThat(handoff().acquire(PLAYER, "Steve")).isTrue();
        assertThat(bus.requests).isEmpty();
        assertThat(clock.sleeps).hasSize(2);
    }

    @Test
    void holderIsReadFromTheConflictMessage() {
        ApiException conflict = new ApiException(409, "lease_held",
                "POST /v1/players/" + PLAYER + "/lease -> 409 (the player is held by server hub-1)");
        assertThat(Handoff.ApiLeases.holderOf(conflict)).isEqualTo("hub-1");
        assertThat(Handoff.ApiLeases.holderOf(new ApiException(409, "http_409", "x"))).isNull();
    }

    @Test
    void holderIsReadFromTheBody() {
        JsonObject body = new JsonObject();
        body.addProperty("held_by", "hub-2");
        // The body's held_by wins; the message is only a fallback for answers without it.
        ApiException conflict = new ApiException(409, "lease_held",
                "POST /v1/players/" + PLAYER + "/lease -> 409 (the player is held by server old-text)", body);
        assertThat(Handoff.ApiLeases.holderOf(conflict)).isEqualTo("hub-2");

        JsonObject blank = new JsonObject();
        blank.addProperty("held_by", " ");
        assertThat(Handoff.ApiLeases.holderOf(new ApiException(409, "lease_held",
                "x -> 409 (the player is held by server hub-1)", blank))).isEqualTo("hub-1");
    }

    // ── fakes ──

    private static final class FakeClock {
        private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
        private final long start = nanos.get();
        final List<Long> sleeps = Collections.synchronizedList(new ArrayList<>());

        long nanos() { return nanos.get(); }

        void sleep(long ms) {
            sleeps.add(ms);
            advance(ms);
        }

        void advance(long ms) { nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(ms)); }

        long elapsedMs() { return TimeUnit.NANOSECONDS.toMillis(nanos.get() - start); }
    }

    private final class FakeLeases implements Handoff.Leases {
        private final Deque<Object> script = new ArrayDeque<>();
        private Object always;

        void script(Object... outcomes) { script.addAll(List.of(outcomes)); }

        void always(Object outcome) { always = outcome; }

        private int takes;

        synchronized int takes() { return takes; }

        @Override
        public synchronized String take(UUID uuid) throws Exception {
            takes++;
            calls.add("POST lease " + uuid);
            Object next = script.isEmpty() ? always : script.poll();
            if (next instanceof Exception e) throw e;
            return (String) next;
        }

        @Override
        public void release(UUID uuid) throws Exception {
            if (failRelease) throw new ApiException(0, "unreachable", "down");
            calls.add("DELETE lease " + uuid);
        }

        volatile boolean failRelease;
    }

    /** Holds the scheduled reclaim tries; the test runs them one by one (no real 3 s waits). */
    private static final class FakeScheduler implements Handoff.Scheduler {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        final List<Long> delays = new ArrayList<>();

        @Override
        public synchronized void schedule(Runnable task, long delayMs) {
            tasks.add(task);
            delays.add(delayMs);
        }

        synchronized int pending() { return tasks.size(); }

        void runNext() {
            Runnable next;
            synchronized (this) {
                next = tasks.poll();
            }
            assertThat(next).as("a scheduled reclaim").isNotNull();
            next.run();
        }
    }

    private interface Answer {
        CompletableFuture<JsonObject> apply(String to, JsonObject body);
    }

    private static final class FakeBus implements Handoff.Bus {
        boolean enabled = true;
        Answer answer = (to, body) -> CompletableFuture.failedFuture(new IllegalStateException("no answer"));
        final List<String> requests = new ArrayList<>();
        final List<Duration> timeouts = new ArrayList<>();
        private final Map<String, Function<JsonObject, CompletableFuture<JsonObject>>> handlers = new ConcurrentHashMap<>();

        Function<JsonObject, CompletableFuture<JsonObject>> handler(String type) { return handlers.get(type); }

        @Override public boolean isEnabled() { return enabled; }

        @Override
        public CompletableFuture<JsonObject> request(String toServer, String type, JsonObject body, Duration timeout) {
            requests.add(toServer);
            timeouts.add(timeout);
            return answer.apply(toServer, body);
        }

        @Override
        public void onRequest(String type, Function<JsonObject, CompletableFuture<JsonObject>> handler) {
            handlers.put(type, handler);
        }
    }
}
