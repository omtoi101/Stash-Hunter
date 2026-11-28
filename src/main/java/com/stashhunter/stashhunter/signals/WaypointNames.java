package com.stashhunter.stashhunter.signals;

/** Builds and classifies waypoint names. The stash format must stay identical to 26.2.2. */
public final class WaypointNames {

    static final String STASH_MARKER = " Blocks at ";
    static final String HINT_PREFIX = "Hint: ";

    private WaypointNames() {
    }

    public static String stash(int blocks, String coords) {
        return blocks + STASH_MARKER + coords;
    }

    public static String hint(String summary, String coords) {
        return HINT_PREFIX + summary + " at " + coords;
    }

    public static Tier tierOf(String waypointName) {
        if (waypointName == null) {
            return Tier.NONE;
        }
        if (waypointName.contains(STASH_MARKER)) {
            return Tier.STASH;
        }
        if (waypointName.startsWith(HINT_PREFIX)) {
            return Tier.HINT;
        }
        return Tier.NONE;
    }
}
