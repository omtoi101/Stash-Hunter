package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.StashHunter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Writes snapshots on one background thread, in the order they were handed over, so a slow disk
 * (fsync on an HDD) never stalls the caller. Only the newest snapshot still waiting is written: a
 * burst of saves while a write runs costs one more write, not one per save.
 * <p>
 * Snapshots must not change after {@link #save}; the writer reads them on its own thread.
 */
public final class BackgroundSaver<T> {
    private final String name;
    private final Consumer<T> writer;
    private final ExecutorService executor;
    // The newest snapshot no write has picked up yet. Non-null means a write task is queued.
    private final AtomicReference<T> pending = new AtomicReference<>();

    public BackgroundSaver(String name, Consumer<T> writer) {
        this.name = name;
        this.writer = writer;
        // Daemon: never keeps the JVM alive. The shutdown hook drains it through shutdown().
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Queues the snapshot; returns at once. After {@link #shutdown} it is written on the caller's thread. */
    public void save(T snapshot) {
        if (pending.getAndSet(snapshot) != null) return; // the queued task will pick up this newer one
        try {
            executor.execute(this::writePending);
        } catch (RejectedExecutionException e) {
            writePending();
        }
    }

    private void writePending() {
        T snapshot = pending.getAndSet(null);
        if (snapshot == null) return;
        try {
            writer.accept(snapshot);
        } catch (RuntimeException e) {
            // Keep the thread for the next save; the writer logs its own I/O errors.
            StashHunter.LOG.warn("{}: write failed", name, e);
        }
    }

    /** Waits until every snapshot handed to {@link #save} so far is written. False on timeout. */
    public boolean flush(long timeout, TimeUnit unit) {
        try {
            // Single thread, FIFO: once this marker ran, every earlier write has finished.
            Future<?> marker = executor.submit(() -> {});
            marker.get(timeout, unit);
            return true;
        } catch (RejectedExecutionException e) {
            writePending();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    /**
     * Writes what is still queued and stops the thread. Waits at most {@code timeout}; a write still
     * stuck then is abandoned (the target keeps its previous version, see {@link AtomicFiles}).
     */
    public void shutdown(long timeout, TimeUnit unit) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                StashHunter.LOG.warn("{}: last write didn't finish in time", name);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        // A save racing with shutdown() may have been accepted after the last task ran.
        writePending();
    }
}
