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

public class TripManager {
    private static final File TRIPS_FILE = new File(MeteorClient.FOLDER, "stashhunter_trips.json");
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Vec3.class, new Vec3dAdapter())
        .setPrettyPrinting()
        .create();

    public static void saveTrips(List<TripData> trips) {
        try {
            AtomicFiles.write(TRIPS_FILE.toPath(), out -> {
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                GSON.toJson(trips, writer);
                writer.flush();
            });
        } catch (IOException | JsonIOException e) {
            StashHunter.LOG.warn("Could not save {}", TRIPS_FILE.getName(), e);
        }
    }

    public static List<TripData> loadTrips() {
        if (TRIPS_FILE.exists()) {
            try (FileReader reader = new FileReader(TRIPS_FILE)) {
                Type type = new TypeToken<List<TripData>>() {}.getType();
                List<TripData> trips = GSON.fromJson(reader, type);
                if (trips != null) {
                    trips.removeIf(trip -> trip == null || trip.waypoints == null);
                    return trips;
                }
            } catch (IOException e) {
                StashHunter.LOG.warn("Could not read {}", TRIPS_FILE.getName(), e);
            } catch (JsonParseException | IllegalStateException e) {
                // Move the broken file aside: otherwise the next addTrip() would overwrite it.
                File backup = new File(TRIPS_FILE.getPath() + ".corrupt");
                backup.delete();
                boolean moved = TRIPS_FILE.renameTo(backup);
                StashHunter.LOG.warn("Could not read {} ({}){}", TRIPS_FILE.getName(), e.getMessage(),
                    moved ? ", moved it to " + backup.getName() : "");
            }
        }
        return new ArrayList<>();
    }

    // Each trip holds its whole waypoint list, and every pause (repair, low rockets, ...) saves one.
    private static final int MAX_TRIPS = 20;

    /**
     * Saves a flight's progress and returns the new trip's timestamp. {@code replaces} is the entry
     * this flight was saved under before (0 = new flight), so one flight keeps a single entry.
     */
    public static long saveTrip(long replaces, List<Vec3> waypoints, int currentWaypoint) {
        List<TripData> trips = loadTrips();
        long timestamp = TripHistory.nextTimestamp(trips, System.currentTimeMillis());
        saveTrips(TripHistory.withTrip(trips, new TripData(timestamp, List.copyOf(waypoints), currentWaypoint), replaces, MAX_TRIPS));
        return timestamp;
    }

    public static List<TripData> getTrips() {
        return loadTrips();
    }

    public static TripData getLatestTrip() {
        return loadTrips().stream().max(Comparator.comparingLong(t -> t.timestamp)).orElse(null);
    }

    public static TripData getTrip(long timestamp) {
        return loadTrips().stream().filter(t -> t.timestamp == timestamp).findFirst().orElse(null);
    }


    public static class TripData {
        public final long timestamp;
        public final List<Vec3> waypoints;
        public final int currentWaypoint;

        public TripData(long timestamp, List<Vec3> waypoints, int currentWaypoint) {
            this.timestamp = timestamp;
            this.waypoints = waypoints;
            this.currentWaypoint = currentWaypoint;
        }
    }
}
