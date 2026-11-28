package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FlightPeriodsTest {
    @Test
    void startAndEndOfAFlight() {
        FlightPeriods p = new FlightPeriods(false);
        assertEquals(FlightPeriods.Change.NONE, p.update(false, false));
        assertEquals(FlightPeriods.Change.FLIGHT_STARTED, p.update(true, false));
        assertEquals(FlightPeriods.Change.NONE, p.update(true, false));
        assertEquals(FlightPeriods.Change.FLIGHT_ENDED, p.update(false, false));
    }

    @Test
    void repairPauseAndResumeStayInTheSamePeriod() {
        FlightPeriods p = new FlightPeriods(true);
        assertEquals(FlightPeriods.Change.NONE, p.update(false, true));
        assertEquals(FlightPeriods.Change.NONE, p.update(false, true));
        // resumeFlight resumes the controller and resets the repair state in the same call.
        assertEquals(FlightPeriods.Change.NONE, p.update(true, false));
        assertEquals(FlightPeriods.Change.FLIGHT_ENDED, p.update(false, false));
    }

    @Test
    void repairThatKeepsTheFlightPausedEndsThePeriod() {
        FlightPeriods p = new FlightPeriods(true);
        assertEquals(FlightPeriods.Change.NONE, p.update(false, true));
        assertEquals(FlightPeriods.Change.FLIGHT_ENDED, p.update(false, false));
        assertEquals(FlightPeriods.Change.FLIGHT_STARTED, p.update(true, false));
    }
}
