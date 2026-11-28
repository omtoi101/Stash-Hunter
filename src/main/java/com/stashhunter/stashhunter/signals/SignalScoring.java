package com.stashhunter.stashhunter.signals;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Scores one cluster of signals into NONE, HINT or STASH. Rules: see globals "Gewichte und Schwellen". */
public final class SignalScoring {
    private static final int WEAK_TRACE_CAP = 4;

    private SignalScoring() {
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
        int trace = 0;
        for (Signal s : signals) {
            SignalKind kind = s.kind();
            switch (kind.category()) {
                case STRONG -> {
                    strong++;
                    score += kind.weight();
                }
                case TRACE -> {
                    trace++;
                    score += kind.weight();
                }
                case WEAK_TRACE -> weakScore += kind.weight();
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
        if (trace > 0 && score >= sensitivity.stashScore()) {
            return new Verdict(Tier.STASH, score, false);
        }
        if (!sensitivity.hints()) {
            return new Verdict(Tier.NONE, score, false);
        }
        // A single trace never makes a hint, even when its weight reaches the hint score.
        boolean hintByTrace = trace > 0 && signals.size() >= 2 && score >= sensitivity.hintScore();
        if (strong > 0 || hintByTrace) {
            return new Verdict(Tier.HINT, score, false);
        }
        return new Verdict(Tier.NONE, score, false);
    }
}
