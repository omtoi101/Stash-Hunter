package com.stashhunter.stashhunter.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TripManager {
    private static final File TRIPS_FILE = new File(MeteorClient.FOLDER, "stashhunter_trips.json");
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Vec3d.class, new Vec3dAdapter())
        .setPrettyPrinting()
        .create();

    public static void saveTrips(List<TripData> trips) {
        try (FileWriter writer = new FileWriter(TRIPS_FILE)) {
            GSON.toJson(trips, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<TripData> loadTrips() {
        if (TRIPS_FILE.exists()) {
            try (FileReader reader = new FileReader(TRIPS_FILE)) {
                Type type = new TypeToken<List<TripData>>() {}.getType();
                List<TripData> trips = GSON.fromJson(reader, type);
                if (trips != null) {
                    return trips;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new ArrayList<>();
    }

    public static void addTrip(List<Vec3d> waypoints, int currentWaypoint) {
        List<TripData> trips = loadTrips();
        trips.add(new TripData(System.currentTimeMillis(), waypoints, currentWaypoint));
        saveTrips(trips);
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
        public final List<Vec3d> waypoints;
        public final int currentWaypoint;

        public TripData(long timestamp, List<Vec3d> waypoints, int currentWaypoint) {
            this.timestamp = timestamp;
            this.waypoints = waypoints;
            this.currentWaypoint = currentWaypoint;
        }
    }
}
