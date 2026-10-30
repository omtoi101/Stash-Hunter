package com.stashhunter.stashhunter.utils;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Square spiral outward from a center: rings gap apart, so the area nearest the start (spawn,
 * a portal) is scanned first. Only corners are emitted, the controller flies straight between them.
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
        // Close the outer ring's north side, otherwise it would stay one gap short of the radius there.
        x += (long) 2 * rings * gap;
        waypoints.add(corner(centerX, centerZ, x, z, radius, altitude));
        return waypoints;
    }

    /** Number of waypoints {@link #generate} produces. */
    public static long count(int radius, int gap) {
        return 4L * rings(radius, gap) + 2;
    }

    private static int rings(int radius, int gap) {
        return (int) (((long) radius + gap - 1) / gap);
    }

    // The outermost ring sits exactly on the radius (like GridWaypoints' last strip on maxX),
    // so the spiral never overshoots the requested area; inner rings are untouched by the clamp.
    private static Vec3 corner(int centerX, int centerZ, long x, long z, int radius, double altitude) {
        long cx = Math.clamp(x, -radius, radius);
        long cz = Math.clamp(z, -radius, radius);
        return new Vec3(centerX + cx, altitude, centerZ + cz);
    }
}
