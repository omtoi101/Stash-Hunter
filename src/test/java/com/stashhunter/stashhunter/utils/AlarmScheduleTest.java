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
    void resetRearms() {
        AlarmSchedule s = new AlarmSchedule();
        assertTrue(s.update(true, 0, 60_000));
        s.reset();
        assertTrue(s.update(true, 10, 60_000));
    }
}
