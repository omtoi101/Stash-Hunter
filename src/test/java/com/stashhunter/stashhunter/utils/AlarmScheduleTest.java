package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlarmScheduleTest {
    @Test
    void ringsImmediatelyThenOncePerInterval() {
        AlarmSchedule s = new AlarmSchedule();
        assertTrue(s.update(true, 1000, 3000));
        assertFalse(s.update(true, 3999, 3000));
        assertTrue(s.update(true, 4000, 3000));
        assertFalse(s.update(true, 5000, 3000));
    }

    @Test
    void silentWithoutTrigger() {
        AlarmSchedule s = new AlarmSchedule();
        assertFalse(s.update(false, 0, 3000));
        assertFalse(s.update(false, 10_000, 3000));
    }

    @Test
    void rearmsWhenTriggerLeaves() {
        AlarmSchedule s = new AlarmSchedule();
        assertTrue(s.update(true, 1000, 3000));
        assertFalse(s.update(false, 1500, 3000));
        assertTrue(s.update(true, 1600, 3000));
    }

    @Test
    void worksWithNegativeClockValues() {
        AlarmSchedule s = new AlarmSchedule();
        assertTrue(s.update(true, -10_000, 3000));
        assertFalse(s.update(true, -9000, 3000));
        assertTrue(s.update(true, -7000, 3000));
    }

    @Test
    void rangeHasHysteresis() {
        assertTrue(AlarmSchedule.inRange(99.9 * 99.9, 100, false));
        assertFalse(AlarmSchedule.inRange(105 * 105, 100, false), "entering needs the radius");
        assertTrue(AlarmSchedule.inRange(105 * 105, 100, true), "leaving needs radius + margin");
        assertFalse(AlarmSchedule.inRange(110 * 110, 100, true));
        assertTrue(AlarmSchedule.inRange(2.5 * 2.5, 1, true), "small radii still get a margin");
    }

    @Test
    void resetRearms() {
        AlarmSchedule s = new AlarmSchedule();
        assertTrue(s.update(true, 0, 60_000));
        s.reset();
        assertTrue(s.update(true, 10, 60_000));
    }
}
