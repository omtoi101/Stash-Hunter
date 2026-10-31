package com.stashhunter.stashhunter.utils;

/**
 * Decides when a repeating alarm rings: immediately once a trigger appears, then every interval
 * while it stays, and re-armed as soon as it is gone, so a returning player rings at once again.
 */
public final class AlarmSchedule {
    private boolean rang;
    private long lastRing;

    /**
     * Range check with hysteresis: a player already in range only leaves it a margin beyond the
     * radius, so someone hovering on the edge doesn't re-arm the alarm (and bypass the interval).
     */
    public static boolean inRange(double distanceSq, int radius, boolean wasInRange) {
        double limit = wasInRange ? radius + Math.max(2, radius / 10) : radius;
        return distanceSq < limit * limit;
    }

    /**
     * @param present    whether the alarm condition currently holds
     * @param nowMs      current time in milliseconds, from a monotonic clock (any origin)
     * @param intervalMs minimum time between two rings while the condition holds
     * @return whether the alarm should ring now
     */
    public boolean update(boolean present, long nowMs, long intervalMs) {
        if (!present) {
            rang = false;
            return false;
        }
        if (rang && nowMs - lastRing < intervalMs) return false;
        rang = true;
        lastRing = nowMs;
        return true;
    }

    public void reset() {
        rang = false;
    }
}
