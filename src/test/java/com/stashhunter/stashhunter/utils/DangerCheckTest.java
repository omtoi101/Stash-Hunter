package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DangerCheckTest {
    @Test
    void safeWhenBothTriggersOff() {
        assertNull(DangerCheck.reason(false, 1, 64, false, 1, 10));
    }

    @Test
    void playerInsideRadiusTriggers() {
        assertEquals("player within 30 blocks", DangerCheck.reason(true, 29.2, 64, false, 20, 10));
        assertNotNull(DangerCheck.reason(true, 64, 64, false, 20, 10));
    }

    @Test
    void playerOutsideRadiusOrAbsentIsSafe() {
        assertNull(DangerCheck.reason(true, 64.1, 64, false, 20, 10));
        assertNull(DangerCheck.reason(true, DangerCheck.NO_PLAYER, 64, false, 20, 10));
    }

    @Test
    void lowHealthTriggers() {
        assertEquals("health 9 below 10", DangerCheck.reason(false, DangerCheck.NO_PLAYER, 64, true, 9, 10));
        assertEquals("health 4.5 below 10", DangerCheck.reason(false, DangerCheck.NO_PLAYER, 64, true, 4.5f, 10));
    }

    @Test
    void healthAtThresholdIsSafe() {
        assertNull(DangerCheck.reason(false, DangerCheck.NO_PLAYER, 64, true, 10, 10));
    }

    @Test
    void deadDoesNotTrigger() {
        assertNull(DangerCheck.reason(false, DangerCheck.NO_PLAYER, 64, true, 0, 10));
    }

    @Test
    void healthTriggerIgnoredWhenDisabled() {
        assertNull(DangerCheck.reason(true, DangerCheck.NO_PLAYER, 64, false, 1, 10));
    }
}
