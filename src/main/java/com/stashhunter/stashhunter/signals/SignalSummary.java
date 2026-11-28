package com.stashhunter.stashhunter.signals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Short English texts listing the kinds in a set of signals, e.g. "2 shulker boxes, 1 written sign". */
public final class SignalSummary {
    private SignalSummary() {
    }

    /**
     * The {@code max} most frequent kinds, most frequent first; ties go to the heavier kind, then to the
     * kind declared first. Empty string for no signals.
     */
    public static String top(Collection<Signal> signals, int max) {
        return describe(signals, kind -> true, max);
    }

    /** All TRACE and WEAK_TRACE kinds, most frequent first; empty string if there are none. */
    public static String traces(Collection<Signal> signals) {
        return describe(signals, kind -> kind.category() == SignalCategory.TRACE
                || kind.category() == SignalCategory.WEAK_TRACE, Integer.MAX_VALUE);
    }

    private static String describe(Collection<Signal> signals, Predicate<SignalKind> include, int max) {
        Map<SignalKind, Integer> counts = new EnumMap<>(SignalKind.class);
        for (Signal s : signals) {
            if (include.test(s.kind())) counts.merge(s.kind(), 1, Integer::sum);
        }
        List<Map.Entry<SignalKind, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<SignalKind, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Comparator.<Map.Entry<SignalKind, Integer>>comparingInt(e -> e.getKey().weight()).reversed())
                .thenComparingInt(e -> e.getKey().ordinal()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size() && i < max; i++) {
            if (i > 0) sb.append(", ");
            int n = entries.get(i).getValue();
            String label = entries.get(i).getKey().label();
            sb.append(n).append(' ').append(n == 1 ? label : plural(label));
        }
        return sb.toString();
    }

    static String plural(String label) {
        if (label.endsWith("s") || label.endsWith("x") || label.endsWith("ch") || label.endsWith("sh")) {
            return label + "es";
        }
        if (label.endsWith("y") && label.length() > 1 && "aeiou".indexOf(label.charAt(label.length() - 2)) < 0) {
            return label.substring(0, label.length() - 1) + "ies";
        }
        return label + "s";
    }
}
