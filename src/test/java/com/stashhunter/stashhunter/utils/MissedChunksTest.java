package com.stashhunter.stashhunter.utils;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MissedChunksTest {
    @Test
    void returnsAChunkOnceItIsLoaded() {
        MissedChunks missed = new MissedChunks(100, 64);
        missed.add("Overworld", 3, -4, 0);

        assertTrue(missed.takeLoaded("Overworld", key -> false).isEmpty());
        assertEquals(List.of(ChunkPos.pack(3, -4)), missed.takeLoaded("Overworld", key -> true));
        assertEquals(0, missed.size());
        assertTrue(missed.takeLoaded("Overworld", key -> true).isEmpty(), "returned only once");
    }

    @Test
    void dimensionsAreSeparate() {
        MissedChunks missed = new MissedChunks(100, 64);
        missed.add("Overworld", 0, 0, 0);

        assertTrue(missed.takeLoaded("Nether", key -> true).isEmpty());
        assertEquals(1, missed.size());
    }

    @Test
    void expiresByTheFirstMiss() {
        MissedChunks missed = new MissedChunks(100, 64);
        missed.add("Overworld", 0, 0, 0);
        missed.add("Overworld", 0, 0, 90); // still missing on a later pass: keeps its first miss time
        missed.add("Overworld", 1, 0, 50);

        missed.expire(99);
        assertEquals(2, missed.size());
        missed.expire(100);
        assertEquals(1, missed.size());
        assertEquals(1, missed.takeDropped());
        assertEquals(0, missed.takeDropped(), "the count resets");
        assertEquals(List.of(ChunkPos.pack(1, 0)), missed.takeLoaded("Overworld", key -> true));
    }

    @Test
    void evictsTheOldestBeyondTheLimit() {
        MissedChunks missed = new MissedChunks(100, 2);
        missed.add("Overworld", 0, 0, 0);
        missed.add("Overworld", 1, 0, 1);
        missed.add("Overworld", 2, 0, 2);

        assertEquals(2, missed.size());
        assertEquals(1, missed.takeDropped());
        assertEquals(List.of(ChunkPos.pack(1, 0), ChunkPos.pack(2, 0)), missed.takeLoaded("Overworld", key -> true));
    }

    @Test
    void clearIsNotCountedAsDropped() {
        MissedChunks missed = new MissedChunks(100, 64);
        missed.add("Overworld", 0, 0, 0);
        missed.clear();

        assertEquals(0, missed.size());
        assertEquals(0, missed.takeDropped());
    }
}
