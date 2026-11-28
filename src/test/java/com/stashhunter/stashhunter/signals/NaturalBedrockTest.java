package com.stashhunter.stashhunter.signals;

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
}
