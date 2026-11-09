package com.stashhunter.stashhunter.utils;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds small pockets of plain air that touch cave air.
 * <p>
 * World generation fills some spaces with cave air: the Nether's carver caves, mineshafts,
 * strongholds, dungeons, and the overworld caves of chunks generated in 1.13-1.17. A block a player
 * breaks there (or places and removes again) becomes plain air. Overworld caves generated since 1.18
 * are plain air from the start (the aquifer returns air for carvers too), so they show nothing.
 * <p>
 * A pocket is a 6-connected group of air blocks, at least one of them next to cave air, closed in
 * by cave air and other blocks. A group larger than maxSize is open natural space (a noise cave a
 * mineshaft runs through, the sky above a ravine) and is ignored.
 * <p>
 * Clean-room: written from the generation rules above only. The idea is known from Trouser-Streak
 * (no license); none of its code was looked at or used.
 */
public final class CaveAirPockets {
    public static final int OTHER = 0;
    public static final int AIR = 1;
    public static final int CAVE_AIR = 2;
    /** Not readable (outside the chunk copy, chunk not loaded): a pocket reaching it can't be judged yet. */
    public static final int UNKNOWN = 3;

    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private CaveAirPockets() {}

    /** One of OTHER, AIR, CAVE_AIR, UNKNOWN for any position. */
    @FunctionalInterface
    public interface Blocks {
        int at(int x, int y, int z);
    }

    public record Cell(int x, int y, int z) {}

    /**
     * resolved: closed in on all sides. False if it reached UNKNOWN blocks, i.e. it may continue into
     * a chunk that wasn't readable and has to be checked again once that is loaded.
     */
    public record Pocket(List<Cell> cells, boolean resolved) {}

    /**
     * Pockets with at least one air block inside the box (inclusive) next to cave air. The flood fill
     * may leave the box; blocks outside it are read through {@code blocks} as well.
     */
    public static List<Pocket> find(Blocks blocks, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int maxSize) {
        List<Pocket> pockets = new ArrayList<>();
        // Position -> id of the flood fill that reached it. A fill reaching another fill's block is
        // part of a group that fill gave up on as too big (groups are disjoint otherwise).
        Long2IntOpenHashMap seen = new Long2IntOpenHashMap();
        int fills = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (blocks.at(x, y, z) != AIR || seen.containsKey(pack(x, y, z)) || !touchesCaveAir(blocks, x, y, z)) continue;
                    Pocket pocket = fill(blocks, x, y, z, maxSize, seen, ++fills);
                    if (pocket != null) pockets.add(pocket);
                }
            }
        }
        return pockets;
    }

    private static boolean touchesCaveAir(Blocks blocks, int x, int y, int z) {
        for (int[] d : NEIGHBOURS) {
            if (blocks.at(x + d[0], y + d[1], z + d[2]) == CAVE_AIR) return true;
        }
        return false;
    }

    // Breadth-first; the cell list doubles as the queue. Null if the group is bigger than maxSize.
    private static Pocket fill(Blocks blocks, int startX, int startY, int startZ, int maxSize, Long2IntOpenHashMap seen, int id) {
        List<Cell> cells = new ArrayList<>();
        seen.put(pack(startX, startY, startZ), id);
        cells.add(new Cell(startX, startY, startZ));
        boolean resolved = true;
        for (int next = 0; next < cells.size(); next++) {
            Cell cell = cells.get(next);
            for (int[] d : NEIGHBOURS) {
                int x = cell.x() + d[0], y = cell.y() + d[1], z = cell.z() + d[2];
                int kind = blocks.at(x, y, z);
                if (kind == UNKNOWN) {
                    resolved = false;
                    continue;
                }
                if (kind != AIR) continue;
                long key = pack(x, y, z);
                int owner = seen.getOrDefault(key, 0);
                if (owner == id) continue;
                if (owner != 0 || cells.size() >= maxSize) return null; // too big
                seen.put(key, id);
                cells.add(new Cell(x, y, z));
            }
        }
        return new Pocket(List.copyOf(cells), resolved);
    }

    // 26 bits x, 26 bits z, 12 bits y (like BlockPos): world coordinates fit.
    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }
}
