package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnfocusedFpsTest {
    @Test
    void focusedKeepsUserLimit() {
        assertEquals(144, UnfocusedFps.effectiveLimit(true, 144, 10));
        assertEquals(260, UnfocusedFps.effectiveLimit(true, 260, 10));
    }

    @Test
    void unfocusedCapsUnlimitedAndHighLimits() {
        assertEquals(10, UnfocusedFps.effectiveLimit(false, 260, 10));
        assertEquals(15, UnfocusedFps.effectiveLimit(false, 144, 15));
    }

    @Test
    void lowerUserLimitWins() {
        assertEquals(5, UnfocusedFps.effectiveLimit(false, 5, 30));
    }

    @Test
    void capNeverDropsBelowTickSafeMinimum() {
        assertEquals(UnfocusedFps.MIN_LIMIT, UnfocusedFps.effectiveLimit(false, 260, 0));
        assertEquals(UnfocusedFps.MIN_LIMIT, UnfocusedFps.effectiveLimit(false, 260, 1));
    }
}
