package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Tells a natural dungeon apart from a stash once spawners are part of the scan. A vanilla dungeon
 * is one mob spawner in the middle of a room of at most 9x9 inside, with up to two chests on the
 * floor along the walls. A cluster made only of such spawners and their chests is natural; any
 * other stash block in it (hoppers, extra chests, shulkers, ...) makes it a candidate again.
 */
public final class DungeonFilter {
    // Spawner sits at the room's centre; the largest room is 9x9 inside, so chests stay within 4.
    static final int DUNGEON_HALF_WIDTH = 4;
    // Chests stand on the floor, the spawner one block above it.
    static final int DUNGEON_HALF_HEIGHT = 1;
    static final int CHESTS_PER_DUNGEON = 2;

    private DungeonFilter() {
    }

    /**
     * @param cluster  all blocks of one cluster
     * @param spawners mob spawner positions (any superset is fine, only members of the cluster count)
     * @param chests   plain chest positions (dungeons never generate trapped chests or barrels)
     * @return true if every block of the cluster is a spawner or a chest a dungeon can explain
     */
    public static boolean isDungeonOnly(List<BlockPos> cluster, Set<BlockPos> spawners, Set<BlockPos> chests) {
        if (cluster.isEmpty() || spawners.isEmpty()) return false;

        List<BlockPos> clusterSpawners = cluster.stream().filter(spawners::contains).toList();
        if (clusterSpawners.isEmpty()) return false;

        int explainable = clusterSpawners.size() * CHESTS_PER_DUNGEON;
        for (BlockPos pos : cluster) {
            if (spawners.contains(pos)) continue;
            if (!chests.contains(pos) || !nearAny(pos, clusterSpawners)) return false;
            if (--explainable < 0) return false;
        }
        return true;
    }

    private static boolean nearAny(BlockPos chest, Collection<BlockPos> spawners) {
        for (BlockPos spawner : spawners) {
            if (Math.abs(chest.getX() - spawner.getX()) <= DUNGEON_HALF_WIDTH
                && Math.abs(chest.getZ() - spawner.getZ()) <= DUNGEON_HALF_WIDTH
                && Math.abs(chest.getY() - spawner.getY()) <= DUNGEON_HALF_HEIGHT) {
                return true;
            }
        }
        return false;
    }
}
