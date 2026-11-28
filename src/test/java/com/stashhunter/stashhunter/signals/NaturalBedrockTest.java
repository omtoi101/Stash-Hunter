package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import meteordevelopment.meteorclient.utils.world.Dimension;
import org.junit.jupiter.api.Test;

class NaturalBedrockTest {
    @Test
    void overworldBedrockAboveMinus60IsPlaced() {
        assertTrue(NaturalBedrock.isNatural(Dimension.Overworld, -60));
        assertTrue(NaturalBedrock.isNatural(Dimension.Overworld, -64));
        assertFalse(NaturalBedrock.isNatural(Dimension.Overworld, -59));
    }

    @Test
    void netherRoofBedrockIsNatural() {
        assertTrue(NaturalBedrock.isNatural(Dimension.Nether, 125));
        assertTrue(NaturalBedrock.isNatural(Dimension.Nether, 4));
        assertTrue(NaturalBedrock.isNatural(Dimension.Nether, 123));
        assertTrue(NaturalBedrock.isNatural(Dimension.Nether, 127));
        assertFalse(NaturalBedrock.isNatural(Dimension.Nether, 128));
        assertFalse(NaturalBedrock.isNatural(Dimension.Nether, 50));
    }

    @Test
    void endIsNeverNaturalButHasAnExitPortalArea() {
        assertFalse(NaturalBedrock.isNatural(Dimension.End, 60));
        assertTrue(NaturalBedrock.isEndExitPortal(4, 50, -4));
        assertTrue(NaturalBedrock.isEndExitPortal(0, 80, 0));
        assertFalse(NaturalBedrock.isEndExitPortal(5, 60, 0));
        assertFalse(NaturalBedrock.isEndExitPortal(0, 49, 0));
        assertFalse(NaturalBedrock.isEndExitPortal(0, 81, 0));
    }

    /** Rows 'from' to 'to' (section-local, inclusive) as a bit mask. */
    private static int rows(int from, int to) {
        int mask = 0;
        for (int y = from; y <= to; y++) mask |= 1 << y;
        return mask;
    }

    @Test
    void overworldBottomSectionWalksOnlyRowsAboveMinus60() {
        assertEquals(rows(5, 15), NaturalBedrock.unnaturalRows(Dimension.Overworld, -64));
        assertEquals(rows(0, 15), NaturalBedrock.unnaturalRows(Dimension.Overworld, -48));
    }

    @Test
    void netherFloorAndRoofSectionsWalkOnlyRows5To122() {
        assertEquals(rows(5, 15), NaturalBedrock.unnaturalRows(Dimension.Nether, 0));
        assertEquals(rows(0, 10), NaturalBedrock.unnaturalRows(Dimension.Nether, 112));
        assertEquals(rows(0, 15), NaturalBedrock.unnaturalRows(Dimension.Nether, 64));
        assertEquals(rows(0, 15), NaturalBedrock.unnaturalRows(Dimension.Nether, 128));
    }

    @Test
    void endWalksEveryRow() {
        assertEquals(rows(0, 15), NaturalBedrock.unnaturalRows(Dimension.End, 48));
    }
}
