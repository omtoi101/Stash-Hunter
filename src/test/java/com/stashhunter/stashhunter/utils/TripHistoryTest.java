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

    @Test
    void nextTimestampNeverRepeatsASavedOne() {
        List<TripManager.TripData> trips = List.of(trip(500));
        assertEquals(501L, TripHistory.nextTimestamp(trips, 500));
        assertEquals(900L, TripHistory.nextTimestamp(trips, 900));
    }
}
