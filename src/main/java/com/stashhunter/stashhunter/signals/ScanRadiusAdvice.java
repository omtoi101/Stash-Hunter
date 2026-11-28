package com.stashhunter.stashhunter.signals;

import java.util.Optional;

/** Suggests a larger scan radius when the server sends chunks well beyond it. */
public final class ScanRadiusAdvice {
    private ScanRadiusAdvice() {}

    /** Returns advice when the scan radius is more than 32 blocks below what the server sends. */
    public static Optional<String> advise(int scanRadius, int viewDistanceChunks) {
        int v = viewDistanceChunks * 16;
        if (scanRadius >= v - 32) return Optional.empty();
        return Optional.of("The server sends chunks about " + v + " blocks out; scan-radius " + scanRadius
                + " could be raised to " + (v - 16) + ".");
    }
}
