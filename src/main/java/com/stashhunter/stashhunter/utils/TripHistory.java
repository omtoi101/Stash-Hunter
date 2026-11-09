package com.stashhunter.stashhunter.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure list logic for the saved trips, kept apart from TripManager's file access for tests. */
public final class TripHistory {
    private TripHistory() {}

    /** Names show in chat and in the trips file. */
    public static final int MAX_NAME_LENGTH = 32;

    /**
     * Adds {@code trip}, dropping the entry it continues ({@code replaces}, 0 = none) and then the
     * oldest unnamed trips beyond {@code max}. The new trip keeps the name of the entry it continues,
     * and named trips stay until they are deleted. Newest first.
     */
    public static List<TripManager.TripData> withTrip(List<TripManager.TripData> trips, TripManager.TripData trip, long replaces, int max) {
        List<TripManager.TripData> result = new ArrayList<>(trips);
        if (replaces != 0) {
            TripManager.TripData replaced = find(trips, replaces);
            result.removeIf(t -> t.timestamp == replaces);
            if (trip.name == null && replaced != null && replaced.name != null) trip = trip.withName(replaced.name);
        }
        result.add(trip);
        result.sort(Comparator.comparingLong((TripManager.TripData t) -> t.timestamp).reversed());

        List<TripManager.TripData> kept = new ArrayList<>(result.size());
        int unnamed = 0;
        for (TripManager.TripData t : result) {
            if (t.name != null || ++unnamed <= max) kept.add(t);
        }
        return kept;
    }

    /** A timestamp after every saved one, so replacing by timestamp never hits two trips. */
    public static long nextTimestamp(List<TripManager.TripData> trips, long now) {
        long latest = 0;
        for (TripManager.TripData trip : trips) latest = Math.max(latest, trip.timestamp);
        return Math.max(now, latest + 1);
    }

    public static TripManager.TripData find(List<TripManager.TripData> trips, long timestamp) {
        for (TripManager.TripData trip : trips) {
            if (trip.timestamp == timestamp) return trip;
        }
        return null;
    }

    /** The trip with this name (ignoring case) or, for a number, this timestamp; null if none. */
    public static TripManager.TripData find(List<TripManager.TripData> trips, String nameOrTimestamp) {
        String key = nameOrTimestamp.trim();
        for (TripManager.TripData trip : trips) {
            if (trip.name != null && trip.name.equalsIgnoreCase(key)) return trip;
        }
        try {
            return find(trips, Long.parseLong(key));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Null if trip {@code timestamp} may be called {@code name}, otherwise the reason it may not. */
    public static String nameProblem(List<TripManager.TripData> trips, long timestamp, String name) {
        if (name.isBlank()) return "The name is empty.";
        if (name.length() > MAX_NAME_LENGTH) return "Names are at most " + MAX_NAME_LENGTH + " characters long.";
        if (name.chars().allMatch(Character::isDigit)) return "A name can't be just digits: those are read as a trip's timestamp.";
        if (name.indexOf('§') >= 0) return "Names can't contain §.";
        for (TripManager.TripData trip : trips) {
            if (trip.timestamp != timestamp && name.equalsIgnoreCase(trip.name)) {
                return "Trip " + trip.timestamp + " is already called \"" + trip.name + "\".";
            }
        }
        return null;
    }

    /** The list with trip {@code timestamp} renamed; null removes its name. */
    public static List<TripManager.TripData> renamed(List<TripManager.TripData> trips, long timestamp, String name) {
        return trips.stream().map(t -> t.timestamp == timestamp ? t.withName(name) : t).toList();
    }

    public static List<TripManager.TripData> without(List<TripManager.TripData> trips, long timestamp) {
        return trips.stream().filter(t -> t.timestamp != timestamp).toList();
    }

    /** True once every waypoint was reached. */
    public static boolean isComplete(TripManager.TripData trip) {
        return trip.currentWaypoint >= trip.waypoints.size();
    }

    /** Share of the trip's flight path (by distance, see {@link WaypointPath}) behind its saved waypoint, 0..1. */
    public static double progress(TripManager.TripData trip) {
        double length = WaypointPath.length(trip.waypoints);
        if (length <= 0) return isComplete(trip) ? 1 : 0;
        if (isComplete(trip)) return 1;
        if (trip.currentWaypoint <= 0) return 0;
        // Saved while heading for currentWaypoint: the leg there hasn't been flown yet.
        var legStart = trip.waypoints.get(trip.currentWaypoint - 1);
        return WaypointPath.flown(trip.waypoints, trip.currentWaypoint, legStart.x, legStart.z) / length;
    }
}
