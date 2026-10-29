package com.stashhunter.stashhunter.utils;

import java.util.OptionalLong;
import java.util.Random;

/**
 * Java Edition slime chunks. Unlike Bedrock, they depend on the world seed: the formula below is
 * vanilla's WorldgenRandom.seedSlimeChunk(x, z, seed, 987234911L).nextInt(10) == 0, whose random
 * source is the same 48-bit LCG as java.util.Random.
 */
public final class SlimeChunks {
    private static final long SALT = 987234911L;

    private SlimeChunks() {}

    public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        long chunkSeed = seed
            + (long) (chunkX * chunkX * 4987142)
            + (long) (chunkX * 5947611)
            + (long) (chunkZ * chunkZ) * 4392871L
            + (long) (chunkZ * 389711)
            ^ SALT;
        return new Random(chunkSeed).nextInt(10) == 0;
    }

    /**
     * Parses a seed the way the "Create New World" screen does: a number is used as-is, any other
     * text is hashed. Returns empty for blank input.
     */
    public static OptionalLong parseSeed(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            return OptionalLong.of(trimmed.hashCode());
        }
    }
}
