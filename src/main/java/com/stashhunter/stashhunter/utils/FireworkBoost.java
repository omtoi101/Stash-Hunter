package com.stashhunter.stashhunter.utils;

/**
 * Decides when Auto-Firework fires a rocket. Speed is measured along the look direction, because a
 * fast vertical fall or a sideways drift is no forward progress and a rocket boosts along the look.
 */
public final class FireworkBoost {
    public static final double TICKS_PER_SECOND = 20.0;

    private FireworkBoost() {
    }

    /**
     * Velocity projected onto the look direction, in blocks per second.
     * Velocity is in blocks per tick (Minecraft's delta movement); the look vector is normalised here.
     */
    public static double forwardSpeed(double vx, double vy, double vz, double lx, double ly, double lz) {
        double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (len == 0) return 0;
        return (vx * lx + vy * ly + vz * lz) / len * TICKS_PER_SECOND;
    }

    /** Whether this tick is a check tick: at most one speed check every {@code checkCooldown} ticks. */
    public static boolean shouldCheck(int ticksSinceCheck, int checkCooldown) {
        return ticksSinceCheck >= checkCooldown;
    }

    /**
     * @param fallFlying      rockets only boost while gliding, so anything else would waste them
     * @param forwardSpeedBps forward speed in blocks per second, see {@link #forwardSpeed}
     * @param minSpeedBps     fire below this speed
     * @param ticksSinceUse   ticks since the last rocket; {@code Integer.MAX_VALUE} if none yet
     * @param useDelayTicks   minimum ticks between two rockets, so a still-burning one isn't stacked
     */
    public static boolean shouldBoost(boolean fallFlying, double forwardSpeedBps, double minSpeedBps,
                                      int ticksSinceUse, int useDelayTicks) {
        if (!fallFlying) return false;
        if (ticksSinceUse < useDelayTicks) return false;
        return forwardSpeedBps < minSpeedBps;
    }

    /**
     * Only plain rockets are safe to use while gliding: each firework star explodes on the user and deals damage.
     *
     * @param explosionCount number of firework stars on the rocket; 0 if it has no fireworks component
     */
    public static boolean isSafeRocket(int explosionCount) {
        return explosionCount == 0;
    }
}
