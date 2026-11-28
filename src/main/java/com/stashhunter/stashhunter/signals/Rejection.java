package com.stashhunter.stashhunter.signals;

/** Why containers were dropped from a candidate, or why a candidate was not reported. */
public enum Rejection {
    LOW_SCORE,
    STRUCTURE_SHAPE,
    STRUCTURE_DUNGEON,
    STRUCTURE_SPAWNER,
    // Only used by the structure locator.
    STRUCTURE_SEED,
    ALREADY_REPORTED
}
