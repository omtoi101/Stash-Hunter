package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StashGrowthTest {
    @Test
    void findsEveryReportedStashTheClusterOverlaps() {
        Set<BlockPos> first = Set.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0));
        Set<BlockPos> elsewhere = Set.of(new BlockPos(500, 64, 0));
        Set<BlockPos> second = Set.of(new BlockPos(20, 64, 0));

        List<BlockPos> merged = List.of(new BlockPos(1, 64, 0), new BlockPos(10, 64, 0), new BlockPos(20, 64, 0));

        assertEquals(List.of(0, 2), StashGrowth.overlapping(List.of(first, elsewhere, second), merged));
        assertTrue(StashGrowth.overlapping(List.of(elsewhere), merged).isEmpty());
    }

    @Test
    void ignoresSmallChangesButReportsRealGrowth() {
        assertFalse(StashGrowth.grewEnough(10, 12), "two more chests is not worth a second report");
        assertTrue(StashGrowth.grewEnough(10, 13));
        assertFalse(StashGrowth.grewEnough(100, 115), "large stash: needs 20 % more");
        assertTrue(StashGrowth.grewEnough(100, 120));
        assertFalse(StashGrowth.grewEnough(14, 10), "shrinking never re-reports");
    }
}
