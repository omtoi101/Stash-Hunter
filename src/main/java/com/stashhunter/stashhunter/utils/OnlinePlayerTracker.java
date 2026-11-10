package com.stashhunter.stashhunter.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Diffs successive tab-list snapshots into joins and leaves of other players. The first snapshot
 * after construction or {@link #reset()} only sets the baseline, so the tab list filling up on
 * server join is not reported as a wave of joins.
 */
public final class OnlinePlayerTracker {
    public record Change(List<String> joined, List<String> left, int previousCount, int count) {
        public boolean isEmpty() {
            return joined.isEmpty() && left.isEmpty();
        }
    }

    private Map<UUID, String> known;

    /** Forget everything; the next {@link #update} becomes the new baseline. */
    public void reset() {
        known = null;
    }

    public boolean hasBaseline() {
        return known != null;
    }

    /** Number of other players in the last snapshot, or -1 without a baseline. */
    public int count() {
        return known == null ? -1 : known.size();
    }

    /**
     * @param snapshot UUID to name of everyone in the tab list
     * @param self     own UUID, never reported (may be null)
     * @param silent   absorb the snapshot as baseline without reporting (startup grace)
     */
    public Change update(Map<UUID, String> snapshot, UUID self, boolean silent) {
        Map<UUID, String> current = new HashMap<>(snapshot);
        if (self != null) current.remove(self);

        if (known == null || silent) {
            int previous = known == null ? current.size() : known.size();
            known = current;
            return new Change(List.of(), List.of(), previous, current.size());
        }

        List<String> joined = new ArrayList<>();
        List<String> left = new ArrayList<>();
        for (Map.Entry<UUID, String> e : current.entrySet()) {
            if (!known.containsKey(e.getKey())) joined.add(e.getValue());
        }
        for (Map.Entry<UUID, String> e : known.entrySet()) {
            if (!current.containsKey(e.getKey())) left.add(e.getValue());
        }
        // Sorted so a batch reads stably in chat/Discord regardless of hash order.
        joined.sort(String.CASE_INSENSITIVE_ORDER);
        left.sort(String.CASE_INSENSITIVE_ORDER);

        int previous = known.size();
        known = current;
        return new Change(joined, left, previous, current.size());
    }

    /** "a, b, c and 2 more" with at most {@code max} names. */
    public static String formatNames(List<String> names, int max) {
        if (names.size() <= max) return String.join(", ", names);
        return String.join(", ", names.subList(0, max)) + " and " + (names.size() - max) + " more";
    }
}
