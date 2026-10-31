package com.stashhunter.stashhunter.utils;

import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers when each chunk was last scanned for stash blocks, so a scan pass only reads chunks
 * that are new, changed since their scan ({@link #invalidate}) or due for a periodic re-check.
 * Ticks are supplied by the caller.
 */
public final class ChunkScanSchedule {
    private final long rescanTicks;
    private final Map<String, Map<Long, Long>> byDimension = new HashMap<>();

    public ChunkScanSchedule(long rescanTicks) {
        this.rescanTicks = rescanTicks;
    }

    public boolean isDue(String dimension, int chunkX, int chunkZ, long now) {
        Map<Long, Long> chunks = byDimension.get(dimension);
        Long last = chunks == null ? null : chunks.get(ChunkPos.pack(chunkX, chunkZ));
        return last == null || now - last >= rescanTicks;
    }

    public void markScanned(String dimension, int chunkX, int chunkZ, long now) {
        byDimension.computeIfAbsent(dimension, d -> new HashMap<>()).put(ChunkPos.pack(chunkX, chunkZ), now);
    }

    /** The chunk changed (blocks updated, chunk data resent): scan it again on the next pass. */
    public void invalidate(String dimension, int chunkX, int chunkZ) {
        Map<Long, Long> chunks = byDimension.get(dimension);
        if (chunks != null) chunks.remove(ChunkPos.pack(chunkX, chunkZ));
    }

    /** Drops entries that are due anyway, so the map stays bounded during a long flight. */
    public void prune(long now) {
        for (Map<Long, Long> chunks : byDimension.values()) {
            chunks.values().removeIf(last -> now - last >= rescanTicks);
        }
    }

    public void clear() {
        byDimension.clear();
    }

    public int size() {
        int size = 0;
        for (Map<Long, Long> chunks : byDimension.values()) size += chunks.size();
        return size;
    }
}
