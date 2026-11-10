package com.stashhunter.stashhunter.utils;

import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class SlimeChunksTest {
    @Test
    void matchesVanilla() {
        long[] seeds = {0L, 1L, -1L, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE, "stash".hashCode()};
        // Includes coordinates far enough out that the int products overflow, like vanilla's do.
        int[] coords = {-30_000_000 / 16, -5000, -1, 0, 1, 7, 4096, 30_000_000 / 16};
        for (long seed : seeds) {
            for (int x : coords) {
                for (int z : coords) {
                    boolean vanilla = WorldgenRandom.seedSlimeChunk(x, z, seed, 987234911L).nextInt(10) == 0;
                    assertEquals(vanilla, SlimeChunks.isSlimeChunk(seed, x, z), "seed " + seed + " at " + x + ", " + z);
                }
            }
        }
    }

    @Test
    void matchesVanillaOnRandomInputs() {
        SplittableRandom rng = new SplittableRandom(0x5114EL);
        for (int i = 0; i < 100_000; i++) {
            long seed = rng.nextLong();
            int x = rng.nextInt(-1_875_000, 1_875_001);
            int z = rng.nextInt(-1_875_000, 1_875_001);
            boolean vanilla = WorldgenRandom.seedSlimeChunk(x, z, seed, 987234911L).nextInt(10) == 0;
            assertEquals(vanilla, SlimeChunks.isSlimeChunk(seed, x, z), "seed " + seed + " at " + x + ", " + z);
        }
    }

    @Test
    void roughlyOneInTen() {
        int hits = 0;
        for (int x = -50; x < 50; x++) {
            for (int z = -50; z < 50; z++) {
                if (SlimeChunks.isSlimeChunk(42L, x, z)) hits++;
            }
        }
        assertTrue(hits > 800 && hits < 1200, "hits: " + hits);
    }

    @Test
    void parsesSeedsLikeTheCreateWorldScreen() {
        assertEquals(-4172144997902289642L, SlimeChunks.parseSeed(" -4172144997902289642 ").getAsLong());
        assertEquals("stash".hashCode(), SlimeChunks.parseSeed("stash").getAsLong());
        assertTrue(SlimeChunks.parseSeed("").isEmpty());
        assertTrue(SlimeChunks.parseSeed("   ").isEmpty());
        assertTrue(SlimeChunks.parseSeed(null).isEmpty());
    }
}
