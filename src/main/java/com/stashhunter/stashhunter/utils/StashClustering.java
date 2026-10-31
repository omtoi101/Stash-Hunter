package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Pure geometry for turning scanned block positions into stash candidates. */
public final class StashClustering {
    private StashClustering() {}

    /**
     * Groups nearby blocks into clusters to separate stashes from natural structures.
     * Seeds are taken densest-first; a block joins a cluster when it is within linkDistance of a
     * member and within maxRadius of the seed, so chains of blocks can't grow a cluster without bound.
     */
    public static List<List<BlockPos>> cluster(List<BlockPos> blocks, int linkDistance, int maxRadius) {
        long linkSq = (long) linkDistance * linkDistance;
        long radiusSq = (long) maxRadius * maxRadius;

        // Grid with cell size = linkDistance: every neighbour of a block lies in the 27 surrounding cells.
        Map<Long, List<BlockPos>> grid = new HashMap<>();
        for (BlockPos pos : blocks) {
            grid.computeIfAbsent(cellKey(pos, linkDistance), k -> new ArrayList<>()).add(pos);
        }

        Map<BlockPos, Integer> neighbourCount = new HashMap<>();
        for (BlockPos pos : blocks) {
            neighbourCount.put(pos, countNeighbours(pos, grid, linkDistance, linkSq));
        }
        List<BlockPos> seeds = new ArrayList<>(blocks);
        seeds.sort(Comparator.comparingInt((BlockPos pos) -> neighbourCount.get(pos)).reversed());

        List<List<BlockPos>> clusters = new ArrayList<>();
        Set<BlockPos> assigned = new HashSet<>();
        for (BlockPos seed : seeds) {
            if (!assigned.add(seed)) continue;

            List<BlockPos> cluster = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            cluster.add(seed);
            queue.add(seed);
            while (!queue.isEmpty()) {
                for (BlockPos next : neighbours(queue.poll(), grid, linkDistance, linkSq)) {
                    if (next.distSqr(seed) <= radiusSq && assigned.add(next)) {
                        cluster.add(next);
                        queue.add(next);
                    }
                }
            }
            clusters.add(cluster);
        }

        return clusters;
    }

    // tighten() stops halving below this: containers a few blocks apart still belong together.
    static final int MIN_LINK_DISTANCE = 4;

    /**
     * Returns the parts of {@code cluster} that pass {@code accept}. A rejected cluster is clustered
     * again with half the link distance and radius, down to {@link #MIN_LINK_DISTANCE}, and its parts
     * are checked on their own; parts smaller than {@code minSize} are dropped. A stash inside or next
     * to a natural structure, or a base with a few chests far off, then still shows up as its dense
     * core instead of being thrown away with the oversized or sparse cluster around it.
     */
    public static List<List<BlockPos>> tighten(List<BlockPos> cluster, int linkDistance, int maxRadius, int minSize,
                                               Predicate<List<BlockPos>> accept) {
        if (cluster.size() < minSize) return List.of();
        if (accept.test(cluster)) return List.of(cluster);

        int link = linkDistance / 2;
        if (link < MIN_LINK_DISTANCE) return List.of();
        int radius = Math.max(maxRadius / 2, link);
        List<List<BlockPos>> result = new ArrayList<>();
        for (List<BlockPos> part : cluster(cluster, link, radius)) {
            result.addAll(tighten(part, link, radius, minSize, accept));
        }
        return result;
    }

    private static long cellKey(BlockPos pos, int cellSize) {
        return BlockPos.asLong(Math.floorDiv(pos.getX(), cellSize), Math.floorDiv(pos.getY(), cellSize), Math.floorDiv(pos.getZ(), cellSize));
    }

    private static List<BlockPos> neighbours(BlockPos pos, Map<Long, List<BlockPos>> grid, int cellSize, long linkSq) {
        List<BlockPos> result = new ArrayList<>();
        int cx = Math.floorDiv(pos.getX(), cellSize), cy = Math.floorDiv(pos.getY(), cellSize), cz = Math.floorDiv(pos.getZ(), cellSize);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<BlockPos> cell = grid.get(BlockPos.asLong(cx + dx, cy + dy, cz + dz));
                    if (cell == null) continue;
                    for (BlockPos other : cell) {
                        if (other != pos && other.distSqr(pos) < linkSq) result.add(other);
                    }
                }
            }
        }
        return result;
    }

    // Same test as neighbours(), without building the list: in a dense stash every block has hundreds of neighbours.
    private static int countNeighbours(BlockPos pos, Map<Long, List<BlockPos>> grid, int cellSize, long linkSq) {
        int count = 0;
        int cx = Math.floorDiv(pos.getX(), cellSize), cy = Math.floorDiv(pos.getY(), cellSize), cz = Math.floorDiv(pos.getZ(), cellSize);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<BlockPos> cell = grid.get(BlockPos.asLong(cx + dx, cy + dy, cz + dz));
                    if (cell == null) continue;
                    for (BlockPos other : cell) {
                        if (other != pos && other.distSqr(pos) < linkSq) count++;
                    }
                }
            }
        }
        return count;
    }

    /** Volume of the block-aligned bounding box around {@code blocks}; at least 1, so it is safe to divide by. */
    public static double boundingBoxVolume(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return 0;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos block : blocks) {
            minX = Math.min(minX, block.getX());
            minY = Math.min(minY, block.getY());
            minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX());
            maxY = Math.max(maxY, block.getY());
            maxZ = Math.max(maxZ, block.getZ());
        }

        // +1 per axis because the positions are whole blocks
        double volume = (maxX - minX + 1.0) * (maxY - minY + 1.0) * (maxZ - minZ + 1.0);
        return Math.max(volume, 1.0);
    }

    /** Average position of {@code blocks}, or empty for an empty list (instead of a misleading 0, 0, 0). */
    public static Optional<BlockPos> center(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return Optional.empty();
        }

        long totalX = 0, totalY = 0, totalZ = 0;
        for (BlockPos pos : blocks) {
            totalX += pos.getX();
            totalY += pos.getY();
            totalZ += pos.getZ();
        }
        int n = blocks.size();
        return Optional.of(new BlockPos((int) (totalX / n), (int) (totalY / n), (int) (totalZ / n)));
    }
}
