package com.stashhunter.stashhunter.utils;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripHistoryTest {
    private static TripManager.TripData trip(long timestamp) {
        return new TripManager.TripData(timestamp, List.of(new Vec3(0, 160, 0)), 0);
    }

    @Test
    void pausingAResumedTripReplacesItsEntry() {
        List<TripManager.TripData> trips = List.of(trip(1), trip(2));
        List<TripManager.TripData> result = TripHistory.withTrip(trips, trip(3), 2, 20);

        assertEquals(List.of(3L, 1L), result.stream().map(t -> t.timestamp).toList());
    }

    @Test
    void keepsOnlyTheNewestTrips() {
        List<TripManager.TripData> trips = new ArrayList<>();
        for (long i = 1; i <= 20; i++) trips.add(trip(i));
        List<TripManager.TripData> result = TripHistory.withTrip(trips, trip(21), 0, 20);

        assertEquals(20, result.size());
        assertEquals(21L, result.get(0).timestamp);
        assertTrue(result.stream().noneMatch(t -> t.timestamp == 1L));
    }

    private static TripManager.TripData named(long timestamp, String name) {
        return trip(timestamp).withName(name);
    }

    @Test
    void pausingANamedTripKeepsItsName() {
        List<TripManager.TripData> trips = List.of(named(1, "north"));
        List<TripManager.TripData> result = TripHistory.withTrip(trips, trip(2), 1, 20);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).timestamp);
        assertEquals("north", result.get(0).name);
    }

    @Test
    void namedTripsAreNotDroppedByTheLimit() {
        List<TripManager.TripData> trips = new ArrayList<>();
        trips.add(named(1, "oldest"));
        for (long i = 2; i <= 4; i++) trips.add(trip(i));
        List<TripManager.TripData> result = TripHistory.withTrip(trips, trip(5), 0, 3);

        assertEquals(List.of(5L, 4L, 3L, 1L), result.stream().map(t -> t.timestamp).toList());
    }

    @Test
    void findsByNameIgnoringCaseOrByTimestamp() {
        List<TripManager.TripData> trips = List.of(named(100, "North Area"), trip(200));

        assertEquals(100L, TripHistory.find(trips, " north area ").timestamp);
        assertEquals(200L, TripHistory.find(trips, "200").timestamp);
        assertNull(TripHistory.find(trips, "south"));
        assertNull(TripHistory.find(trips, "300"));
    }

    @Test
    void namesMustBeUniqueAndNotLookLikeATimestamp() {
        List<TripManager.TripData> trips = List.of(named(100, "north"), trip(200));

        assertNull(TripHistory.nameProblem(trips, 200, "south"));
        assertNull(TripHistory.nameProblem(trips, 100, "North"), "renaming a trip to its own name");
        assertNotNull(TripHistory.nameProblem(trips, 200, "NORTH"));
        assertNotNull(TripHistory.nameProblem(trips, 200, "12345"));
        assertNotNull(TripHistory.nameProblem(trips, 200, " "));
        assertNotNull(TripHistory.nameProblem(trips, 200, "x".repeat(TripHistory.MAX_NAME_LENGTH + 1)));
    }

    @Test
    void renameAndDelete() {
        List<TripManager.TripData> trips = List.of(named(100, "north"), trip(200));

        List<TripManager.TripData> renamed = TripHistory.renamed(trips, 200, "south");
        assertEquals("south", TripHistory.find(renamed, 200).name);
        assertEquals("north", TripHistory.find(renamed, 100).name);
        assertNull(TripHistory.find(TripHistory.renamed(trips, 100, null), 100).name);

        assertEquals(List.of(200L), TripHistory.without(trips, 100).stream().map(t -> t.timestamp).toList());
    }

    @Test
    void progressFollowsTheFlightPathNotTheWaypointCount() {
        // Legs of 100 and 300 blocks.
        List<Vec3> path = List.of(new Vec3(0, 160, 0), new Vec3(100, 160, 0), new Vec3(100, 160, 300));

        assertEquals(0, TripHistory.progress(new TripManager.TripData(1, path, 0)), 1e-9);
        assertEquals(0, TripHistory.progress(new TripManager.TripData(1, path, 1)), 1e-9, "heading for waypoint 2");
        assertEquals(0.25, TripHistory.progress(new TripManager.TripData(1, path, 2)), 1e-9);
        assertEquals(1, TripHistory.progress(new TripManager.TripData(1, path, 3)), 1e-9);
        assertTrue(TripHistory.isComplete(new TripManager.TripData(1, path, 3)));
        assertFalse(TripHistory.isComplete(new TripManager.TripData(1, path, 2)));
    }

    @Test
    void nextTimestampNeverRepeatsASavedOne() {
        List<TripManager.TripData> trips = List.of(trip(500));
        assertEquals(501L, TripHistory.nextTimestamp(trips, 500));
        assertEquals(900L, TripHistory.nextTimestamp(trips, 900));
    }
}
