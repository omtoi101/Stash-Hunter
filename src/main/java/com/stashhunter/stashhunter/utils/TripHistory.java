package com.stashhunter.stashhunter.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure list logic for the saved trips, kept apart from TripManager's file access for tests. */
public final class TripHistory {
    private TripHistory() {}

    /**
     * Adds {@code trip}, dropping the entry it continues ({@code replaces}, 0 = none) and then the
     * oldest trips beyond {@code max}. Newest first.
     */
    public static List<TripManager.TripData> withTrip(List<TripManager.TripData> trips, TripManager.TripData trip, long replaces, int max) {
        List<TripManager.TripData> result = new ArrayList<>(trips);
        if (replaces != 0) result.removeIf(t -> t.timestamp == replaces);
        result.add(trip);
        result.sort(Comparator.comparingLong((TripManager.TripData t) -> t.timestamp).reversed());
        return result.size() > max ? new ArrayList<>(result.subList(0, max)) : result;
    }

    /** A timestamp after every saved one, so replacing by timestamp never hits two trips. */
    public static long nextTimestamp(List<TripManager.TripData> trips, long now) {
        long latest = 0;
        for (TripManager.TripData trip : trips) latest = Math.max(latest, trip.timestamp);
        return Math.max(now, latest + 1);
    }
}
