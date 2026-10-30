package com.stashhunter.stashhunter.utils;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Decides when an already reported stash has to be reported again because more of it was found.
 * A stash first seen only in part (at the edge of the scanned area) is reported with too few blocks
 * and an off-centre position; once the rest comes into range, its cluster contains the old blocks
 * plus new ones.
 */
public final class StashGrowth {
    // Small changes (a chest placed or broken) are not worth a second report.
    static final int MIN_EXTRA_BLOCKS = 3;
    static final int MIN_GROWTH_PERCENT = 20;

    private StashGrowth() {
    }

    /** Indices of the known stashes that share at least one block with {@code cluster}. */
    public static List<Integer> overlapping(List<? extends Set<BlockPos>> known, Collection<BlockPos> cluster) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < known.size(); i++) {
            Set<BlockPos> blocks = known.get(i);
            for (BlockPos pos : cluster) {
                if (blocks.contains(pos)) {
                    result.add(i);
                    break;
                }
            }
        }
        return result;
    }

    /** Whether a stash reported with {@code reportedBlocks} that now has {@code currentBlocks} grew enough. */
    public static boolean grewEnough(int reportedBlocks, int currentBlocks) {
        int needed = Math.max(MIN_EXTRA_BLOCKS, reportedBlocks * MIN_GROWTH_PERCENT / 100);
        return currentBlocks - reportedBlocks >= needed;
    }
}
