package com.stashhunter.stashhunter.utils;

/**
 * Decides when a repeating alarm rings: immediately once a trigger appears, then every interval
 * while it stays, and re-armed as soon as it is gone, so a returning player rings at once again.
 */
public final class AlarmSchedule {
    private long lastRing = -1;

    /**
     * @param present    whether the alarm condition currently holds
     * @param nowMs      current time in milliseconds
     * @param intervalMs minimum time between two rings while the condition holds
     * @return whether the alarm should ring now
     */
    public boolean update(boolean present, long nowMs, long intervalMs) {
        if (!present) {
            lastRing = -1;
            return false;
        }
        if (lastRing >= 0 && nowMs - lastRing < intervalMs) return false;
        lastRing = nowMs;
        return true;
    }

    public void reset() {
        lastRing = -1;
    }
}
