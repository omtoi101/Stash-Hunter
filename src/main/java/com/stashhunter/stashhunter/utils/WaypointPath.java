package com.stashhunter.stashhunter.utils;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Horizontal distances along a waypoint list. A spiral's legs grow ring by ring, so counting waypoints
 * says little about progress: halfway through the waypoints is only about a quarter of the flight.
 */
public final class WaypointPath {
    private WaypointPath() {}

    /** Horizontal length from the first waypoint to the last. */
    public static double length(List<Vec3> waypoints) {
        double total = 0;
        for (int i = 1; i < waypoints.size(); i++) {
            total += leg(waypoints.get(i - 1), waypoints.get(i));
        }
        return total;
    }

    /**
     * Horizontal distance covered while heading for {@code waypoints.get(current)} with the player at
     * x/z. The way to the first waypoint isn't part of the path, and a player who strayed off the
     * current leg is credited by how close they are to its end, never below the leg's start.
     */
    public static double flown(List<Vec3> waypoints, int current, double x, double z) {
        if (current <= 0 || waypoints.isEmpty()) return 0;
        if (current >= waypoints.size()) return length(waypoints);

        double done = 0;
        for (int i = 1; i < current; i++) {
            done += leg(waypoints.get(i - 1), waypoints.get(i));
        }
        Vec3 target = waypoints.get(current);
        double leg = leg(waypoints.get(current - 1), target);
        double left = Math.hypot(target.x - x, target.z - z);
        return done + Math.max(0, leg - left);
    }

    private static double leg(Vec3 a, Vec3 b) {
        return Math.hypot(b.x - a.x, b.z - a.z);
    }
}
