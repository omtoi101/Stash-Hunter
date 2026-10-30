package com.stashhunter.stashhunter.utils;

import java.util.OptionalLong;

/**
 * Java Edition slime chunks. Unlike Bedrock, they depend on the world seed: the formula below is
 * vanilla's WorldgenRandom.seedSlimeChunk(x, z, seed, 987234911L).nextInt(10) == 0, whose random
 * source is the same 48-bit LCG as java.util.Random.
 */
public final class SlimeChunks {
    private static final long SALT = 987234911L;
    // java.util.Random's LCG constants.
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private SlimeChunks() {}

    public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        long chunkSeed = seed
            + (long) (chunkX * chunkX * 4987142)
            + (long) (chunkX * 5947611)
            + (long) (chunkZ * chunkZ) * 4392871L
            + (long) (chunkZ * 389711)
            ^ SALT;
        return nextIntTen(chunkSeed) == 0;
    }

    // new Random(seed).nextInt(10) without the allocation: the overlay calls this up to 16641
    // times per chunk change. Same scramble, next(31) and rejection loop as java.util.Random.
    private static int nextIntTen(long seed) {
        long state = (seed ^ MULTIPLIER) & MASK;
        while (true) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            int bits = (int) (state >>> 17);
            int val = bits % 10;
            // Rejects the top partial range so all ten values stay equally likely.
            if (bits - val + 9 >= 0) return val;
        }
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
