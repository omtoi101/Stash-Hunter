package com.stashhunter.stashhunter.signals;

import com.stashhunter.stashhunter.utils.DungeonFilter;
import com.stashhunter.stashhunter.utils.StashClustering;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Groups signals into clusters and applies the natural-structure filter to the containers only. */
public final class SignalClusterer {
    private SignalClusterer() {
    }

    public static List<Candidate> candidates(Collection<Signal> signals, Set<BlockPos> spawners, int linkDistance,
                                             int maxRadius, int minContainers, ContainerFilter filter) {
        Set<BlockPos> distinct = new LinkedHashSet<>();
        for (Signal s : signals) distinct.add(s.pos());

        List<Candidate> result = new ArrayList<>();
        for (List<BlockPos> cluster : StashClustering.cluster(new ArrayList<>(distinct), linkDistance, maxRadius)) {
            Set<BlockPos> inCluster = new HashSet<>(cluster);
            List<Signal> clusterSignals = new ArrayList<>();
            for (Signal s : signals) if (inCluster.contains(s.pos())) clusterSignals.add(s);

            Candidate candidate = build(cluster, clusterSignals, spawners, linkDistance, maxRadius, minContainers, filter);
            if (candidate != null) result.add(candidate);
        }
        return result;
    }

    private static Candidate build(List<BlockPos> cluster, List<Signal> clusterSignals, Set<BlockPos> spawners,
                                   int linkDistance, int maxRadius, int minContainers, ContainerFilter filter) {
        Set<BlockPos> containers = new LinkedHashSet<>();
        Set<BlockPos> chests = new HashSet<>();
        Set<BlockPos> strong = new LinkedHashSet<>();
        for (Signal s : clusterSignals) {
            switch (s.kind().category()) {
                case CONTAINER -> {
                    containers.add(s.pos());
                    if (s.kind() == SignalKind.CHEST) chests.add(s.pos());
                }
                case STRONG -> strong.add(s.pos());
                default -> {
                }
            }
        }

        Map<Rejection, Integer> filtered = new EnumMap<>(Rejection.class);
        Set<BlockPos> kept = new LinkedHashSet<>(containers);

        if (filter.filterNaturalStructures() && !containers.isEmpty()) {
            if (isDungeon(cluster, containers, spawners, chests)) {
                reject(kept, new HashSet<>(kept), Rejection.STRUCTURE_DUNGEON, filtered);
            } else {
                Set<BlockPos> inParts = new HashSet<>();
                if (containers.size() >= minContainers) {
                    for (List<BlockPos> part : StashClustering.tighten(new ArrayList<>(containers), linkDistance,
                            maxRadius, minContainers, filter::hasStashShape)) {
                        inParts.addAll(part);
                    }
                } else if (filter.hasStashShape(new ArrayList<>(containers))) {
                    // Too few for tighten (it would drop everything): judge the group as it is.
                    inParts.addAll(containers);
                }
                Set<BlockPos> outside = new HashSet<>(kept);
                outside.removeAll(inParts);
                reject(kept, outside, Rejection.STRUCTURE_SHAPE, filtered);
            }
            if (!kept.isEmpty() && filter.nearStructureSpawner(centerOf(clusterSignals, kept, containers))) {
                reject(kept, new HashSet<>(kept), Rejection.STRUCTURE_SPAWNER, filtered);
            }
        }

        List<Signal> remaining = new ArrayList<>();
        for (Signal s : clusterSignals) {
            if (s.kind().category() == SignalCategory.CONTAINER && !kept.contains(s.pos())) continue;
            remaining.add(s);
        }
        if (remaining.isEmpty()) return null;

        List<BlockPos> remainingPositions = remaining.stream().map(Signal::pos).distinct().toList();
        BlockPos center = StashClustering.center(remainingPositions).orElseThrow();

        // STRONG kinds count for the shape, like in SignalScoring's storage count.
        Set<BlockPos> shapeSet = new LinkedHashSet<>(kept);
        shapeSet.addAll(strong);
        boolean shapeOk = !shapeSet.isEmpty() && filter.hasStashShape(new ArrayList<>(shapeSet));
        return new Candidate(center, remaining, new ArrayList<>(kept), shapeOk, filtered);
    }

    private static boolean isDungeon(List<BlockPos> cluster, Set<BlockPos> containers, Set<BlockPos> spawners,
                                     Set<BlockPos> chests) {
        if (spawners.isEmpty()) return false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : cluster) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        // DungeonFilter needs the spawner positions in the list it checks.
        Set<BlockPos> checked = new LinkedHashSet<>(containers);
        for (BlockPos sp : spawners) {
            if (sp.getX() >= minX && sp.getX() <= maxX && sp.getY() >= minY && sp.getY() <= maxY
                    && sp.getZ() >= minZ && sp.getZ() <= maxZ) {
                checked.add(sp);
            }
        }
        return DungeonFilter.isDungeonOnly(new ArrayList<>(checked), spawners, chests);
    }

    /** Center of the cluster without the containers already removed. */
    private static BlockPos centerOf(List<Signal> clusterSignals, Set<BlockPos> kept, Set<BlockPos> allContainers) {
        List<BlockPos> positions = new ArrayList<>();
        for (Signal s : clusterSignals) {
            if (!allContainers.contains(s.pos()) || kept.contains(s.pos())) positions.add(s.pos());
        }
        return StashClustering.center(positions).orElseThrow();
    }

    private static void reject(Set<BlockPos> kept, Set<BlockPos> toRemove, Rejection reason,
                               Map<Rejection, Integer> filtered) {
        if (toRemove.isEmpty()) return;
        kept.removeAll(toRemove);
        filtered.merge(reason, toRemove.size(), Integer::sum);
    }
}
