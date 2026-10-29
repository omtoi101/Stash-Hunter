package com.stashhunter.stashhunter.utils;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TrailAnalysisTest {
    @Test
    void noTrailWithoutEnoughNewChunks() {
        assertNull(TrailAnalysis.analyze(Vec3.ZERO, chunk -> false));
        Set<ChunkPos> two = Set.of(new ChunkPos(1, 0), new ChunkPos(2, 0));
        assertNull(TrailAnalysis.analyze(Vec3.ZERO, two::contains));
    }

    @Test
    void findsAStraightLineOfNewChunks() {
        Set<ChunkPos> line = Set.of(new ChunkPos(1, 0), new ChunkPos(2, 0), new ChunkPos(3, 0), new ChunkPos(4, 0));

        TrailAnalysis.Trail trail = TrailAnalysis.analyze(Vec3.ZERO, line::contains);

        assertNotNull(trail);
        assertTrue(trail.confidence > 0.9, "confidence " + trail.confidence);
        assertEquals(1.0, Math.abs(trail.direction.x), 1e-9, "trail runs along X");
        assertEquals(0.0, trail.direction.z, 1e-9);
    }

    @Test
    void ignoresChunksOutsideTheAnalysisRadius() {
        int far = TrailAnalysis.TRAIL_ANALYSIS_RADIUS + 1;
        Set<ChunkPos> farLine = Set.of(new ChunkPos(far, 0), new ChunkPos(far + 1, 0), new ChunkPos(far + 2, 0));
        assertNull(TrailAnalysis.analyze(Vec3.ZERO, farLine::contains));
    }
}
