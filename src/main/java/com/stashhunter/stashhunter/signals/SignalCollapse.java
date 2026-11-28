package com.stashhunter.stashhunter.signals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * Collapses per-block traces of a chunk scan to one signal per (kind, chunk section), so a multi-block structure
 * (a concrete wall, a portal, map art) stays one signal and the clusterer's input stays bounded (ruling R12/R13).
 * STRONG and CONTAINER kinds stay per block: they count per block in the score and in the container condition.
 */
public final class SignalCollapse {
    private SignalCollapse() {}

    private record Key(SignalKind kind, long section) {}

    public static Map<BlockPos, SignalKind> perSection(Map<BlockPos, SignalKind> signals) {
        Map<BlockPos, SignalKind> result = new HashMap<>();
        Map<Key, List<BlockPos>> groups = new HashMap<>();
        for (Map.Entry<BlockPos, SignalKind> e : signals.entrySet()) {
            SignalCategory category = e.getValue().category();
            if (category == SignalCategory.TRACE || category == SignalCategory.WEAK_TRACE) {
                Key key = new Key(e.getValue(), SectionPos.asLong(e.getKey()));
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e.getKey());
            } else {
                result.put(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<Key, List<BlockPos>> g : groups.entrySet()) {
            BlockPos kept = nearestToMiddle(g.getValue());
            // A container already at this position wins; a trace never sits on the same block anyway.
            result.putIfAbsent(kept, g.getKey().kind());
        }
        return result;
    }

    /** The member nearest the members' mean, so the kept signal links to nearby clusters like the whole group. */
    private static BlockPos nearestToMiddle(List<BlockPos> members) {
        double mx = 0, my = 0, mz = 0;
        for (BlockPos p : members) {
            mx += p.getX();
            my += p.getY();
            mz += p.getZ();
        }
        mx /= members.size();
        my /= members.size();
        mz /= members.size();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : members) {
            double dx = p.getX() - mx, dy = p.getY() - my, dz = p.getZ() - mz;
            double d = dx * dx + dy * dy + dz * dz;
            if (best == null || d < bestDist || (d == bestDist && p.compareTo(best) < 0)) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }
}
