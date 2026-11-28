package com.stashhunter.stashhunter.signals;

/**
 * Tells when a diagnostics flight period starts or ends. A pause by auto-elytra-repair and the resume after it
 * stay inside the flight period: no summary and no radius advice per repair. A repair that leaves the flight
 * paused (stop/pause command, abort) ends the period once the repair is over.
 */
public final class FlightPeriods {
    public enum Change { NONE, FLIGHT_STARTED, FLIGHT_ENDED }

    private boolean inFlight;
    private boolean pausedForRepair;

    public FlightPeriods(boolean flying) {
        inFlight = flying;
    }

    /** Call once per tick. */
    public Change update(boolean flying, boolean repairing) {
        if (flying) {
            if (pausedForRepair) {
                pausedForRepair = false;
                return Change.NONE;
            }
            if (inFlight) return Change.NONE;
            inFlight = true;
            return Change.FLIGHT_STARTED;
        }
        if (!inFlight) return Change.NONE;
        if (repairing) {
            pausedForRepair = true;
            return Change.NONE;
        }
        inFlight = false;
        pausedForRepair = false;
        return Change.FLIGHT_ENDED;
    }
}
