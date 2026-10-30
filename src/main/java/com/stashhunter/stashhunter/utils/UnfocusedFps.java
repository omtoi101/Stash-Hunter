package com.stashhunter.stashhunter.utils;

/**
 * Framerate cap while the game window is in the background. The user's own limit always wins when
 * it is lower, so the module can only ever reduce the framerate, never raise it.
 */
public final class UnfocusedFps {
    /**
     * Minecraft runs at most 10 client ticks per rendered frame and drops the rest, so a cap below
     * ~3 FPS slows the game below 20 TPS. 5 keeps it at 4 ticks per frame with headroom for jitter.
     */
    public static final int MIN_LIMIT = 5;

    private UnfocusedFps() {
    }

    /**
     * @param focused        whether the game window has focus
     * @param userLimit      the user's framerate option (vanilla treats 260 as unlimited)
     * @param unfocusedLimit the cap to apply while unfocused
     * @return the framerate limit to hand to the game
     */
    public static int effectiveLimit(boolean focused, int userLimit, int unfocusedLimit) {
        if (focused) return userLimit;
        return Math.min(userLimit, Math.max(MIN_LIMIT, unfocusedLimit));
    }
}
