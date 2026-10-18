package me.hektortm.woSSystems.database;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.wosCore.discord.DiscordLog;
import me.hektortm.wosCore.discord.DiscordLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Shared single-thread executor for all database writes.
 *
 * Why single-thread:
 *  - SQLite allows only one concurrent writer; a pool would just queue anyway
 *  - Guarantees write order per entity (no stale overwrites from race conditions)
 *  - Zero contention — no locks needed in the DAOs
 *
 * Usage:
 *   AsyncWriteQueue.submit(() -> myDao.persist(key, value));
 */
public final class AsyncWriteQueue {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wos-db-writer");
        t.setDaemon(false); // keep JVM alive until pending writes finish
        return t;
    });

    private AsyncWriteQueue() {}

    public static void submit(ThrowingRunnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                DiscordLogger.log(new DiscordLog(
                        Level.SEVERE,
                        WoSSystems.getPlugin(WoSSystems.class),
                        "AWQ:001",
                        "Async DB write failed: ",
                        e
                ));
            }
        });
    }

    /**
     * Completes when every write queued before this call has finished (the marker runs on the
     * same single thread, so it runs after them). Used by the session hand-off: a player's
     * lease is released only after their last write reached the API. After {@link #shutdown}
     * (which already waited for the queue) it completes at once.
     */
    public static CompletableFuture<Void> barrier() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            executor.execute(() -> done.complete(null));
        } catch (RejectedExecutionException e) {
            done.complete(null);
        }
        return done;
    }

    /**
     * Call on server shutdown. Waits up to 15 seconds for pending writes to finish
     * before giving up, so no data is silently lost on a clean stop.
     */
    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                WoSSystems.getPlugin(WoSSystems.class).getLogger()
                        .warning("[AsyncWriteQueue] Timed out waiting for pending writes — some data may not have been saved.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
