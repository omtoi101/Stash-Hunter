package com.stashhunter.stashhunter.signals;

/** Result of scoring one cluster. {@code byContainers} marks a stash via the container condition. */
public record Verdict(Tier tier, int score, boolean byContainers) {
}
