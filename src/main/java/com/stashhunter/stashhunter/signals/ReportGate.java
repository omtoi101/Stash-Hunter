package com.stashhunter.stashhunter.signals;

/** Whether a stash gets a waypoint and a Discord message (the notification-density-threshold gate). */
public final class ReportGate {
    private ReportGate() {
    }

    /**
     * The density threshold only guards stashes found by the container count alone. A stash that
     * also meets the stash score, or whose earlier report (a stash with waypoint, or the hint it grew
     * out of) was already announced, is always announced.
     */
    public static boolean announce(boolean byContainers, int score, int stashScore, double density,
                                   double densityThreshold, boolean announcedBefore) {
        if (announcedBefore) return true;
        if (!byContainers || score >= stashScore) return true;
        return density >= densityThreshold;
    }
}
