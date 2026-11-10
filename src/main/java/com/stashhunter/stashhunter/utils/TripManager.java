package com.stashhunter.stashhunter.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.stashhunter.stashhunter.StashHunter;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The saved flights. The file is read once; afterwards this class keeps the list in memory and the
 * file follows it. Writing (temp file + fsync) runs on a background thread: a pause or landing
 * happens mid-flight on the client thread, where a slow disk would show as a stutter.
 * <p>
 * Client thread only, apart from {@link #shutdown()}.
 */
public class TripManager {
    private static final File TRIPS_FILE = new File(MeteorClient.FOLDER, "stashhunter_trips.json");
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Vec3.class, new Vec3dAdapter())
        .setPrettyPrinting()
        .create();
    private static final BackgroundSaver<List<TripData>> SAVER = new BackgroundSaver<>("StashHunter-TripSave", TripManager::writeTrips);
    // How long the shutdown hook waits for the last trip write.
    private static final long SHUTDOWN_TIMEOUT_MS = 2000;

    // Each trip holds its whole waypoint list, and every pause (repair, low rockets, ...) saves one.
    // Named trips don't count: they stay until .stashhunter delete.
    private static final int MAX_TRIPS = 20;

    // Immutable, newest first; null until first used. A new list replaces it on every save, so the
    // saver thread can serialize the old one while the client thread goes on.
    private static List<TripData> trips;

    private TripManager() {}

    // Runs on the saver thread. Only reads the snapshot: the list and its trips are immutable.
    private static void writeTrips(List<TripData> snapshot) {
        try {
            AtomicFiles.write(TRIPS_FILE.toPath(), out -> {
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                GSON.toJson(snapshot, writer);
                writer.flush();
            });
        } catch (IOException | JsonIOException e) {
            StashHunter.LOG.warn("Could not save {}", TRIPS_FILE.getName(), e);
        }
    }

    private static List<TripData> trips() {
        if (trips == null) trips = List.copyOf(readTrips());
        return trips;
    }

    // Runs once, before the first save is queued, so no write can be mid-way here.
    private static List<TripData> readTrips() {
        AtomicFiles.deleteLeftovers(TRIPS_FILE.toPath());
        if (TRIPS_FILE.exists()) {
            try (FileReader reader = new FileReader(TRIPS_FILE, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<TripData>>() {}.getType();
                List<TripData> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    // An empty list (hand-edited file) would leave resume() with an active flight
                    // that never moves and blocks .stashhunter start.
                    return loaded.stream()
                        .filter(trip -> trip != null && trip.waypoints != null)
                        .map(TripManager::immutable)
                        .filter(trip -> !trip.waypoints.isEmpty())
                        .toList();
                }
            } catch (IOException e) {
                StashHunter.LOG.warn("Could not read {}", TRIPS_FILE.getName(), e);
            } catch (JsonParseException | IllegalStateException e) {
                // Move the broken file aside: otherwise the next save would overwrite it.
                File backup = new File(TRIPS_FILE.getPath() + ".corrupt");
                backup.delete();
                boolean moved = TRIPS_FILE.renameTo(backup);
                StashHunter.LOG.warn("Could not read {} ({}){}", TRIPS_FILE.getName(), e.getMessage(),
                    moved ? ", moved it to " + backup.getName() : "");
            }
        }
        return new ArrayList<>();
    }

    // Gson fills in ArrayLists; the saver thread may read a trip while the client thread holds it.
    // Null entries (hand-edited file) would make List.copyOf throw, so they are dropped.
    private static TripData immutable(TripData trip) {
        List<Vec3> waypoints = trip.waypoints.stream().filter(w -> w != null).toList();
        String name = trip.name == null || trip.name.isBlank() ? null : trip.name.trim();
        return new TripData(trip.timestamp, waypoints, trip.currentWaypoint, name);
    }

    /**
     * Saves a flight's progress and returns the new trip's timestamp. {@code replaces} is the entry
     * this flight was saved under before (0 = new flight), so one flight keeps a single entry.
     * The waypoints are copied here on the client thread; the file is written in the background.
     */
    public static long saveCurrentTripAsync(long replaces, List<Vec3> waypoints, int currentWaypoint) {
        List<TripData> current = trips();
        long timestamp = TripHistory.nextTimestamp(current, System.currentTimeMillis());
        TripData trip = new TripData(timestamp, List.copyOf(waypoints), currentWaypoint);
        trips = List.copyOf(TripHistory.withTrip(current, trip, replaces, MAX_TRIPS));
        SAVER.save(trips);
        return timestamp;
    }

    /** Immutable, newest first. */
    public static List<TripData> getTrips() {
        return trips();
    }

    public static TripData getLatestTrip() {
        return trips().stream().max(Comparator.comparingLong(t -> t.timestamp)).orElse(null);
    }

    public static TripData getTrip(long timestamp) {
        return TripHistory.find(trips(), timestamp);
    }

    /** The trip with this name (ignoring case) or timestamp, or null. */
    public static TripData findTrip(String nameOrTimestamp) {
        return TripHistory.find(trips(), nameOrTimestamp);
    }

    /** Names trip {@code timestamp} (null removes the name). Returns null, or why it didn't. */
    public static String renameTrip(long timestamp, String name) {
        List<TripData> current = trips();
        if (TripHistory.find(current, timestamp) == null) return "There is no saved trip " + timestamp + ".";
        if (name != null) {
            String problem = TripHistory.nameProblem(current, timestamp, name);
            if (problem != null) return problem;
        }
        trips = List.copyOf(TripHistory.renamed(current, timestamp, name));
        SAVER.save(trips);
        return null;
    }

    /** False if there is no such trip. */
    public static boolean deleteTrip(long timestamp) {
        List<TripData> current = trips();
        if (TripHistory.find(current, timestamp) == null) return false;
        trips = List.copyOf(TripHistory.without(current, timestamp));
        SAVER.save(trips);
        return true;
    }

    /** JVM shutdown hook: writes a trip save that is still queued. */
    public static void shutdown() {
        SAVER.shutdown(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    public static class TripData {
        public final long timestamp;
        public final List<Vec3> waypoints;
        public final int currentWaypoint;
        // Given with .stashhunter name, null otherwise (and left out of the file). Named trips are
        // kept until deleted; unnamed ones beyond MAX_TRIPS are dropped.
        public final String name;

        public TripData(long timestamp, List<Vec3> waypoints, int currentWaypoint) {
            this(timestamp, waypoints, currentWaypoint, null);
        }

        public TripData(long timestamp, List<Vec3> waypoints, int currentWaypoint, String name) {
            this.timestamp = timestamp;
            this.waypoints = waypoints;
            this.currentWaypoint = currentWaypoint;
            this.name = name;
        }

        public TripData withName(String name) {
            return new TripData(timestamp, waypoints, currentWaypoint, name);
        }
    }
}
