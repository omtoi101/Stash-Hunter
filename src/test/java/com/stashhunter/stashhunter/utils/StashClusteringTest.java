package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StashClusteringTest {
    @Test
    void separatesGroupsFartherApartThanTheLinkDistance() {
        List<BlockPos> blocks = new ArrayList<>();
        for (int i = 0; i < 5; i++) blocks.add(new BlockPos(i, 64, 0));
        for (int i = 0; i < 5; i++) blocks.add(new BlockPos(500 + i, 64, 0));

        List<List<BlockPos>> clusters = StashClustering.cluster(blocks, 10, 32);

        assertEquals(2, clusters.size());
        assertEquals(5, clusters.get(0).size());
        assertEquals(5, clusters.get(1).size());
    }

    @Test
    void capsAChainAtTheMaximumRadius() {
        // A row of blocks 2 apart is fully linked, but must not become one 200-block cluster.
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x <= 200; x += 2) blocks.add(new BlockPos(x, 64, 0));

        List<List<BlockPos>> clusters = StashClustering.cluster(blocks, 5, 32);

        assertTrue(clusters.size() > 1);
        int total = 0;
        for (List<BlockPos> cluster : clusters) {
            total += cluster.size();
            int minX = cluster.stream().mapToInt(BlockPos::getX).min().orElseThrow();
            int maxX = cluster.stream().mapToInt(BlockPos::getX).max().orElseThrow();
            assertTrue(maxX - minX <= 64, "cluster wider than 2 * maxRadius: " + (maxX - minX));
        }
        assertEquals(blocks.size(), total, "every block belongs to exactly one cluster");
    }

    @Test
    void boundingBoxVolumeCountsWholeBlocks() {
        assertEquals(1.0, StashClustering.boundingBoxVolume(List.of(new BlockPos(3, 3, 3))));
        assertEquals(2.0 * 3.0 * 4.0,
            StashClustering.boundingBoxVolume(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 2, 3))));
    }

    @Test
    void centerIsEmptyForNoBlocks() {
        assertTrue(StashClustering.center(List.of()).isEmpty());
        assertEquals(new BlockPos(5, 10, 0),
            StashClustering.center(List.of(new BlockPos(0, 10, 0), new BlockPos(10, 10, 0))).orElseThrow());
    }
}
