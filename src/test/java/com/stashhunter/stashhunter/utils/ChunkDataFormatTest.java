package com.stashhunter.stashhunter.utils;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkDataFormatTest {
    @Test
    void roundTrips() {
        ChunkPos pos = new ChunkPos(-1875, 42);
        assertEquals("[-1875, 42]", ChunkDataFormat.format(pos));
        assertEquals(pos, ChunkDataFormat.parse(ChunkDataFormat.format(pos)));
    }

    @Test
    void readsLinesWrittenByOlderVersions() {
        // Older versions wrote ChunkPos.toString(), which is the same "[x, z]" shape.
        assertEquals(new ChunkPos(3, -7), ChunkDataFormat.parse("[3, -7]"));
        assertEquals(new ChunkPos(3, -7), ChunkDataFormat.parse("  [3,-7]  "));
    }

    @Test
    void rejectsBrokenLines() {
        assertNull(ChunkDataFormat.parse(""));
        assertNull(ChunkDataFormat.parse("[3]"));
        assertNull(ChunkDataFormat.parse("[3, 4, 5]"));
        assertNull(ChunkDataFormat.parse("[a, 4]"));
        assertNull(ChunkDataFormat.parse("ChunkPos[x=3, z=4]"));
        assertNull(ChunkDataFormat.parse("[3, 4"));
    }
}
