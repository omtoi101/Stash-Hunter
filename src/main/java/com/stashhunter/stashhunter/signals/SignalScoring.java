package com.stashhunter.stashhunter.signals;

import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Scores one cluster of signals into NONE, HINT or STASH. Rules: see globals "Gewichte und Schwellen", with ruling
 * R12: each TRACE and WEAK_TRACE kind counts once per cluster (a wall of concrete or a portal is one trace), open
 * portal and obsidian frame count as one portal trace, and a hint without a STRONG kind needs two distinct kinds.
 * STRONG and CONTAINER kinds still count per block.
 */
public final class SignalScoring {
    private static final int WEAK_TRACE_CAP = 4;

    private SignalScoring() {
    }

    /** The kind a signal counts as: the obsidian frame around a portal is the same trace as the portal. */
    static SignalKind scoringKind(SignalKind kind) {
        return kind == SignalKind.OBSIDIAN_FRAME ? SignalKind.OPEN_PORTAL : kind;
    }

    /**
     * @param signals          all signals of the cluster after structure filtering
     * @param keptContainers   positions of the containers that survived the structure filter
     * @param containerShapeOk result of hasStashShape on keptContainers (and STRONG positions)
     */
    public static Verdict evaluate(List<Signal> signals, List<BlockPos> keptContainers,
                                   boolean containerShapeOk, Sensitivity sensitivity, int thresholdSetting) {
        int score = 0;
        int weakScore = 0;
        int strong = 0;
        boolean trace = false;
        Set<SignalKind> kinds = EnumSet.noneOf(SignalKind.class);
        for (Signal s : signals) {
            SignalKind kind = scoringKind(s.kind());
            boolean firstOfKind = kinds.add(kind);
            switch (kind.category()) {
                case STRONG -> {
                    strong++;
                    score += kind.weight();
                }
                case TRACE -> {
                    trace = true;
                    if (firstOfKind) score += kind.weight();
                }
                case WEAK_TRACE -> {
                    if (firstOfKind) weakScore += kind.weight();
                }
                case CONTAINER -> score += kind.weight();
            }
        }
        score += Math.min(weakScore, WEAK_TRACE_CAP);

        // STRONG kinds count toward the container condition, like containers.
        int storageCount = keptContainers.size() + strong;
        boolean byContainers = storageCount >= sensitivity.containerThreshold(thresholdSetting)
                && containerShapeOk;
        if (byContainers) {
            return new Verdict(Tier.STASH, score, true);
        }
        // Score-stash needs at least one TRACE kind; STRONG alone never stashes by score.
        if (trace && score >= sensitivity.stashScore()) {
            return new Verdict(Tier.STASH, score, false);
        }
        if (!sensitivity.hints()) {
            return new Verdict(Tier.NONE, score, false);
        }
        // One kind alone (one portal, one wall of concrete) never makes a hint, even when it reaches the hint score.
        boolean hintByTrace = trace && kinds.size() >= 2 && score >= sensitivity.hintScore();
        if (strong > 0 || hintByTrace) {
            return new Verdict(Tier.HINT, score, false);
        }
        return new Verdict(Tier.NONE, score, false);
    }
}
