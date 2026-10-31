package com.stashhunter.stashhunter.utils;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaypointPathTest {
    // 100 east, then 300 south; the altitude doesn't count.
    private static final List<Vec3> PATH = List.of(
        new Vec3(0, 160, 0), new Vec3(100, 200, 0), new Vec3(100, 160, 300));

    @Test
    void lengthIsHorizontal() {
        assertEquals(400, WaypointPath.length(PATH), 1e-9);
        assertEquals(0, WaypointPath.length(List.of()), 1e-9);
        assertEquals(0, WaypointPath.length(PATH.subList(0, 1)), 1e-9);
    }

    @Test
    void flownCountsFinishedLegsAndThePartOfTheCurrentOne() {
        // Heading for the first waypoint: the approach isn't part of the path.
        assertEquals(0, WaypointPath.flown(PATH, 0, -500, 0), 1e-9);
        // 40 blocks into the first leg.
        assertEquals(40, WaypointPath.flown(PATH, 1, 40, 0), 1e-9);
        // First leg done, 120 blocks into the second.
        assertEquals(220, WaypointPath.flown(PATH, 2, 100, 120), 1e-9);
        // All waypoints reached.
        assertEquals(400, WaypointPath.flown(PATH, 3, 0, 0), 1e-9);
    }

    @Test
    void strayingOffTheLegNeverGoesBelowItsStart() {
        // Farther from the target than the leg is long (e.g. after a detour): no credit, but no loss either.
        assertEquals(100, WaypointPath.flown(PATH, 2, 2000, 2000), 1e-9);
    }

    @Test
    void halfTheSpiralsWaypointsIsFarLessThanHalfItsFlight() {
        List<Vec3> spiral = SpiralWaypoints.generate(0, 0, 3000, 128, 160);
        int half = spiral.size() / 2;
        Vec3 at = spiral.get(half - 1);

        double share = WaypointPath.flown(spiral, half, at.x, at.z) / WaypointPath.length(spiral);
        assertTrue(share < 0.3, "share " + share);
    }
}
