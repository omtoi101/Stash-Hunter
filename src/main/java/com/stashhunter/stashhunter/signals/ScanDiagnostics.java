package com.stashhunter.stashhunter.signals;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Counters for one scan period (for example one flight) plus a running session total.
 * Produces plain English chat lines for the diag output.
 */
public final class ScanDiagnostics {
    private static final String[] REASON_TEXT = new String[Rejection.values().length];
    private static final String[] CATEGORY_TEXT = new String[SignalCategory.values().length];

    static {
        REASON_TEXT[Rejection.LOW_SCORE.ordinal()] = "low score";
        REASON_TEXT[Rejection.STRUCTURE_SHAPE.ordinal()] = "structure (shape)";
        REASON_TEXT[Rejection.STRUCTURE_DUNGEON.ordinal()] = "structure (dungeon)";
        REASON_TEXT[Rejection.STRUCTURE_SPAWNER.ordinal()] = "structure (spawner)";
        REASON_TEXT[Rejection.STRUCTURE_SEED.ordinal()] = "structure (seed)";
        REASON_TEXT[Rejection.ALREADY_REPORTED.ordinal()] = "already reported";
        CATEGORY_TEXT[SignalCategory.STRONG.ordinal()] = "strong";
        CATEGORY_TEXT[SignalCategory.CONTAINER.ordinal()] = "container";
        CATEGORY_TEXT[SignalCategory.TRACE.ordinal()] = "trace";
        CATEGORY_TEXT[SignalCategory.WEAK_TRACE.ordinal()] = "weak trace";
    }

    private static final class Counters {
        int chunks;
        int neverLoaded;
        int viewDistance;
        final int[] signals = new int[SignalCategory.values().length];
        final Map<Rejection, Integer> rejections = new EnumMap<>(Rejection.class);
        int hints;
        int stashes;

        void addAll(Counters o) {
            chunks += o.chunks;
            neverLoaded += o.neverLoaded;
            if (o.viewDistance > 0) viewDistance = o.viewDistance;
            for (int i = 0; i < signals.length; i++) signals[i] += o.signals[i];
            o.rejections.forEach((r, n) -> rejections.merge(r, n, Integer::sum));
            hints += o.hints;
            stashes += o.stashes;
        }

        List<String> lines() {
            List<String> out = new ArrayList<>();
            String view = viewDistance > 0
                    ? "server view distance ~" + viewDistance * 16 + " blocks"
                    : "server view distance unknown";
            out.add("Scanned " + chunks + " chunks, " + neverLoaded + " never loaded (" + view + ")");
            StringBuilder cats = new StringBuilder("Signals per category:");
            for (SignalCategory c : SignalCategory.values()) {
                cats.append(c.ordinal() == 0 ? " " : ", ")
                        .append(CATEGORY_TEXT[c.ordinal()]).append(' ').append(signals[c.ordinal()]);
            }
            out.add(cats.toString());
            for (Rejection r : Rejection.values()) {
                Integer n = rejections.get(r);
                if (n != null && n > 0) out.add(REASON_TEXT[r.ordinal()] + ": " + n);
            }
            out.add("Hints: " + hints + ", stashes: " + stashes);
            return out;
        }
    }

    private String label = "-";
    private Counters period = new Counters();
    private Counters session = new Counters();

    public void startPeriod(String label) {
        this.label = label;
        this.period = new Counters();
    }

    public void chunkScanned() {
        period.chunks++;
    }

    public void chunksNeverLoaded(int n) {
        period.neverLoaded += n;
    }

    public void viewDistance(int chunks) {
        period.viewDistance = chunks;
    }

    public void signals(SignalCategory c, int n) {
        period.signals[c.ordinal()] += n;
    }

    public void rejected(Rejection r) {
        period.rejections.merge(r, 1, Integer::sum);
    }

    public void reported(Tier t) {
        if (t == Tier.HINT) period.hints++;
        else if (t == Tier.STASH) period.stashes++;
    }

    /** Returns the period's chat lines, resets the period and adds it to the session total. */
    public List<String> endPeriod() {
        List<String> out = new ArrayList<>();
        out.add("Period " + label);
        out.addAll(period.lines());
        session.addAll(period);
        period = new Counters();
        return out;
    }

    /** Running period plus session total, for the diag command. */
    public List<String> current() {
        List<String> out = new ArrayList<>();
        out.add("Period " + label);
        out.addAll(period.lines());
        out.add("Session");
        out.addAll(session.lines());
        return out;
    }

    public void resetSession() {
        session = new Counters();
    }
}
