package com.stashhunter.stashhunter.utils;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpiralWaypointsTest {
    @Test
    void spiralsOutwardFromTheCenter() {
        List<Vec3> waypoints = SpiralWaypoints.generate(1000, -500, 200, 100, 160);

        assertEquals(List.of(
            new Vec3(1000, 160, -500),
            new Vec3(1100, 160, -500), new Vec3(1100, 160, -400),
            new Vec3(900, 160, -400), new Vec3(900, 160, -600),
            new Vec3(1200, 160, -600), new Vec3(1200, 160, -300),
            new Vec3(800, 160, -300), new Vec3(800, 160, -700),
            new Vec3(1200, 160, -700)
        ), waypoints);
        assertEquals(waypoints.size(), SpiralWaypoints.count(200, 100));
    }

    @Test
    void outerRingIsClampedToTheRadius() {
        List<Vec3> waypoints = SpiralWaypoints.generate(0, 0, 250, 100, 160);

        assertEquals(SpiralWaypoints.count(250, 100), waypoints.size());
        for (Vec3 w : waypoints) {
            assertTrue(Math.abs(w.x) <= 250 && Math.abs(w.z) <= 250, "outside radius: " + w);
        }
        // The last ring sits on the radius so the far edge gets covered.
        assertEquals(new Vec3(-250, 160, -250), waypoints.get(waypoints.size() - 2));
        assertEquals(new Vec3(250, 160, -250), waypoints.getLast());
    }

    @Test
    void consecutiveLegsAreAxisAligned() {
        List<Vec3> waypoints = SpiralWaypoints.generate(0, 0, 1000, 128, 160);
        for (int i = 1; i < waypoints.size(); i++) {
            Vec3 a = waypoints.get(i - 1);
            Vec3 b = waypoints.get(i);
            assertTrue(a.x == b.x || a.z == b.z, "diagonal leg " + a + " -> " + b);
        }
    }

    @Test
    void radiusSmallerThanGapGivesOneRing() {
        assertEquals(List.of(
            new Vec3(0, 160, 0),
            new Vec3(50, 160, 0), new Vec3(50, 160, 50),
            new Vec3(-50, 160, 50), new Vec3(-50, 160, -50),
            new Vec3(50, 160, -50)
        ), SpiralWaypoints.generate(0, 0, 50, 128, 160));
    }

    @Test
    void rejectsNonPositiveArguments() {
        assertThrows(IllegalArgumentException.class, () -> SpiralWaypoints.generate(0, 0, 100, 0, 160));
        assertThrows(IllegalArgumentException.class, () -> SpiralWaypoints.generate(0, 0, 0, 100, 160));
    }
}
