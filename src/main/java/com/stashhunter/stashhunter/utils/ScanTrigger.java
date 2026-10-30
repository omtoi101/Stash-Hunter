package com.stashhunter.stashhunter.utils;

/**
 * Decides when the stash scan runs. A fixed tick interval alone leaves gaps at high flight speed
 * (40 ticks at ~30 blocks/s is ~60 blocks between scans with a 64-block radius), so a scan is also
 * triggered once the player has moved a fraction of the scan radius since the last one.
 */
public final class ScanTrigger {
    // Half the radius: consecutive scan areas overlap by at least half their width along the path.
    public static final double DISTANCE_FRACTION = 0.5;

    private ScanTrigger() {
    }

    /**
     * @param ticksSinceLastScan ticks elapsed since the last scan
     * @param maxIntervalTicks   scan at least this often, even when standing still
     * @param dx                 horizontal X offset since the last scan position
     * @param dz                 horizontal Z offset since the last scan position
     * @param scanRadius         scan radius in blocks
     * @param byDistance         whether the distance trigger is enabled
     */
    public static boolean shouldScan(int ticksSinceLastScan, int maxIntervalTicks, double dx, double dz,
                                     int scanRadius, boolean byDistance) {
        if (ticksSinceLastScan >= maxIntervalTicks) return true;
        if (!byDistance) return false;
        double step = scanRadius * DISTANCE_FRACTION;
        return dx * dx + dz * dz >= step * step;
    }
}
