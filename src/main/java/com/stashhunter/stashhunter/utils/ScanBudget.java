package com.stashhunter.stashhunter.utils;

import java.util.function.LongSupplier;

/** Per-tick time budget for incremental scans, plus the cheap section pre-check used by the tunnel scan. */
public final class ScanBudget {
    private final long budgetNanos;
    private final LongSupplier clock;
    private long startedAt;

    public ScanBudget(long budgetNanos, LongSupplier clock) {
        this.budgetNanos = budgetNanos;
        this.clock = clock;
    }

    public void start() {
        startedAt = clock.getAsLong();
    }

    public boolean exhausted() {
        return clock.getAsLong() - startedAt >= budgetNanos;
    }

    /**
     * Whether no hole, tunnel or stair can start in section {@code i}: it holds nothing passable, or it and the
     * section below are all air (then the floor is air and every in-chunk neighbour is air too).
     */
    public static boolean skipSection(boolean[] onlyAir, boolean[] hasPassable, int i) {
        if (!hasPassable[i]) return true;
        return onlyAir[i] && i > 0 && onlyAir[i - 1];
    }
}
