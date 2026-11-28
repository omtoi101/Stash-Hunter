package com.stashhunter.stashhunter.signals;

public enum Sensitivity {
    CLASSIC(Integer.MAX_VALUE, Integer.MAX_VALUE, false),
    NORMAL(6, 25, true),
    SENSITIVE(4, 15, true);

    private final int hintScore;
    private final int stashScore;
    private final boolean hints;

    Sensitivity(int hintScore, int stashScore, boolean hints) {
        this.hintScore = hintScore;
        this.stashScore = stashScore;
        this.hints = hints;
    }

    /** Score needed for a hint. Unreachable in CLASSIC, which has no hints. */
    public int hintScore() {
        return hintScore;
    }

    /** Score needed for a stash. Unreachable in CLASSIC. */
    public int stashScore() {
        return stashScore;
    }

    /** Container threshold: CLASSIC uses the setting directly, the others cap it. */
    public int containerThreshold(int setting) {
        return switch (this) {
            case CLASSIC -> setting;
            case NORMAL -> Math.min(setting, 6);
            case SENSITIVE -> Math.min(setting, 4);
        };
    }

    public boolean hints() {
        return hints;
    }

    /** Lower case: Meteor's EnumSetting shows, saves and parses enum values through toString. */
    @Override
    public String toString() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
