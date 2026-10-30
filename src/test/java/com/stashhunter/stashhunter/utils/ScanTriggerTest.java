package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScanTriggerTest {
    @Test
    void scansWhenIntervalElapsedEvenWithoutMovement() {
        assertTrue(ScanTrigger.shouldScan(40, 40, 0, 0, 64, true));
        assertTrue(ScanTrigger.shouldScan(40, 40, 0, 0, 64, false));
        assertFalse(ScanTrigger.shouldScan(39, 40, 0, 0, 64, true));
    }

    @Test
    void scansEarlyOnceHalfTheRadiusIsTravelled() {
        assertFalse(ScanTrigger.shouldScan(10, 40, 31, 0, 64, true));
        assertTrue(ScanTrigger.shouldScan(10, 40, 32, 0, 64, true));
        assertTrue(ScanTrigger.shouldScan(10, 40, 23, 23, 64, true)); // diagonal, ~32.5 blocks
    }

    @Test
    void distanceTriggerCanBeDisabled() {
        assertFalse(ScanTrigger.shouldScan(10, 40, 500, 500, 64, false));
    }
}
