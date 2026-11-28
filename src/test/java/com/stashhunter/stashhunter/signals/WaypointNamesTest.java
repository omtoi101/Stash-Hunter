package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WaypointNamesTest {

    @Test
    void stashNameIsUnchangedFrom2622() {
        assertEquals("10 Blocks at 1, 2, 3", WaypointNames.stash(10, "1, 2, 3"));
    }

    @Test
    void hintNameUsesHintPrefix() {
        assertEquals("Hint: shulker box at 1, 2, 3", WaypointNames.hint("shulker box", "1, 2, 3"));
    }

    @Test
    void tierOfRecognisesBothSchemes() {
        assertEquals(Tier.STASH, WaypointNames.tierOf(WaypointNames.stash(10, "1, 2, 3")));
        assertEquals(Tier.HINT, WaypointNames.tierOf(WaypointNames.hint("chest", "1, 2, 3")));
        assertEquals(Tier.NONE, WaypointNames.tierOf("Home"));
    }
}
