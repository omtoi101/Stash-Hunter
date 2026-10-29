package com.stashhunter.stashhunter.utils;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridWaypointsTest {
    @Test
    void walksStripsInAlternatingDirections() {
        List<Vec3> waypoints = GridWaypoints.generate(0, 0, 200, 1000, 100, 160);

        assertEquals(List.of(
            new Vec3(0, 160, 0), new Vec3(0, 160, 1000),
            new Vec3(100, 160, 1000), new Vec3(100, 160, 0),
            new Vec3(200, 160, 0), new Vec3(200, 160, 1000)
        ), waypoints);
    }

    @Test
    void cornersInAnyOrderGiveTheSameGrid() {
        assertEquals(GridWaypoints.generate(0, 0, 200, 1000, 100, 160),
            GridWaypoints.generate(200, 1000, 0, 0, 100, 160));
    }

    @Test
    void countMatchesGenerate() {
        for (int width : new int[] {50, 128, 333, 500}) {
            assertEquals(GridWaypoints.generate(-1234, 0, 5678, 100, width, 160).size(),
                GridWaypoints.count(-1234, 5678, width), "stripWidth " + width);
        }
    }

    @Test
    void rejectsNonPositiveStripWidth() {
        assertThrows(IllegalArgumentException.class, () -> GridWaypoints.generate(0, 0, 100, 100, 0, 160));
        assertThrows(IllegalArgumentException.class, () -> GridWaypoints.generate(0, 0, 100, 100, -5, 160));
    }
}
