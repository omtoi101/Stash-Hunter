package com.stashhunter.stashhunter.signals;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Detects nether portal frames (vertical obsidian rectangles) in a set of obsidian positions. */
public final class PortalFrames {
    private static final int MIN_W = 2, MAX_W = 21, MIN_H = 3, MAX_H = 21;

    private PortalFrames() {}

    /**
     * One entry per frame: the minimum corner (lowest x, y, z) of the outer rectangle. Frames in the X-Y and Z-Y
     * plane are found; the four corner blocks and the inside are not checked. Every obsidian block is tried as the
     * first block of a bottom edge (corner + 1 along the plane axis).
     */
    public static List<BlockPos> frames(Set<BlockPos> obsidian) {
        Set<BlockPos> found = new LinkedHashSet<>();
        for (BlockPos b : obsidian) {
            for (int axis = 0; axis < 2; axis++) {
                int dx = axis == 0 ? 1 : 0, dz = axis == 0 ? 0 : 1;
                int x0 = b.getX() - dx, y0 = b.getY(), z0 = b.getZ() - dz;
                // Bottom edge: inner width w blocks starting at b.
                for (int w = 1; w <= MAX_W && obsidian.contains(at(x0, y0, z0, dx, dz, w, 0)); w++) {
                    if (w < MIN_W) continue;
                    for (int h = MIN_H; h <= MAX_H; h++) {
                        if (isFrame(obsidian, x0, y0, z0, dx, dz, w, h)) found.add(new BlockPos(x0, y0, z0));
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static BlockPos at(int x0, int y0, int z0, int dx, int dz, int along, int up) {
        return new BlockPos(x0 + dx * along, y0 + up, z0 + dz * along);
    }

    private static boolean isFrame(Set<BlockPos> o, int x0, int y0, int z0, int dx, int dz, int w, int h) {
        for (int i = 1; i <= w; i++) {
            if (!o.contains(at(x0, y0, z0, dx, dz, i, 0)) || !o.contains(at(x0, y0, z0, dx, dz, i, h + 1))) return false;
        }
        for (int j = 1; j <= h; j++) {
            if (!o.contains(at(x0, y0, z0, dx, dz, 0, j)) || !o.contains(at(x0, y0, z0, dx, dz, w + 1, j))) return false;
        }
        return true;
    }
}
