package com.stashhunter.stashhunter.signals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Static hub where detector modules publish signals for others to read. Client thread only.
 * Each source keeps at most {@link #MAX_PER_SOURCE} entries; the oldest are evicted first.
 */
public final class SignalHub {

    static final int MAX_PER_SOURCE = 4096;

    private record Key(String dimension, BlockPos pos) {
    }

    private record Entry(String dimension, Signal signal) {
    }

    private static final Map<String, LinkedHashMap<Key, Entry>> SOURCES = new LinkedHashMap<>();

    private SignalHub() {
    }

    public static void put(String source, String dimension, BlockPos pos, SignalKind kind) {
        LinkedHashMap<Key, Entry> entries = SOURCES.computeIfAbsent(source, s -> new LinkedHashMap<>());
        entries.put(new Key(dimension, pos), new Entry(dimension, new Signal(pos, kind)));
        if (entries.size() > MAX_PER_SOURCE) {
            Iterator<Key> oldest = entries.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    public static void remove(String source, String dimension, BlockPos pos) {
        LinkedHashMap<Key, Entry> entries = SOURCES.get(source);
        if (entries != null) {
            entries.remove(new Key(dimension, pos));
        }
    }

    public static void clear(String source) {
        SOURCES.remove(source);
    }

    public static void clearAll() {
        SOURCES.clear();
    }

    /** Signals in {@code dimension} whose horizontal (x/z) distance to (x, z) is at most {@code radius}. */
    public static List<Signal> near(String dimension, int x, int z, int radius) {
        List<Signal> result = new ArrayList<>();
        long maxSq = (long) radius * radius;
        for (LinkedHashMap<Key, Entry> entries : SOURCES.values()) {
            for (Entry entry : entries.values()) {
                if (!entry.dimension().equals(dimension)) {
                    continue;
                }
                BlockPos pos = entry.signal().pos();
                long dx = pos.getX() - x;
                long dz = pos.getZ() - z;
                if (dx * dx + dz * dz <= maxSq) {
                    result.add(entry.signal());
                }
            }
        }
        return result;
    }
}
