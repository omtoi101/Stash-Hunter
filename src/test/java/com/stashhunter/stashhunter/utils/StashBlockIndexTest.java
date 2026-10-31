package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StashBlockIndexTest {
    @Test
    void keepsBlocksFromEarlierScansSoASplitStashIsSeenWhole() {
        StashBlockIndex<String> index = new StashBlockIndex<>(100);
        // Two halves of one stash on either side of a chunk border, scanned on different passes.
        index.update("Overworld", 0, 0, Map.of(new BlockPos(15, 64, 5), "chest"));
        index.update("Overworld", 1, 0, Map.of(new BlockPos(16, 64, 5), "chest"));

        assertEquals(2, index.near("Overworld", 16, 5, 32).size());
    }

    @Test
    void rescanReplacesTheChunkAndEmptyResultForgetsIt() {
        StashBlockIndex<String> index = new StashBlockIndex<>(100);
        index.update("Overworld", 0, 0, Map.of(new BlockPos(1, 64, 1), "chest", new BlockPos(2, 64, 1), "chest"));
        index.update("Overworld", 0, 0, Map.of(new BlockPos(1, 64, 1), "chest"));
        assertEquals(1, index.near("Overworld", 8, 8, 16).size());

        index.update("Overworld", 0, 0, Map.of());
        assertEquals(0, index.chunkCount());
    }

    @Test
    void separatesDimensionsAndFiltersByDistance() {
        StashBlockIndex<String> index = new StashBlockIndex<>(100);
        index.update("Overworld", 0, 0, Map.of(new BlockPos(1, 64, 1), "chest"));
        index.update("Nether", 0, 0, Map.of(new BlockPos(1, 64, 1), "barrel"));
        index.update("Overworld", 100, 0, Map.of(new BlockPos(1601, 64, 1), "chest"));

        Map<BlockPos, String> near = index.near("Overworld", 8, 8, 64);
        assertEquals(1, near.size());
        assertEquals("chest", near.get(new BlockPos(1, 64, 1)));
        assertTrue(index.near("End", 8, 8, 64).isEmpty());
    }

    @Test
    void largeIndexFindsExactlyTheChunksWithinRadius() {
        // Small radius over a big index probes the chunk square instead of walking every entry.
        StashBlockIndex<String> index = new StashBlockIndex<>(10_000);
        for (int cx = -40; cx <= 40; cx++) {
            for (int cz = -40; cz <= 40; cz++) {
                index.update("Overworld", cx, cz, Map.of(new BlockPos(cx * 16 + 3, 64, cz * 16 + 7), cx + "," + cz));
            }
        }
        int radius = 40;
        for (double[] q : new double[][] {{0, 0}, {-17.5, 9}, {-8, -8}, {23, -31}, {-24, 8}}) {
            Map<BlockPos, String> expected = new HashMap<>();
            for (int cx = -40; cx <= 40; cx++) {
                for (int cz = -40; cz <= 40; cz++) {
                    double dx = cx * 16 + 8 - q[0], dz = cz * 16 + 8 - q[1];
                    if (dx * dx + dz * dz <= radius * radius) expected.put(new BlockPos(cx * 16 + 3, 64, cz * 16 + 7), cx + "," + cz);
                }
            }
            assertEquals(expected, index.near("Overworld", q[0], q[1], radius));
        }
    }

    @Test
    void evictsTheChunkScannedLongestAgo() {
        StashBlockIndex<String> index = new StashBlockIndex<>(2);
        index.update("Overworld", 0, 0, Map.of(new BlockPos(1, 64, 1), "a"));
        index.update("Overworld", 1, 0, Map.of(new BlockPos(17, 64, 1), "b"));
        index.update("Overworld", 0, 0, Map.of(new BlockPos(1, 64, 1), "a")); // rescanned: now the newest
        index.update("Overworld", 2, 0, Map.of(new BlockPos(33, 64, 1), "c"));

        Map<BlockPos, String> all = index.near("Overworld", 0, 0, 1000);
        assertEquals(2, all.size());
        assertFalse(all.containsValue("b"));
    }
}
