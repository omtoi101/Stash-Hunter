package com.stashhunter.stashhunter.utils;

/**
 * Decides whether AutoLogout should disconnect. Kept free of Minecraft types so the trigger
 * rules can be unit-tested; the module feeds in the current health and nearest-player distance.
 */
public final class DangerCheck {
    /** Passed as nearest distance when no relevant player is loaded. */
    public static final double NO_PLAYER = Double.POSITIVE_INFINITY;

    private DangerCheck() {
    }

    /**
     * @param playerCheck     whether the player-proximity trigger is enabled
     * @param nearestDistance distance to the nearest relevant (non-friend) player, or {@link #NO_PLAYER}
     * @param radius          trigger radius in blocks (inclusive)
     * @param healthCheck     whether the low-health trigger is enabled
     * @param health          current health in half-hearts
     * @param minHealth       trigger when health drops strictly below this
     * @return a human-readable reason, or {@code null} when it is safe to stay
     */
    public static String reason(boolean playerCheck, double nearestDistance, int radius,
                                boolean healthCheck, float health, int minHealth) {
        // Dead (health 0) is not a trigger: logging out on the death screen saves nothing.
        if (healthCheck && health > 0 && health < minHealth) {
            return "health " + formatHealth(health) + " below " + minHealth;
        }
        if (playerCheck && nearestDistance <= radius) {
            return "player within " + (int) Math.ceil(nearestDistance) + " blocks";
        }
        return null;
    }

    private static String formatHealth(float health) {
        return health == Math.floor(health) ? Integer.toString((int) health) : String.format(java.util.Locale.ROOT, "%.1f", health);
    }
}
