package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DungeonFilterTest {
    private static final BlockPos SPAWNER = new BlockPos(100, 30, 100);

    @Test
    void spawnerWithTwoChestsIsADungeon() {
        BlockPos a = new BlockPos(96, 30, 100);
        BlockPos b = new BlockPos(100, 30, 104);
        assertTrue(DungeonFilter.isDungeonOnly(List.of(SPAWNER, a, b), Set.of(SPAWNER), Set.of(a, b)));
    }

    @Test
    void loneSpawnersAreNatural() {
        BlockPos other = new BlockPos(120, 25, 110);
        assertTrue(DungeonFilter.isDungeonOnly(List.of(SPAWNER, other), Set.of(SPAWNER, other), Set.of()));
    }

    @Test
    void thirdChestIsNotExplainedByOneSpawner() {
        BlockPos a = new BlockPos(96, 30, 100);
        BlockPos b = new BlockPos(100, 30, 104);
        BlockPos c = new BlockPos(104, 30, 96);
        assertFalse(DungeonFilter.isDungeonOnly(List.of(SPAWNER, a, b, c), Set.of(SPAWNER), Set.of(a, b, c)));
    }

    @Test
    void chestOutsideTheRoomIsNotExplained() {
        BlockPos far = new BlockPos(106, 30, 100);
        BlockPos above = new BlockPos(100, 33, 101);
        assertFalse(DungeonFilter.isDungeonOnly(List.of(SPAWNER, far), Set.of(SPAWNER), Set.of(far)));
        assertFalse(DungeonFilter.isDungeonOnly(List.of(SPAWNER, above), Set.of(SPAWNER), Set.of(above)));
    }

    @Test
    void otherStashBlockMakesItACandidate() {
        BlockPos hopper = new BlockPos(101, 30, 100);
        assertFalse(DungeonFilter.isDungeonOnly(List.of(SPAWNER, hopper), Set.of(SPAWNER), Set.of()));
    }

    @Test
    void clusterWithoutSpawnerIsNeverADungeon() {
        BlockPos a = new BlockPos(96, 30, 100);
        assertFalse(DungeonFilter.isDungeonOnly(List.of(a), Set.of(), Set.of(a)));
        assertFalse(DungeonFilter.isDungeonOnly(List.of(a), Set.of(SPAWNER), Set.of(a)));
        assertFalse(DungeonFilter.isDungeonOnly(new ArrayList<>(), Set.of(SPAWNER), Set.of()));
    }

    @Test
    void spawnerFarmWithManyContainersIsACandidate() {
        List<BlockPos> cluster = new ArrayList<>();
        cluster.add(SPAWNER);
        for (int i = 0; i < 9; i++) cluster.add(new BlockPos(98 + i, 28, 98));
        assertFalse(DungeonFilter.isDungeonOnly(cluster, Set.of(SPAWNER), Set.copyOf(cluster.subList(1, 10))));
    }
}
