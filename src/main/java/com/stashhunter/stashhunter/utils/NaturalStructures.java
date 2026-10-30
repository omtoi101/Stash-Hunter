package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Positions of natural structures (spawners of dungeons, mineshafts, trial chambers, ...) seen this
 * session, per dimension. Capped per dimension so a long flight can't grow it without bound; the
 * oldest entries go first, they are the farthest behind the player.
 */
public final class NaturalStructures {
    private final int capacityPerDimension;
    private final Map<String, LinkedHashSet<BlockPos>> byDimension = new HashMap<>();

    public NaturalStructures(int capacityPerDimension) {
        if (capacityPerDimension < 1) throw new IllegalArgumentException("capacity must be at least 1");
        this.capacityPerDimension = capacityPerDimension;
    }

    /** @return true if the position was not known yet */
    public boolean add(String dimension, BlockPos pos) {
        LinkedHashSet<BlockPos> positions = byDimension.computeIfAbsent(dimension, d -> new LinkedHashSet<>());
        if (!positions.add(pos.immutable())) return false;
        if (positions.size() > capacityPerDimension) {
            Iterator<BlockPos> oldest = positions.iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    /** 3D distance: a stash on the surface above a deep dungeon is not part of the dungeon. */
    public boolean isNear(String dimension, BlockPos pos, int radius) {
        LinkedHashSet<BlockPos> positions = byDimension.get(dimension);
        if (positions == null || radius <= 0) return false;
        for (BlockPos structure : positions) {
            if (structure.closerThan(pos, radius)) return true;
        }
        return false;
    }

    public int size(String dimension) {
        LinkedHashSet<BlockPos> positions = byDimension.get(dimension);
        return positions == null ? 0 : positions.size();
    }

    public void clear() {
        byDimension.clear();
    }
}
