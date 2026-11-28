package com.stashhunter.stashhunter.signals;

import meteordevelopment.meteorclient.utils.world.Dimension;

/** Where bedrock occurs naturally; bedrock elsewhere was placed by a player. */
public final class NaturalBedrock {
    private NaturalBedrock() {}

    /** End: never natural by Y alone; callers exclude |x|,|z| <= 8 and use {@link #isEndExitPortal}. */
    public static boolean isNatural(Dimension dimension, int y) {
        return switch (dimension) {
            case Overworld -> y <= -60;
            case Nether -> y <= 4 || (y >= 123 && y <= 127);
            case End -> false;
        };
    }

    public static boolean isEndExitPortal(int x, int y, int z) {
        return Math.abs(x) <= 4 && Math.abs(z) <= 4 && y >= 50 && y <= 80;
    }
}
