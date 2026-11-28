package com.stashhunter.stashhunter.signals;

import com.stashhunter.stashhunter.utils.DungeonFilter;
import com.stashhunter.stashhunter.utils.StashClustering;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;

/**
 * The classic (26.2.2) stash decision, without the Minecraft level: threshold check, clustering with tighten,
 * the natural-structure checks and the dedupe decision. Used by StashHunterModule in classic sensitivity.
 */
public final class ClassicRule {
    private ClassicRule() {}

    /**
     * @param threshold block-detection-threshold, used directly
     * @param maxRadius half of max-cluster-size
     */
    public record Settings(int threshold, int linkDistance, int maxRadius, boolean filterNaturalStructures,
                           boolean includeSpawners, int maxVolume, double minDensity) {}

    public enum Action {
        /** New stash report. */
        REPORT,
        /** Stash report that corrects an earlier one (grown stash, or a hint from another sensitivity). */
        UPDATE,
        /** Already reported this session and not grown enough: silently skipped. */
        SKIP,
        /** Not reported; {@link Outcome#reason()} says why. */
        REJECT
    }

    public record Outcome(Action action, Rejection reason) {
        static final Outcome REPORT = new Outcome(Action.REPORT, null);
        static final Outcome UPDATE = new Outcome(Action.UPDATE, null);
        static final Outcome SKIP = new Outcome(Action.SKIP, null);

        static Outcome reject(Rejection reason) {
            return new Outcome(Action.REJECT, reason);
        }
    }

    /** The volume and density limits of filter-natural-structures: natural structures spread a few chests wide. */
    public static boolean hasStashShape(List<BlockPos> cluster, Settings settings) {
        double volume = StashClustering.boundingBoxVolume(cluster);
        return volume <= settings.maxVolume() && cluster.size() / volume >= settings.minDensity();
    }

    /**
     * The groups of {@code found} (classic kinds only) that are judged as stashes: nothing below the threshold;
     * with the structure filter, a cluster too big or too sparse as a whole is reduced to its dense cores.
     */
    public static List<List<BlockPos>> parts(Map<BlockPos, SignalKind> found, Settings settings) {
        int threshold = settings.threshold();
        if (found.size() < threshold) return List.of();
        List<List<BlockPos>> result = new ArrayList<>();
        for (List<BlockPos> cluster : StashClustering.cluster(new ArrayList<>(found.keySet()),
                settings.linkDistance(), settings.maxRadius())) {
            if (cluster.size() < threshold) continue;
            if (settings.filterNaturalStructures()) {
                result.addAll(StashClustering.tighten(cluster, settings.linkDistance(), settings.maxRadius(),
                    threshold, c -> hasStashShape(c, settings)));
            } else {
                result.add(cluster);
            }
        }
        return result;
    }

    /**
     * Whether one part is reported.
     *
     * @param reportedAsStash  this session already reported a stash that is part of this cluster
     * @param grewEnough       that report has grown well past what was reported
     * @param knownTier        the highest report near {@code center} (file and session); read only when needed
     * @param nearStructure    activated-spawner-detector knows a natural structure near the center
     */
    public static Outcome decide(BlockPos center, List<BlockPos> cluster, Map<BlockPos, SignalKind> types,
                                 boolean reportedAsStash, boolean grewEnough, Supplier<Tier> knownTier,
                                 Settings settings, Predicate<BlockPos> nearStructure) {
        boolean update;
        if (reportedAsStash) {
            if (!grewEnough) return Outcome.SKIP;
            update = true;
        } else {
            // Already reported near this location (earlier session, or cleared)? Only a hint from another
            // sensitivity is replaced, never a stash.
            Tier known = knownTier.get();
            if (known == Tier.STASH) return Outcome.reject(Rejection.ALREADY_REPORTED);
            update = known == Tier.HINT;
        }
        // A grown cluster that now looks natural keeps its first report.
        Rejection natural = naturalStructureReason(center, cluster, types, settings, nearStructure);
        if (natural != null) return Outcome.reject(natural);
        return update ? Outcome.UPDATE : Outcome.REPORT;
    }

    /** Why the cluster looks natural, or null. */
    static Rejection naturalStructureReason(BlockPos center, List<BlockPos> blocks, Map<BlockPos, SignalKind> types,
                                            Settings settings, Predicate<BlockPos> nearStructure) {
        if (!settings.filterNaturalStructures()) return null;
        // Volume and density: parts() has already looked for a dense core, so this only rejects what is left.
        if (!hasStashShape(blocks, settings)) return Rejection.STRUCTURE_SHAPE;
        // A vanilla dungeon (spawner + up to two chests) is no base, however low the threshold is.
        if (settings.includeSpawners() && isDungeonOnly(blocks, types)) return Rejection.STRUCTURE_DUNGEON;
        // Exact positions from the spawners activated-spawner-detector has seen.
        if (nearStructure.test(center)) return Rejection.STRUCTURE_SPAWNER;
        // No block-type checks: the cluster only holds scanned stash blocks. Structures without spawners need
        // the world-seed structure locator (PLAN 44).
        return null;
    }

    private static boolean isDungeonOnly(List<BlockPos> blocks, Map<BlockPos, SignalKind> types) {
        Set<BlockPos> spawners = new HashSet<>();
        Set<BlockPos> chests = new HashSet<>();
        for (BlockPos pos : blocks) {
            SignalKind kind = types.get(pos);
            if (kind == SignalKind.SPAWNER) spawners.add(pos);
            else if (kind == SignalKind.CHEST) chests.add(pos);
        }
        return DungeonFilter.isDungeonOnly(blocks, spawners, chests);
    }
}
