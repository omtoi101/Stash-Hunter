package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OnlinePlayerTrackerTest {
    private static final UUID SELF = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private static Map<UUID, String> tab(Object... uuidNamePairs) {
        Map<UUID, String> map = new HashMap<>();
        for (int i = 0; i < uuidNamePairs.length; i += 2) map.put((UUID) uuidNamePairs[i], (String) uuidNamePairs[i + 1]);
        return map;
    }

    @Test
    void firstSnapshotIsBaselineWithoutReports() {
        OnlinePlayerTracker tracker = new OnlinePlayerTracker();
        assertFalse(tracker.hasBaseline());
        assertEquals(-1, tracker.count());

        OnlinePlayerTracker.Change change = tracker.update(tab(SELF, "me", A, "alice", B, "bob"), SELF, false);
        assertTrue(change.isEmpty());
        assertEquals(2, tracker.count());
    }

    @Test
    void reportsJoinsAndLeavesSorted() {
        OnlinePlayerTracker tracker = new OnlinePlayerTracker();
        tracker.update(tab(SELF, "me", A, "alice"), SELF, false);

        OnlinePlayerTracker.Change change = tracker.update(tab(SELF, "me", B, "bob", C, "Carl"), SELF, false);
        assertEquals(List.of("bob", "Carl"), change.joined());
        assertEquals(List.of("alice"), change.left());
        assertEquals(1, change.previousCount());
        assertEquals(2, change.count());
    }

    @Test
    void selfIsNeverReported() {
        OnlinePlayerTracker tracker = new OnlinePlayerTracker();
        tracker.update(tab(A, "alice"), SELF, false);
        OnlinePlayerTracker.Change change = tracker.update(tab(A, "alice", SELF, "me"), SELF, false);
        assertTrue(change.isEmpty());
        assertEquals(1, tracker.count());
    }

    @Test
    void silentUpdatesAbsorbTheTabListFill() {
        OnlinePlayerTracker tracker = new OnlinePlayerTracker();
        tracker.update(tab(SELF, "me"), SELF, true);
        OnlinePlayerTracker.Change change = tracker.update(tab(SELF, "me", A, "alice", B, "bob"), SELF, true);
        assertTrue(change.isEmpty());

        change = tracker.update(tab(SELF, "me", A, "alice"), SELF, false);
        assertEquals(List.of("bob"), change.left());
        assertTrue(change.joined().isEmpty());
    }

    @Test
    void resetStartsANewBaseline() {
        OnlinePlayerTracker tracker = new OnlinePlayerTracker();
        tracker.update(tab(A, "alice"), null, false);
        tracker.reset();
        assertFalse(tracker.hasBaseline());
        assertTrue(tracker.update(tab(B, "bob", C, "carl"), null, false).isEmpty());
    }

    @Test
    void formatNamesCapsTheList() {
        assertEquals("a, b", OnlinePlayerTracker.formatNames(List.of("a", "b"), 3));
        assertEquals("a, b and 2 more", OnlinePlayerTracker.formatNames(List.of("a", "b", "c", "d"), 2));
    }
}
