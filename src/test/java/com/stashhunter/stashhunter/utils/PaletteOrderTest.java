package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import static com.stashhunter.stashhunter.utils.PaletteOrder.Verdict;
import static com.stashhunter.stashhunter.utils.PaletteOrder.Vote;
import static org.junit.jupiter.api.Assertions.*;

class PaletteOrderTest {
    @Test
    void linearPaletteStartingWithAirIsFresh() {
        assertEquals(Vote.FRESH, PaletteOrder.vote(PaletteOrder.LINEAR_BITS, true, false));
    }

    @Test
    void linearPaletteStartingWithOriginBlockIsSaved() {
        assertEquals(Vote.SAVED, PaletteOrder.vote(PaletteOrder.LINEAR_BITS, false, false));
    }

    @Test
    void airAtOriginTellsNothing() {
        assertEquals(Vote.NONE, PaletteOrder.vote(PaletteOrder.LINEAR_BITS, true, true));
        assertEquals(Vote.NONE, PaletteOrder.vote(0, false, true));
    }

    @Test
    void singleNonAirValueIsSaved() {
        assertEquals(Vote.SAVED, PaletteOrder.vote(0, false, false));
    }

    @Test
    void largerPalettesTellNothing() {
        assertEquals(Vote.NONE, PaletteOrder.vote(5, true, false));
        assertEquals(Vote.NONE, PaletteOrder.vote(8, false, false));
        assertEquals(Vote.NONE, PaletteOrder.vote(15, true, false));
    }

    @Test
    void oneSavedSectionMakesTheChunkOld() {
        assertEquals(Verdict.NEW, PaletteOrder.classify(5, 0));
        assertEquals(Verdict.OLD, PaletteOrder.classify(0, 3));
        assertEquals(Verdict.OLD, PaletteOrder.classify(1, 7), "filled-in origin in an old chunk");
        assertEquals(Verdict.UNKNOWN, PaletteOrder.classify(0, 0));
    }
}
