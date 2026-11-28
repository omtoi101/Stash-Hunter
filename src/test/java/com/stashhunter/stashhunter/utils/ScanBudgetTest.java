package com.stashhunter.stashhunter.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScanBudgetTest {
    @Test
    void exhaustedOnlyAfterBudgetElapsed() {
        long[] now = {1000};
        ScanBudget b = new ScanBudget(500, () -> now[0]);
        b.start();
        assertFalse(b.exhausted());
        now[0] = 1499;
        assertFalse(b.exhausted());
        now[0] = 1500;
        assertTrue(b.exhausted());
    }

    @Test
    void startRestartsTheWindow() {
        long[] now = {0};
        ScanBudget b = new ScanBudget(100, () -> now[0]);
        b.start();
        now[0] = 200;
        assertTrue(b.exhausted());
        b.start();
        assertFalse(b.exhausted());
    }

    @Test
    void skipsLevelsWithoutPassableBlocks() {
        boolean[] onlyAir = {false, false};
        boolean[] passable = {true, false};
        assertFalse(ScanBudget.skipSection(onlyAir, passable, 0));
        assertTrue(ScanBudget.skipSection(onlyAir, passable, 1));
    }

    @Test
    void skipsAirOverAirButNotAirOverSolid() {
        boolean[] onlyAir = {false, true, true};
        boolean[] passable = {true, true, true};
        assertFalse(ScanBudget.skipSection(onlyAir, passable, 1)); // floor section has solids
        assertTrue(ScanBudget.skipSection(onlyAir, passable, 2));
        assertEquals(false, ScanBudget.skipSection(onlyAir, passable, 0)); // lowest: no section below
    }
}
