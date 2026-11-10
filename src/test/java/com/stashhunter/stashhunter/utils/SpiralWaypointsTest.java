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
    }

    @Test
    void outerRingIsClampedToTheRadius() {
        // 60 blocks beyond the second ring: more than its pass scans outward (50), so a third ring is needed.
        List<Vec3> waypoints = SpiralWaypoints.generate(0, 0, 260, 100, 160);

        assertEquals(14, waypoints.size());
        for (Vec3 w : waypoints) {
            assertTrue(Math.abs(w.x) <= 260 && Math.abs(w.z) <= 260, "outside radius: " + w);
        }
        assertEquals(new Vec3(-260, 160, -260), waypoints.get(waypoints.size() - 2));
        assertEquals(new Vec3(260, 160, -260), waypoints.getLast());
    }

    @Test
    void skipsAnOuterRingTheScanAlreadyCovers() {
        // The second ring at 200 scans out to 250; a third ring clamped to 250 would add nothing.
        List<Vec3> waypoints = SpiralWaypoints.generate(0, 0, 250, 100, 160);

        assertEquals(10, waypoints.size());
        assertEquals(new Vec3(200, 160, -200), waypoints.getLast());
    }

    @Test
    void scanReachesTheRadiusWithoutASpareRing() {
        for (int gap : new int[] {50, 64, 101, 128, 500}) {
            for (int radius = 50; radius <= 3000; radius += 37) {
                List<Vec3> waypoints = SpiralWaypoints.generate(0, 0, radius, gap, 160);
                double outer = waypoints.stream()
                    .mapToDouble(w -> Math.max(Math.abs(w.x), Math.abs(w.z))).max().orElseThrow();
                long rings = (waypoints.size() - 2) / 4;
                String at = " (radius " + radius + ", gap " + gap + ")";

                assertTrue(outer <= radius, "flies outside the radius" + at);
                assertTrue(outer + gap / 2.0 >= radius, "scan stops short of the radius" + at);
                assertTrue(rings == 1 || (rings - 1) * gap + gap / 2.0 < radius, "one ring fewer would do" + at);
            }
        }
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
