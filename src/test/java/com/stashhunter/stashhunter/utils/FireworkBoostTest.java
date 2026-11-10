package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FireworkBoostTest {
    @Test
    void forwardSpeedProjectsOntoLookAndConvertsToBlocksPerSecond() {
        assertEquals(20.0, FireworkBoost.forwardSpeed(1, 0, 0, 1, 0, 0), 1e-9);
        // Sideways drift is no forward progress.
        assertEquals(0.0, FireworkBoost.forwardSpeed(0, 0, 1, 1, 0, 0), 1e-9);
        // Flying backwards relative to the look counts as negative.
        assertEquals(-20.0, FireworkBoost.forwardSpeed(-1, 0, 0, 1, 0, 0), 1e-9);
        // Look vector is normalised.
        assertEquals(20.0, FireworkBoost.forwardSpeed(1, 0, 0, 5, 0, 0), 1e-9);
        assertEquals(0.0, FireworkBoost.forwardSpeed(1, 0, 0, 0, 0, 0), 1e-9);
    }

    @Test
    void boostsOnlyWhenSlowFlyingAndDelayElapsed() {
        assertTrue(FireworkBoost.shouldBoost(true, 10, 20, 40, 40));
        assertFalse(FireworkBoost.shouldBoost(true, 20, 20, 40, 40));
        assertFalse(FireworkBoost.shouldBoost(true, 10, 20, 39, 40));
        assertFalse(FireworkBoost.shouldBoost(false, 0, 20, Integer.MAX_VALUE, 40));
        assertTrue(FireworkBoost.shouldBoost(true, 10, 20, Integer.MAX_VALUE, 40));
    }

    @Test
    void checkCooldownThrottlesChecks() {
        assertFalse(FireworkBoost.shouldCheck(4, 5));
        assertTrue(FireworkBoost.shouldCheck(5, 5));
        assertTrue(FireworkBoost.shouldCheck(0, 0));
    }

    @Test
    void onlyRocketsWithoutStarsAreSafe() {
        assertTrue(FireworkBoost.isSafeRocket(0));
        assertFalse(FireworkBoost.isSafeRocket(1));
        assertFalse(FireworkBoost.isSafeRocket(7));
    }
}
