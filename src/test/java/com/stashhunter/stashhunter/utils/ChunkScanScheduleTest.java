package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkScanScheduleTest {
    @Test
    void scannedChunkIsSkippedUntilTheRescanInterval() {
        ChunkScanSchedule schedule = new ChunkScanSchedule(100);
        assertTrue(schedule.isDue("Overworld", 0, 0, 0));

        schedule.markScanned("Overworld", 0, 0, 0);
        assertFalse(schedule.isDue("Overworld", 0, 0, 99));
        assertTrue(schedule.isDue("Overworld", 0, 0, 100));
    }

    @Test
    void invalidatedChunkIsDueAtOnce() {
        ChunkScanSchedule schedule = new ChunkScanSchedule(100);
        schedule.markScanned("Overworld", 3, -2, 0);
        schedule.invalidate("Overworld", 3, -2);
        assertTrue(schedule.isDue("Overworld", 3, -2, 1));
    }

    @Test
    void dimensionsAndChunksAreSeparate() {
        ChunkScanSchedule schedule = new ChunkScanSchedule(100);
        schedule.markScanned("Overworld", 0, 0, 0);
        assertTrue(schedule.isDue("Nether", 0, 0, 1));
        assertTrue(schedule.isDue("Overworld", 1, 0, 1));
    }

    @Test
    void pruneDropsOnlyEntriesThatAreDueAnyway() {
        ChunkScanSchedule schedule = new ChunkScanSchedule(100);
        schedule.markScanned("Overworld", 0, 0, 0);
        schedule.markScanned("Overworld", 1, 0, 50);

        schedule.prune(120);
        assertEquals(1, schedule.size());
        assertFalse(schedule.isDue("Overworld", 1, 0, 120));
    }
}
