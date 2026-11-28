package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportGateTest {
    private static final double THRESHOLD = 0.005;

    @Test
    void denseContainerStashIsAnnounced() {
        assertTrue(ReportGate.announce(true, 20, 25, 0.01, THRESHOLD, false));
    }

    @Test
    void sparseContainerStashIsNotAnnounced() {
        assertFalse(ReportGate.announce(true, 20, 25, 0.001, THRESHOLD, false));
    }

    @Test
    void scoreStashIsAlwaysAnnounced() {
        assertTrue(ReportGate.announce(false, 25, 25, 0.0001, THRESHOLD, false));
    }

    @Test
    void containerStashThatAlsoMeetsTheScoreSkipsTheDensityGate() {
        assertTrue(ReportGate.announce(true, 30, 25, 0.0001, THRESHOLD, false));
    }

    @Test
    void anythingAnnouncedBeforeIsAnnouncedAgain() {
        // An earlier stash with waypoint, or a hint that grows into this stash.
        assertTrue(ReportGate.announce(true, 20, 25, 0.0001, THRESHOLD, true));
    }

    @Test
    void classicNeverReachesTheScore() {
        assertFalse(ReportGate.announce(true, 1000, Integer.MAX_VALUE, 0.0001, THRESHOLD, false));
    }
}
