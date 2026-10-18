package me.hektortm.woSSystems.player;

import com.google.gson.JsonObject;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;
import me.hektortm.wosCore.net.NetworkBus;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The safe hand-off of a player's session between servers that share player data.
 *
 * <p>With Velocity, when a player moves from server A to server B, B's login happens
 * <b>before</b> A sees the quit. wos-api keeps one lease per (player, realm): B takes it
 * before loading the session ({@link #acquire}). If A still holds it, B asks A over the
 * Redis bus ({@value #RELEASE}). A runs the player's "leaving" save on its main thread (the
 * writes its quit would make), freezes the player (later writes are dropped), waits until every
 * queued write has reached the API ({@code AsyncWriteQueue.barrier()}), releases the lease and
 * answers. B then takes the lease and loads data that is complete.</p>
 *
 * <p>Without the bus (no Redis) B only retries taking the lease for up to {@link #BUDGET}:
 * the holder may quit normally in that time ({@link #playerLeft}). After the budget the
 * login is refused rather than loading data that may still change.</p>
 *
 * <p>No lock is ever held across an HTTP call. Everything Bukkit-specific stays in
 * {@link PlayerSessions}; this class gets the lease calls, the bus, the write barrier, the
 * leaving save, the clock and the sleeper injected so it can be tested.</p>
 */
public final class Handoff {
    public static final String RELEASE = "handoff.release";
    static final Duration BUDGET = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    static final long RETRY_MS = 250;
    /** After releasing a player we still have online: how often to try to take them back (for as long as they stay frozen here). */
    static final long RECLAIM_EVERY_MS = 3_000;

    /** The lease routes of wos-api. */
    public interface Leases {
        /**
         * Takes the lease. Returns {@code ""} when this server holds it now, the holder's
         * server id when another live server holds it ({@code null} if the holder is unknown).
         * Throws on any other outcome (the caller retries).
         */
        String take(UUID uuid) throws Exception;

        /** Releases the lease (a no-op on the API side when this server does not hold it). */
        void release(UUID uuid) throws Exception;
    }

    /** The part of the Redis bus the hand-off uses. */
    public interface Bus {
        boolean isEnabled();

        CompletableFuture<JsonObject> request(String toServer, String type, JsonObject body, Duration timeout);

        void onRequest(String type, Function<JsonObject, CompletableFuture<JsonObject>> handler);

        static Bus of(NetworkBus bus) {
            return new Bus() {
                @Override public boolean isEnabled() { return bus.isEnabled(); }
                @Override public CompletableFuture<JsonObject> request(String to, String type, JsonObject body, Duration timeout) {
                    return bus.request(to, type, body, timeout);
                }
                @Override public void onRequest(String type, Function<JsonObject, CompletableFuture<JsonObject>> handler) {
                    bus.onRequest(type, handler);
                }
            };
        }
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Runs a task later (the reclaim tries); production uses the worker pool. */
    @FunctionalInterface
    public interface Scheduler {
        void schedule(Runnable task, long delayMs);
    }

    private final Leases leases;
    private final Bus bus;
    private final FrozenPlayers frozen;
    private final Supplier<CompletableFuture<Void>> barrier;
    private final Predicate<UUID> hasSession;
    private final Function<UUID, CompletableFuture<Void>> leavingSave;
    private final ScheduledExecutorService worker;
    private final Scheduler reclaims;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;
    private final Logger log;
    /** Per player: bumped whenever this server starts taking the lease (a quit's release must not drop it). */
    private final Map<UUID, AtomicLong> generations = new ConcurrentHashMap<>();
    /** Players whose quit-time writes already ran because another server took them over. */
    private final Set<UUID> leavingSaved = ConcurrentHashMap.newKeySet();

    /**
     * @param leavingSave runs the writes a quit would make for that player (on the main thread in
     *                    production) and completes when they are queued
     */
    public Handoff(Leases leases, Bus bus, FrozenPlayers frozen, Supplier<CompletableFuture<Void>> barrier,
                   Predicate<UUID> hasSession, Function<UUID, CompletableFuture<Void>> leavingSave,
                   ScheduledExecutorService worker, LongSupplier nanoClock, Sleeper sleeper, Logger log) {
        this(leases, bus, frozen, barrier, hasSession, leavingSave, worker,
                (task, delayMs) -> worker.schedule(task, delayMs, TimeUnit.MILLISECONDS), nanoClock, sleeper, log);
    }

    /** As above, with the scheduler of the reclaim tries given (tests run them by hand). */
    Handoff(Leases leases, Bus bus, FrozenPlayers frozen, Supplier<CompletableFuture<Void>> barrier,
            Predicate<UUID> hasSession, Function<UUID, CompletableFuture<Void>> leavingSave,
            ScheduledExecutorService worker, Scheduler reclaims, LongSupplier nanoClock, Sleeper sleeper, Logger log) {
        this.reclaims = reclaims;
        this.leases = leases;
        this.bus = bus;
        this.frozen = frozen;
        this.barrier = barrier;
        this.hasSession = hasSession;
        this.leavingSave = leavingSave;
        this.worker = worker;
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
        this.log = log;
    }

    /** The production wiring: real lease routes, the Core bus, real time, a small daemon pool. */
    public static Handoff create(WosApi api, NetworkBus bus, FrozenPlayers frozen,
                                 Supplier<CompletableFuture<Void>> barrier, Predicate<UUID> hasSession,
                                 Function<UUID, CompletableFuture<Void>> leavingSave, Logger log) {
        ScheduledExecutorService worker = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "wos-handoff");
            t.setDaemon(true);
            return t;
        });
        return new Handoff(new ApiLeases(api, log, System::nanoTime), Bus.of(bus), frozen, barrier, hasSession,
                leavingSave, worker, System::nanoTime, Thread::sleep, log);
    }

    /** Answers other servers' release requests. Call once at enable. */
    public void register() {
        bus.onRequest(RELEASE, this::onReleaseRequest);
    }

    private AtomicLong gen(UUID uuid) {
        return generations.computeIfAbsent(uuid, u -> new AtomicLong());
    }

    /** The current lease generation of a player (captured at quit). Lock-free: safe on the main thread. */
    public long generation(UUID uuid) {
        return gen(uuid).get();
    }

    /** True when the quit-time writes of this player already ran for a hand-off (the real quit skips them). */
    public boolean wasLeavingSaved(UUID uuid) {
        return leavingSaved.contains(uuid);
    }

    // ── taking the lease (new server) ────────────────────────────────────────

    /**
     * Takes this player's lease, blocking the calling (async pre-login) thread for at most
     * {@link #BUDGET}. True: this server holds the lease and may load the session. False: the
     * login must be refused.
     */
    public boolean acquire(UUID uuid, String name) {
        long deadline = nanoClock.getAsLong() + BUDGET.toNanos();
        // Bumped before the first POST: a quit-release of an older generation that runs while we
        // take the lease sees the change and does not leave the lease dropped (see releaseAfterQuit).
        gen(uuid).incrementAndGet();
        String asked = null;
        while (true) {
            String holder;
            try {
                holder = leases.take(uuid);
                if (holder != null && holder.isEmpty()) {
                    gen(uuid).incrementAndGet();
                    frozen.unfreeze(uuid);
                    leavingSaved.remove(uuid);
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                holder = null;
                log.fine("[Handoff] taking the lease of " + name + " failed, retrying: " + e.getMessage());
            }
            long leftMs = TimeUnit.NANOSECONDS.toMillis(deadline - nanoClock.getAsLong());
            if (leftMs <= 0) {
                log.warning("[Handoff] " + name + " (" + uuid + ") is still held"
                        + (holder == null ? "" : " by " + holder) + " after " + BUDGET.toSeconds() + " s; login refused");
                return false;
            }
            if (holder != null && !holder.equals(asked) && bus.isEnabled()) {
                asked = holder;
                askToRelease(holder, uuid, name, Math.min(REQUEST_TIMEOUT.toMillis(), leftMs));
                continue; // take again at once: the holder has released (or failed to answer)
            }
            try {
                sleeper.sleep(Math.min(RETRY_MS, leftMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void askToRelease(String holder, UUID uuid, String name, long timeoutMs) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid.toString());
        try {
            // The bus times the request out itself; the extra margin only guards against a bus that does not.
            JsonObject answer = bus.request(holder, RELEASE, body, Duration.ofMillis(timeoutMs))
                    .get(timeoutMs + 500, TimeUnit.MILLISECONDS);
            if (answer != null && answer.has("ok") && !answer.get("ok").getAsBoolean()) {
                log.info("[Handoff] " + holder + " could not release " + name + " yet; retrying");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            log.info("[Handoff] " + holder + " did not release " + name + ": " + cause);
        }
    }

    // ── giving the lease away (old server) ───────────────────────────────────

    /**
     * Bus handler: another server takes this player over. Never blocks the dispatcher thread.
     * Chain: leaving save (main thread) → freeze → barrier (write thread) → DELETE ({@link #worker}).
     * Answers {@code {"ok":true}}; when the DELETE fails, unfreezes (this server still holds the
     * lease, so writing is right) and answers {@code {"ok":false}}. With no session here (an old
     * lease after a crash) it only releases the lease and freezes nothing.
     */
    CompletableFuture<JsonObject> onReleaseRequest(JsonObject body) {
        UUID uuid;
        try {
            uuid = UUID.fromString(body.get("uuid").getAsString());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("bad uuid in release request"));
        }
        boolean online = hasSession.test(uuid);
        CompletableFuture<Void> saved;
        if (online) {
            saved = leavingSave.apply(uuid).handle((v, e) -> {
                if (e != null) log.warning("[Handoff] the leaving save of " + uuid + " failed: " + e);
                else leavingSaved.add(uuid);
                frozen.freeze(uuid); // after the leaving writes were queued: they still go out
                return null;
            });
        } else {
            saved = CompletableFuture.completedFuture(null);
        }
        return saved
                .thenCompose(v -> barrier.get())
                .thenRunAsync(() -> {
                    try {
                        leases.release(uuid);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, worker)
                .handle((v, e) -> {
                    JsonObject answer = new JsonObject();
                    if (e != null) {
                        frozen.unfreeze(uuid);
                        // Still our player: the real quit must run the temp removal and quest save again.
                        leavingSaved.remove(uuid);
                        log.warning("[Handoff] could not release " + uuid + " (writes resume here): " + e);
                        answer.addProperty("ok", false);
                        return answer;
                    }
                    if (online) scheduleReclaim(uuid);
                    answer.addProperty("ok", true);
                    return answer;
                });
    }

    /**
     * Safety net: the other server's login can still fail after we released (its session load
     * fails, another plugin refuses it), and then Velocity keeps the player here. While the
     * player is still online here and frozen, try to take the lease back every
     * {@value #RECLAIM_EVERY_MS} ms, with no limit: a frozen player whose writes are dropped must
     * not be left so. Stops when the take succeeds (unfreeze), or when the player has left this
     * server or is no longer frozen (then whoever holds the lease holds it rightly).
     */
    private void scheduleReclaim(UUID uuid) {
        reclaims.schedule(() -> {
            if (!hasSession.test(uuid) || !frozen.isFrozen(uuid)) return;
            try {
                String holder = leases.take(uuid);
                if (holder != null && holder.isEmpty()) {
                    gen(uuid).incrementAndGet();
                    frozen.unfreeze(uuid);
                    leavingSaved.remove(uuid);
                    log.warning("[Handoff] " + uuid + " stayed on this server (the switch failed): lease taken back, writes resume");
                    return;
                }
            } catch (Exception e) {
                log.fine("[Handoff] reclaim of " + uuid + " failed: " + e.getMessage());
            }
            scheduleReclaim(uuid);
        }, RECLAIM_EVERY_MS);
    }

    /**
     * The player left this server (main thread, after the session was unloaded): the freeze and
     * the leaving mark end here (they only cover "released but still connected"), and the lease
     * is released once every queued write is done.
     */
    public CompletableFuture<Void> playerLeft(UUID uuid) {
        long generation = generation(uuid);
        frozen.unfreeze(uuid);
        leavingSaved.remove(uuid);
        return releaseAfterQuit(uuid, generation);
    }

    /**
     * Once every queued write is done, releases the lease — unless this server started taking it
     * again since {@code generation} (the player came back). No lock across the DELETE: when a
     * take started while we released, the lease is taken back right after.
     */
    public CompletableFuture<Void> releaseAfterQuit(UUID uuid, long generation) {
        return barrier.get().thenRunAsync(() -> {
            AtomicLong g = gen(uuid);
            if (g.get() != generation) return;
            try {
                leases.release(uuid);
            } catch (Exception e) {
                log.warning("[Handoff] could not release the lease of " + uuid + ": " + e.getMessage());
                return;
            }
            if (g.get() != generation) {
                try {
                    leases.take(uuid); // a login here began meanwhile: keep the lease ours
                } catch (Exception e) {
                    log.warning("[Handoff] could not take back the lease of " + uuid + ": " + e.getMessage());
                }
            }
        }, worker);
    }

    /** Shutdown: releases the leases of every player still here, waiting at most {@code max}. Call after the write queue is flushed. */
    public void releaseAllNow(Collection<UUID> uuids, Duration max) {
        List<CompletableFuture<Void>> all = uuids.stream()
                .map(u -> CompletableFuture.runAsync(() -> {
                    try {
                        leases.release(u);
                    } catch (Exception e) {
                        log.warning("[Handoff] could not release the lease of " + u + " at shutdown: " + e.getMessage());
                    }
                }, worker))
                .toList();
        try {
            CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).get(max.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.warning("[Handoff] not every lease was released at shutdown: " + e);
        }
    }

    public void shutdown() {
        worker.shutdownNow();
    }

    // ── the real lease routes ────────────────────────────────────────────────

    /** {@code POST/DELETE /v1/players/{uuid}/lease}; the realm comes from the server's token. */
    static final class ApiLeases implements Leases {
        private static final long WARN_EVERY_NANOS = TimeUnit.MINUTES.toNanos(1);

        private final WosApi api;
        private final Logger log;
        private final LongSupplier nanoClock;
        private final AtomicLong lastNoRouteWarn = new AtomicLong(Long.MIN_VALUE);

        ApiLeases(WosApi api, Logger log, LongSupplier nanoClock) {
            this.api = api;
            this.log = log;
            this.nanoClock = nanoClock;
        }

        @Override
        public String take(UUID uuid) throws ApiException {
            try {
                api.send("POST", "/v1/players/" + uuid + "/lease", null);
                return "";
            } catch (ApiException e) {
                if (e.status() == 404 || e.status() == 405) {
                    // An API from before the lease routes: nothing to hand off, load as before.
                    warnNoRoute(e.status());
                    return "";
                }
                if (e.status() != 409) throw e;
                return holderOf(e);
            }
        }

        private void warnNoRoute(int status) {
            long now = nanoClock.getAsLong();
            long last = lastNoRouteWarn.get();
            if (last != Long.MIN_VALUE && now - last < WARN_EVERY_NANOS) return;
            if (!lastNoRouteWarn.compareAndSet(last, now)) return;
            log.warning("[Handoff] wos-api has no lease route (" + status + "): the session hand-off is OFF, "
                    + "players load without waiting for the server they come from");
        }

        @Override
        public void release(UUID uuid) throws ApiException {
            api.send("DELETE", "/v1/players/" + uuid + "/lease", null);
        }

        /**
         * The holder's server id from a 409: the body's {@code held_by}. Fallback for an answer
         * without it: the API's message "the player is held by server &lt;id&gt;", which WosApi
         * appends as " (&lt;message&gt;)". Null when it can't be read.
         */
        static String holderOf(ApiException e) {
            if (!"lease_held".equals(e.code())) return null;
            String fromBody = e.field("held_by").map(String::trim).orElse("");
            if (!fromBody.isEmpty()) return fromBody;
            String msg = e.getMessage();
            String marker = "held by server ";
            int at = msg == null ? -1 : msg.lastIndexOf(marker);
            if (at < 0) return null;
            String rest = msg.substring(at + marker.length());
            if (rest.endsWith(")")) rest = rest.substring(0, rest.length() - 1);
            rest = rest.trim();
            return rest.isEmpty() ? null : rest;
        }
    }
}
