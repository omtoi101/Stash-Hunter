package com.stashhunter.stashhunter.utils;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;

public class Vec3dAdapter extends TypeAdapter<Vec3> {
    @Override
    public void write(JsonWriter out, Vec3 value) throws IOException {
        out.beginObject();
        out.name("x").value(value.x);
        out.name("y").value(value.y);
        out.name("z").value(value.z);
        out.endObject();
    }

    @Override
    public Vec3 read(JsonReader in) throws IOException {
        in.beginObject();
        double x = Double.NaN, y = Double.NaN, z = Double.NaN;
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "x":
                    x = in.nextDouble();
                    break;
                case "y":
                    y = in.nextDouble();
                    break;
                case "z":
                    z = in.nextDouble();
                    break;
                default:
                    in.skipValue(); // tolerate fields added by other versions
            }
        }
        in.endObject();
        // A missing coordinate must not silently become 0 - that would send the bot to the origin.
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new JsonParseException("Waypoint needs finite x, y and z, got (" + x + ", " + y + ", " + z + ")");
        }
        return new Vec3(x, y, z);
    }
}
