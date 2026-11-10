package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NaturalStructuresTest {
    @Test
    void nearIsPerDimensionAndThreeDimensional() {
        NaturalStructures structures = new NaturalStructures(100);
        structures.add("Overworld", new BlockPos(100, 20, 100));

        assertTrue(structures.isNear("Overworld", new BlockPos(110, 25, 95), 16));
        assertFalse(structures.isNear("Overworld", new BlockPos(100, 70, 100), 16), "surface far above the dungeon");
        assertFalse(structures.isNear("Nether", new BlockPos(100, 20, 100), 16), "dimensions are separate");
        assertFalse(structures.isNear("Overworld", new BlockPos(100, 20, 100), 0), "radius 0 disables the check");
    }

    @Test
    void addDedupes() {
        NaturalStructures structures = new NaturalStructures(100);
        assertTrue(structures.add("Overworld", new BlockPos(1, 2, 3)));
        assertFalse(structures.add("Overworld", new BlockPos(1, 2, 3)));
        assertEquals(1, structures.size("Overworld"));
    }

    @Test
    void evictsOldestOverCapacity() {
        NaturalStructures structures = new NaturalStructures(2);
        structures.add("Overworld", new BlockPos(0, 0, 0));
        structures.add("Overworld", new BlockPos(1000, 0, 0));
        structures.add("Overworld", new BlockPos(2000, 0, 0));

        assertEquals(2, structures.size("Overworld"));
        assertFalse(structures.isNear("Overworld", new BlockPos(0, 0, 0), 8));
        assertTrue(structures.isNear("Overworld", new BlockPos(2000, 0, 0), 8));
    }

    @Test
    void evictsLeastRecentlyUsed() {
        NaturalStructures structures = new NaturalStructures(2);
        structures.add("Overworld", new BlockPos(0, 0, 0));
        structures.add("Overworld", new BlockPos(1000, 0, 0));
        assertTrue(structures.isNear("Overworld", new BlockPos(2, 0, 0), 8), "use the oldest entry");
        structures.add("Overworld", new BlockPos(2000, 0, 0));

        assertTrue(structures.isNear("Overworld", new BlockPos(0, 0, 0), 8), "recently used entry stays");
        assertFalse(structures.isNear("Overworld", new BlockPos(1000, 0, 0), 8), "unused entry is evicted");
    }

    @Test
    void addingAKnownPositionCountsAsUse() {
        NaturalStructures structures = new NaturalStructures(2);
        structures.add("Overworld", new BlockPos(0, 0, 0));
        structures.add("Overworld", new BlockPos(1000, 0, 0));
        assertFalse(structures.add("Overworld", new BlockPos(0, 0, 0)));
        structures.add("Overworld", new BlockPos(2000, 0, 0));

        assertTrue(structures.isNear("Overworld", new BlockPos(0, 0, 0), 8));
        assertFalse(structures.isNear("Overworld", new BlockPos(1000, 0, 0), 8));
    }

    @Test
    void clearForgetsEverything() {
        NaturalStructures structures = new NaturalStructures(10);
        structures.add("End", new BlockPos(5, 60, 5));
        structures.clear();
        assertFalse(structures.isNear("End", new BlockPos(5, 60, 5), 8));
    }
}
