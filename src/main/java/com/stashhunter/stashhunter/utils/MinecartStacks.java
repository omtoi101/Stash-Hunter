package com.stashhunter.stashhunter.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups minecart positions into stacks: carts within radius of a stack's first cart belong to it.
 * Stacked carts sit on (almost) the same spot, so this greedy grouping is enough. A spatial grid
 * keeps it linear, a storage stack can hold hundreds of carts.
 */
public final class MinecartStacks {
    /** A stack anchored at its first cart's position. */
    public record Stack(double x, double y, double z, int count) {}

    private static final class Group {
        final double x, y, z;
        int count = 1;

        Group(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final double radius;
    private final double radiusSq;
    // Cells at least as wide as the radius, so a match is always in a neighbouring cell.
    private final double cellSize;
    private final Map<Long, List<Group>> grid = new HashMap<>();
    private final List<Group> groups = new ArrayList<>();

    public MinecartStacks(double radius) {
        this.radius = Math.max(0, radius);
        this.radiusSq = this.radius * this.radius;
        this.cellSize = Math.max(this.radius, 0.5);
    }

    public void add(double x, double y, double z) {
        int cx = cell(x), cy = cell(y), cz = cell(z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<Group> bucket = grid.get(key(cx + dx, cy + dy, cz + dz));
                    if (bucket == null) continue;
                    for (Group g : bucket) {
                        double ox = g.x - x, oy = g.y - y, oz = g.z - z;
                        if (ox * ox + oy * oy + oz * oz <= radiusSq) {
                            g.count++;
                            return;
                        }
                    }
                }
            }
        }
        Group g = new Group(x, y, z);
        groups.add(g);
        grid.computeIfAbsent(key(cx, cy, cz), k -> new ArrayList<>()).add(g);
    }

    /** Stacks with at least minSize carts, in the order their first cart was added. */
    public List<Stack> stacks(int minSize) {
        List<Stack> result = new ArrayList<>();
        for (Group g : groups) {
            if (g.count >= minSize) result.add(new Stack(g.x, g.y, g.z, g.count));
        }
        return result;
    }

    private int cell(double v) {
        return (int) Math.floor(v / cellSize);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x1FFFFF) << 42 | ((long) y & 0x1FFFFF) << 21 | ((long) z & 0x1FFFFF);
    }
}
