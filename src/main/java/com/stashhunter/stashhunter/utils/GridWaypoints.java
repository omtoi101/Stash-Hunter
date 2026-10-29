package com.stashhunter.stashhunter.utils;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Boustrophedon ("lawnmower") strips over a rectangle: north-south strips, stripWidth apart along X. */
public final class GridWaypoints {
    private GridWaypoints() {}

    public static List<Vec3> generate(int x1, int z1, int x2, int z2, int stripWidth, double altitude) {
        // Stepping by stripWidth, so anything <= 0 would never terminate.
        if (stripWidth <= 0) throw new IllegalArgumentException("stripWidth must be positive, got " + stripWidth);

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        List<Vec3> waypoints = new ArrayList<>();
        boolean forward = true;

        for (int x = minX; x <= maxX; x += stripWidth) {
            if (forward) {
                waypoints.add(new Vec3(x, altitude, minZ));
                waypoints.add(new Vec3(x, altitude, maxZ));
            } else {
                waypoints.add(new Vec3(x, altitude, maxZ));
                waypoints.add(new Vec3(x, altitude, minZ));
            }
            forward = !forward;
        }
        return waypoints;
    }

    /** Number of waypoints {@link #generate} produces for the given X extent. */
    public static long count(int x1, int x2, int stripWidth) {
        return (Math.abs((long) x2 - x1) / stripWidth + 1) * 2;
    }
}
