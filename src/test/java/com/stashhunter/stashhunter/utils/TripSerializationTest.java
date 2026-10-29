package com.stashhunter.stashhunter.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Uses the same Gson setup as TripManager; TripManager itself needs a running Meteor (its file path).
class TripSerializationTest {
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Vec3.class, new Vec3dAdapter()).create();
    private static final Type TRIPS = new TypeToken<List<TripManager.TripData>>() {}.getType();

    @Test
    void tripRoundTrips() {
        TripManager.TripData trip = new TripManager.TripData(1234L,
            List.of(new Vec3(1.5, 160, -2), new Vec3(100, 160, 300)), 1);

        List<TripManager.TripData> read = GSON.fromJson(GSON.toJson(List.of(trip)), TRIPS);

        assertEquals(1, read.size());
        assertEquals(1234L, read.get(0).timestamp);
        assertEquals(1, read.get(0).currentWaypoint);
        assertEquals(trip.waypoints, read.get(0).waypoints);
    }

    @Test
    void unknownFieldsAreSkipped() {
        Vec3 vec = GSON.fromJson("{\"x\": 1, \"note\": {\"a\": [1, 2]}, \"y\": 2, \"z\": 3}", Vec3.class);
        assertEquals(new Vec3(1, 2, 3), vec);
    }

    @Test
    void missingCoordinateIsRejectedInsteadOfBecomingZero() {
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{\"x\": 1, \"z\": 3}", Vec3.class));
    }
}
