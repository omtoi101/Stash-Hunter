package com.stashhunter.stashhunter.signals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScanDiagnosticsTest {

    @Test
    void periodSummaryCountsRejectionsByReason() {
        ScanDiagnostics d = new ScanDiagnostics();
        d.startPeriod("p1");
        d.rejected(Rejection.STRUCTURE_SHAPE);
        d.rejected(Rejection.STRUCTURE_SHAPE);
        d.rejected(Rejection.LOW_SCORE);
        var lines = d.endPeriod();
        assertTrue(lines.stream().anyMatch(l -> l.contains("structure (shape): 2")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("low score: 1")), lines.toString());
    }

    @Test
    void endPeriodResetsButSessionKeepsTotals() {
        ScanDiagnostics d = new ScanDiagnostics();
        d.startPeriod("p1");
        d.chunkScanned();
        d.rejected(Rejection.ALREADY_REPORTED);
        d.endPeriod();

        d.startPeriod("p2");
        d.chunkScanned();
        var lines = d.endPeriod();

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Scanned 1 chunks")), lines.toString());
        assertFalse(lines.stream().anyMatch(l -> l.contains("already reported")), lines.toString());
        assertTrue(d.current().stream().anyMatch(l -> l.startsWith("Scanned 2 chunks")), d.current().toString());
        assertTrue(d.current().stream().anyMatch(l -> l.contains("already reported: 1")), d.current().toString());
    }

    @Test
    void currentShowsPeriodAndSession() {
        ScanDiagnostics d = new ScanDiagnostics();
        d.startPeriod("running");
        d.chunkScanned();
        d.reported(Tier.STASH);
        var lines = d.current();
        assertTrue(lines.get(0).contains("running"), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Scanned 1 chunks")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("Session")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("stashes: 1")), lines.toString());
    }

    @Test
    void resetSessionClearsTotals() {
        ScanDiagnostics d = new ScanDiagnostics();
        d.startPeriod("p");
        d.chunkScanned();
        d.endPeriod();
        d.resetSession();
        assertTrue(d.current().stream().anyMatch(l -> l.startsWith("Scanned 0 chunks")), d.current().toString());
    }

    @Test
    void neverLoadedAndViewDistanceAppearInSummary() {
        ScanDiagnostics d = new ScanDiagnostics();
        d.startPeriod("p");
        d.chunkScanned();
        d.chunksNeverLoaded(12);
        d.viewDistance(11);
        var lines = d.endPeriod();
        assertTrue(lines.stream().anyMatch(l -> l.equals(
                "Scanned 1 chunks, 12 never loaded (server view distance ~176 blocks)")), lines.toString());
    }

    @Test
    void signalsPerCategoryAreCounted() {
        ScanDiagnostics d = new ScanDiagnostics();
        d.startPeriod("p");
        d.signals(SignalCategory.STRONG, 3);
        d.signals(SignalCategory.TRACE, 2);
        var lines = d.endPeriod();
        assertTrue(lines.stream().anyMatch(l -> l.contains("strong 3") && l.contains("trace 2")), lines.toString());
    }
}
