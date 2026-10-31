package com.stashhunter.stashhunter.utils;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Square spiral outward from a center: rings gap apart, so the area nearest the start (spawn,
 * a portal) is scanned first. Only corners are emitted, the controller flies straight between them.
 * Each pass is taken to scan gap/2 to either side (gap = 2 x scan-radius), so the scan reaches the
 * radius while the flight itself stays inside it.
 */
public final class SpiralWaypoints {
    private SpiralWaypoints() {}

    // East, south (+Z), west, north: clockwise as seen on the map.
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, 1, 0, -1};

    public static List<Vec3> generate(int centerX, int centerZ, int radius, int gap, double altitude) {
        // Stepping by gap, so anything <= 0 would never terminate.
        if (gap <= 0) throw new IllegalArgumentException("gap must be positive, got " + gap);
        if (radius <= 0) throw new IllegalArgumentException("radius must be positive, got " + radius);

        int rings = rings(radius, gap);
        List<Vec3> waypoints = new ArrayList<>();
        waypoints.add(new Vec3(centerX, altitude, centerZ));

        long x = 0;
        long z = 0;
        // Legs grow 1,1,2,2,3,3... gaps; 4*rings legs end at the north-west corner of the last ring.
        for (int leg = 0; leg < 4 * rings; leg++) {
            int dir = leg % 4;
            long length = (long) (leg / 2 + 1) * gap;
            x += DX[dir] * length;
            z += DZ[dir] * length;
            waypoints.add(corner(centerX, centerZ, x, z, radius, altitude));
        }
        // Close the outer ring's north side, otherwise it would stay one gap inside the other three.
        x += (long) 2 * rings * gap;
        waypoints.add(corner(centerX, centerZ, x, z, radius, altitude));
        return waypoints;
    }

    // Fewest rings whose last one, plus the gap/2 its pass scans outward, reaches the radius. Rounding
    // up to radius/gap instead often added a ring only a few blocks outside the previous one: up to
    // 8 x radius blocks of flight (24 km at radius 3000) for ground the scan already had.
    private static int rings(int radius, int gap) {
        long needed = ((long) 2 * radius + gap - 1) / (2L * gap);
        return (int) Math.max(1, needed);
    }

    // The outermost ring sits at most on the radius, so the spiral never overshoots the requested
    // area; inner rings are untouched by the clamp.
    private static Vec3 corner(int centerX, int centerZ, long x, long z, int radius, double altitude) {
        long cx = Math.clamp(x, -radius, radius);
        long cz = Math.clamp(z, -radius, radius);
        return new Vec3(centerX + cx, altitude, centerZ + cz);
    }
}
