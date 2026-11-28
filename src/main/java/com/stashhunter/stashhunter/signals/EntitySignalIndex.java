package com.stashhunter.stashhunter.signals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Entity signals keyed by entity, so one entity gives at most one signal: an entity seen somewhere
 * else moves there. Entities nobody saw this scan stay where they were last seen, except in the
 * chunks the caller clears (where their entities are surely loaded). Client thread only.
 */
public final class EntitySignalIndex {
    public record Seen(UUID id, BlockPos pos, SignalKind kind) {
    }

    private record Entry(String dimension, BlockPos pos, SignalKind kind) {
    }

    private final int maxEntries;
    // Insertion order = last seen, so the eldest entry is the entity seen longest ago.
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();

    public EntitySignalIndex(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Records the entities seen in {@code dimension} this scan, then forgets every other entity of
     * that dimension whose last position lies in a chunk ({@link ChunkPos#pack}) {@code cleared} accepts.
     */
    public void update(String dimension, Collection<Seen> seen, LongPredicate cleared) {
        Set<UUID> ids = new HashSet<>();
        for (Seen s : seen) {
            ids.add(s.id());
            entries.remove(s.id());
            entries.put(s.id(), new Entry(dimension, s.pos().immutable(), s.kind()));
        }
        entries.entrySet().removeIf(e -> !ids.contains(e.getKey()) && e.getValue().dimension().equals(dimension)
                && cleared.test(ChunkPos.pack(e.getValue().pos())));
        Iterator<UUID> eldest = entries.keySet().iterator();
        while (entries.size() > maxEntries && eldest.hasNext()) {
            eldest.next();
            eldest.remove();
        }
    }

    /** Signals in {@code dimension} within {@code radius} blocks (horizontally) of x/z. */
    public List<Signal> near(String dimension, int x, int z, int radius) {
        List<Signal> result = new ArrayList<>();
        long maxSq = (long) radius * radius;
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            if (!entry.dimension().equals(dimension)) continue;
            long dx = entry.pos().getX() - x, dz = entry.pos().getZ() - z;
            if (dx * dx + dz * dz <= maxSq) result.add(new Signal(entry.pos(), entry.kind()));
        }
        return result;
    }

    public void clear() {
        entries.clear();
    }
}
