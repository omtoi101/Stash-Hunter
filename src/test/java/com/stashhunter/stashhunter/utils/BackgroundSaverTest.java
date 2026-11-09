package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundSaverTest {
    private final List<Integer> written = new CopyOnWriteArrayList<>();
    private final List<String> threads = new CopyOnWriteArrayList<>();
    private BackgroundSaver<Integer> saver;

    @AfterEach
    void stop() {
        if (saver != null) saver.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    void writesOnItsOwnThread() {
        saver = new BackgroundSaver<>("test-saver", value -> {
            threads.add(Thread.currentThread().getName());
            written.add(value);
        });
        saver.save(1);
        assertTrue(saver.flush(1, TimeUnit.SECONDS));
        assertEquals(List.of(1), written);
        assertEquals(List.of("test-saver"), threads);
    }

    @Test
    void saveDoesNotWaitForASlowWrite() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        saver = new BackgroundSaver<>("test-saver", value -> {
            await(release);
            written.add(value);
        });
        long start = System.nanoTime();
        saver.save(1);
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(tookMs < 500, "save() blocked for " + tookMs + " ms");
        assertTrue(written.isEmpty());

        release.countDown();
        assertTrue(saver.flush(1, TimeUnit.SECONDS));
        assertEquals(List.of(1), written);
    }

    @Test
    void savesDuringAWriteCollapseIntoTheNewest() throws InterruptedException {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        saver = new BackgroundSaver<>("test-saver", value -> {
            writing.countDown();
            await(release);
            written.add(value);
        });
        saver.save(1);
        assertTrue(writing.await(1, TimeUnit.SECONDS));
        saver.save(2);
        saver.save(3);
        saver.save(4);
        release.countDown();

        assertTrue(saver.flush(1, TimeUnit.SECONDS));
        // In order, and the stale 2 and 3 are never written.
        assertEquals(List.of(1, 4), written);
    }

    @Test
    void failedWriteKeepsTheThreadForTheNextSave() {
        saver = new BackgroundSaver<>("test-saver", value -> {
            if (value == 1) throw new IllegalStateException("disk full");
            written.add(value);
        });
        saver.save(1);
        assertTrue(saver.flush(1, TimeUnit.SECONDS));
        saver.save(2);
        assertTrue(saver.flush(1, TimeUnit.SECONDS));
        assertEquals(List.of(2), written);
    }

    @Test
    void shutdownWritesWhatIsQueued() throws InterruptedException {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        saver = new BackgroundSaver<>("test-saver", value -> {
            writing.countDown();
            await(release);
            written.add(value);
        });
        saver.save(1);
        assertTrue(writing.await(1, TimeUnit.SECONDS));
        saver.save(2);
        release.countDown();

        saver.shutdown(1, TimeUnit.SECONDS);
        assertEquals(List.of(1, 2), written);
    }

    @Test
    void saveAfterShutdownWritesOnTheCallersThread() {
        saver = new BackgroundSaver<>("test-saver", value -> {
            threads.add(Thread.currentThread().getName());
            written.add(value);
        });
        saver.shutdown(1, TimeUnit.SECONDS);
        saver.save(7);
        assertEquals(List.of(7), written);
        assertEquals(List.of(Thread.currentThread().getName()), threads);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
