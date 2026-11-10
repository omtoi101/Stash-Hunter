package com.stashhunter.stashhunter.utils;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Chunks that lay inside the scan radius while they were not loaded yet. A server that sends chunks
 * slower than the bot flies delivers the ones at the edge of a flight strip after the player has
 * passed them, and the scan around the player never reaches them again. They are remembered and
 * scanned once they arrive. Entries expire after {@code expireTicks} (the server stops sending a
 * chunk once the player is far enough away); at most {@code maxChunks} are kept, the oldest go first.
 * Ticks are supplied by the caller.
 */
public final class MissedChunks {
    private final long expireTicks;
    private final int maxChunks;
    // Insertion order = order of the first miss, so the eldest entry is the one waiting longest.
    private final Map<String, LinkedHashMap<Long, Long>> byDimension = new HashMap<>();
    private int dropped = 0;

    public MissedChunks(long expireTicks, int maxChunks) {
        this.expireTicks = expireTicks;
        this.maxChunks = maxChunks;
    }

    /** The chunk is inside the scan radius but not loaded. A chunk already waiting keeps its first miss time. */
    public void add(String dimension, int chunkX, int chunkZ, long now) {
        LinkedHashMap<Long, Long> chunks = byDimension.computeIfAbsent(dimension, d -> new LinkedHashMap<>());
        chunks.putIfAbsent(ChunkPos.pack(chunkX, chunkZ), now);
        Iterator<Long> eldest = chunks.keySet().iterator();
        while (chunks.size() > maxChunks && eldest.hasNext()) {
            eldest.next();
            eldest.remove();
            dropped++;
        }
    }

    /** Removes and returns the packed positions ({@link ChunkPos#pack}) of the waiting chunks that {@code loaded} accepts. */
    public List<Long> takeLoaded(String dimension, LongPredicate loaded) {
        List<Long> result = new ArrayList<>();
        LinkedHashMap<Long, Long> chunks = byDimension.get(dimension);
        if (chunks == null) return result;
        Iterator<Long> it = chunks.keySet().iterator();
        while (it.hasNext()) {
            long key = it.next();
            if (loaded.test(key)) {
                result.add(key);
                it.remove();
            }
        }
        return result;
    }

    /** Gives up on chunks that have waited {@code expireTicks} or longer. */
    public void expire(long now) {
        for (LinkedHashMap<Long, Long> chunks : byDimension.values()) {
            Iterator<Long> firstMiss = chunks.values().iterator();
            while (firstMiss.hasNext()) {
                if (now - firstMiss.next() < expireTicks) break; // the rest missed later
                firstMiss.remove();
                dropped++;
            }
        }
    }

    /** Chunks given up on (expired or evicted) since the last call; they were never scanned. */
    public int takeDropped() {
        int result = dropped;
        dropped = 0;
        return result;
    }

    public int size() {
        int size = 0;
        for (LinkedHashMap<Long, Long> chunks : byDimension.values()) size += chunks.size();
        return size;
    }

    /** Forgets everything without counting it as dropped (dimension change, leaving the server). */
    public void clear() {
        byDimension.clear();
        dropped = 0;
    }
}
