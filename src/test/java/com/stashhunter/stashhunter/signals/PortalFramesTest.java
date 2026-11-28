package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class PortalFramesTest {
    /** Frame in the X-Y plane (outer min corner x0,y0,z0), inner width w, inner height h, no corners. */
    private static Set<BlockPos> frameX(int x0, int y0, int z0, int w, int h) {
        Set<BlockPos> s = new HashSet<>();
        for (int i = 1; i <= w; i++) {
            s.add(new BlockPos(x0 + i, y0, z0));
            s.add(new BlockPos(x0 + i, y0 + h + 1, z0));
        }
        for (int j = 1; j <= h; j++) {
            s.add(new BlockPos(x0, y0 + j, z0));
            s.add(new BlockPos(x0 + w + 1, y0 + j, z0));
        }
        return s;
    }

    private static Set<BlockPos> frameZ(int x0, int y0, int z0, int w, int h) {
        Set<BlockPos> s = new HashSet<>();
        for (int i = 1; i <= w; i++) {
            s.add(new BlockPos(x0, y0, z0 + i));
            s.add(new BlockPos(x0, y0 + h + 1, z0 + i));
        }
        for (int j = 1; j <= h; j++) {
            s.add(new BlockPos(x0, y0 + j, z0));
            s.add(new BlockPos(x0, y0 + j, z0 + w + 1));
        }
        return s;
    }

    @Test
    void minimalPortalFrameIsFound() {
        List<BlockPos> frames = PortalFrames.frames(frameX(10, 60, 5, 2, 3));
        assertEquals(List.of(new BlockPos(10, 60, 5)), frames);
    }

    @Test
    void zPlaneFrameIsFound() {
        List<BlockPos> frames = PortalFrames.frames(frameZ(10, 60, 5, 3, 4));
        assertEquals(List.of(new BlockPos(10, 60, 5)), frames);
    }

    @Test
    void frameWithGapIsNotFound() {
        Set<BlockPos> s = frameX(0, 0, 0, 2, 3);
        s.remove(new BlockPos(0, 2, 0));
        assertTrue(PortalFrames.frames(s).isEmpty());
    }

    @Test
    void randomObsidianBlobIsNotAFrame() {
        Set<BlockPos> s = new HashSet<>();
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++) s.add(new BlockPos(x, y, z));
        assertTrue(PortalFrames.frames(s).isEmpty());
    }

    @Test
    void twoSeparateFramesAreBothReported() {
        Set<BlockPos> s = frameX(0, 0, 0, 2, 3);
        s.addAll(frameZ(100, 10, 100, 2, 3));
        assertEquals(2, PortalFrames.frames(s).size());
    }
}
