package com.stashhunter.stashhunter.utils;

/** Clean-room; idea from Trouser-Streak/etianl's hole/tunnel ESP, no code used. */
public final class TunnelShapes {
    private TunnelShapes() {}

    public interface Passable {
        boolean at(int x, int y, int z);
    }

    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * 1x1 shaft: the column (x, y down to y-minDepth+1) is passable, its four horizontal neighbours at every level
     * are not, and (x, y+1) is passable (open top).
     */
    public static boolean isHole(Passable p, int x, int y, int z, int minDepth) {
        if (!p.at(x, y + 1, z)) return false;
        for (int d = 0; d < minDepth; d++) {
            int yy = y - d;
            if (!p.at(x, yy, z)) return false;
            for (int[] n : HORIZONTAL) if (p.at(x + n[0], yy, z + n[1])) return false;
        }
        return true;
    }

    /**
     * Corridor starting at (x, y, z), running minLength blocks along +X or +Z. Each step: y and y+1 passable, floor
     * (y-1) solid, y+3 solid (so at most 3 high; y+2 may be open), side walls at y and y+1 solid.
     */
    public static boolean isTunnel(Passable p, int x, int y, int z, int minLength) {
        return tunnelAlong(p, x, y, z, minLength, 1, 0) || tunnelAlong(p, x, y, z, minLength, 0, 1);
    }

    private static boolean tunnelAlong(Passable p, int x, int y, int z, int len, int dx, int dz) {
        // Side offsets are perpendicular to the direction of travel.
        int sx = dz, sz = dx;
        for (int i = 0; i < len; i++) {
            int cx = x + i * dx, cz = z + i * dz;
            if (!p.at(cx, y, cz) || !p.at(cx, y + 1, cz)) return false;
            if (p.at(cx, y - 1, cz) || p.at(cx, y + 3, cz)) return false;
            for (int yy = y; yy <= y + 1; yy++) {
                if (p.at(cx + sx, yy, cz + sz) || p.at(cx - sx, yy, cz - sz)) return false;
            }
        }
        return true;
    }

    /**
     * Staircase: for minSteps steps along one of the four horizontal directions, step i stands at
     * (x+i*dx, y+i, z+i*dz): that block and the one above are passable, the one below is solid.
     */
    public static boolean isStair(Passable p, int x, int y, int z, int minSteps) {
        for (int[] d : HORIZONTAL) {
            boolean ok = true;
            for (int i = 0; i < minSteps && ok; i++) {
                int cx = x + i * d[0], cy = y + i, cz = z + i * d[1];
                ok = p.at(cx, cy, cz) && p.at(cx, cy + 1, cz) && !p.at(cx, cy - 1, cz);
            }
            if (ok) return true;
        }
        return false;
    }
}
