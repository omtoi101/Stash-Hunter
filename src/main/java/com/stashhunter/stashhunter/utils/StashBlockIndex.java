package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stash blocks found per chunk, kept across scans. Each scan only sees the chunks around the player,
 * so without memory a stash on the border between two flight strips (strip width = 2 * scan radius by
 * default) is seen half on each pass, and is never reported once neither half reaches the threshold.
 * Only chunks with at least one hit are stored, the oldest are evicted beyond {@code maxChunks}.
 *
 * @param <V> what is stored per block (the block type in game, anything in tests)
 */
public final class StashBlockIndex<V> {
    private final int maxChunks;
    // Insertion order = scan order, so the eldest entry is the chunk scanned longest ago.
    private final Map<String, LinkedHashMap<Long, Map<BlockPos, V>>> byDimension = new HashMap<>();

    public StashBlockIndex(int maxChunks) {
        this.maxChunks = maxChunks;
    }

    /** Replaces everything known about one chunk with the result of a full scan of it. */
    public void update(String dimension, int chunkX, int chunkZ, Map<BlockPos, V> blocks) {
        LinkedHashMap<Long, Map<BlockPos, V>> chunks = byDimension.computeIfAbsent(dimension, d -> new LinkedHashMap<>());
        long key = ChunkPos.pack(chunkX, chunkZ);
        chunks.remove(key); // re-inserted below, so it counts as freshly scanned
        if (blocks.isEmpty()) return;

        chunks.put(key, Map.copyOf(blocks));
        Iterator<Long> eldest = chunks.keySet().iterator();
        while (chunks.size() > maxChunks && eldest.hasNext()) {
            eldest.next();
            eldest.remove();
        }
    }

    /** All known blocks in chunks whose centre lies within {@code radius} blocks (horizontally) of x/z. */
    public Map<BlockPos, V> near(String dimension, double x, double z, int radius) {
        Map<BlockPos, V> result = new HashMap<>();
        LinkedHashMap<Long, Map<BlockPos, V>> chunks = byDimension.get(dimension);
        if (chunks == null) return result;

        double radiusSq = (double) radius * radius;
        for (Map.Entry<Long, Map<BlockPos, V>> entry : chunks.entrySet()) {
            long key = entry.getKey();
            double dx = ChunkPos.getX(key) * 16 + 8 - x;
            double dz = ChunkPos.getZ(key) * 16 + 8 - z;
            if (dx * dx + dz * dz <= radiusSq) result.putAll(entry.getValue());
        }
        return result;
    }

    public int chunkCount() {
        int count = 0;
        for (LinkedHashMap<Long, Map<BlockPos, V>> chunks : byDimension.values()) count += chunks.size();
        return count;
    }

    public void clear() {
        byDimension.clear();
    }
}
