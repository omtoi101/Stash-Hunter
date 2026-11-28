package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class EntitySignalIndexTest {
    private static final UUID VILLAGER = new UUID(0, 1);
    private static final UUID MULE = new UUID(0, 2);

    private static EntitySignalIndex.Seen seen(UUID id, int x, int z, SignalKind kind) {
        return new EntitySignalIndex.Seen(id, new BlockPos(x, 64, z), kind);
    }

    @Test
    void movedEntityKeepsOneSignal() {
        EntitySignalIndex index = new EntitySignalIndex(100);
        index.update("Overworld", List.of(seen(VILLAGER, 8, 8, SignalKind.TRADED_VILLAGER)), key -> false);
        // Walked into the next chunk; the old chunk is far away and not cleared.
        index.update("Overworld", List.of(seen(VILLAGER, 24, 8, SignalKind.TRADED_VILLAGER)), key -> false);
        List<Signal> near = index.near("Overworld", 16, 8, 64);
        assertEquals(List.of(new Signal(new BlockPos(24, 64, 8), SignalKind.TRADED_VILLAGER)), near);
    }

    @Test
    void unseenEntityInClearedChunkIsDropped() {
        EntitySignalIndex index = new EntitySignalIndex(100);
        index.update("Overworld", List.of(seen(VILLAGER, 8, 8, SignalKind.TRADED_VILLAGER)), key -> false);
        long chunk = ChunkPos.pack(0, 0);
        index.update("Overworld", List.of(), key -> key == chunk);
        assertTrue(index.near("Overworld", 8, 8, 64).isEmpty());
    }

    @Test
    void unseenEntityOutsideClearedChunksStays() {
        EntitySignalIndex index = new EntitySignalIndex(100);
        index.update("Overworld", List.of(seen(VILLAGER, 8, 8, SignalKind.TRADED_VILLAGER)), key -> false);
        index.update("Overworld", List.of(), key -> key == ChunkPos.pack(5, 5));
        assertEquals(1, index.near("Overworld", 8, 8, 64).size());
    }

    @Test
    void seenEntityInClearedChunkStays() {
        EntitySignalIndex index = new EntitySignalIndex(100);
        index.update("Overworld", List.of(seen(VILLAGER, 8, 8, SignalKind.TRADED_VILLAGER)), key -> true);
        assertEquals(1, index.near("Overworld", 8, 8, 64).size());
    }

    @Test
    void clearingOnlyTouchesTheGivenDimension() {
        EntitySignalIndex index = new EntitySignalIndex(100);
        index.update("Nether", List.of(seen(MULE, 8, 8, SignalKind.CHESTED_ANIMAL)), key -> false);
        index.update("Overworld", List.of(), key -> true);
        assertEquals(1, index.near("Nether", 8, 8, 64).size());
        assertTrue(index.near("Overworld", 8, 8, 64).isEmpty());
    }

    @Test
    void nearUsesHorizontalDistance() {
        EntitySignalIndex index = new EntitySignalIndex(100);
        index.update("Overworld", List.of(seen(VILLAGER, 100, 0, SignalKind.TRADED_VILLAGER)), key -> false);
        assertTrue(index.near("Overworld", 0, 0, 99).isEmpty());
        assertEquals(1, index.near("Overworld", 0, 0, 100).size());
    }

    @Test
    void oldestEntriesAreEvictedBeyondTheCap() {
        EntitySignalIndex index = new EntitySignalIndex(1);
        index.update("Overworld", List.of(seen(VILLAGER, 0, 0, SignalKind.TRADED_VILLAGER)), key -> false);
        index.update("Overworld", List.of(seen(MULE, 1, 0, SignalKind.CHESTED_ANIMAL)), key -> false);
        assertEquals(Set.of(new Signal(new BlockPos(1, 64, 0), SignalKind.CHESTED_ANIMAL)),
                Set.copyOf(index.near("Overworld", 0, 0, 64)));
    }
}
