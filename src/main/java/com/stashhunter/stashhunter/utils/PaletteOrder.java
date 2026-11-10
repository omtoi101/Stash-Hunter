package com.stashhunter.stashhunter.utils;

/**
 * New-chunk detection from the order of a chunk section's block palette (PaletteExploit).
 *
 * <p>World generation starts every section as pure air and adds block states to a linear palette
 * (up to 16 states, 4 bits per block) in the order they are placed, so entry 0 stays plain air even
 * when no air is left. Saving a chunk rebuilds the palette in the order the states first occur in
 * the section (index order y, z, x), and so does growing past 16 states; entry 0 is then the block
 * at the section origin. The server sends the palette as it is in memory, so a chunk generated just
 * now still shows the generation order, a chunk loaded from disk the saved one.
 *
 * <p>Servers that rewrite palettes before sending (e.g. some anti-xray implementations) can break
 * this.
 */
public final class PaletteOrder {
    /** Storage bits of a linear block palette; 1-4 bit palettes are all stored with 4 bits. */
    public static final int LINEAR_BITS = 4;

    public enum Vote { FRESH, SAVED, NONE }

    public enum Verdict { NEW, OLD, UNKNOWN }

    private PaletteOrder() {
    }

    /**
     * @param bits        storage bits of the section's block container (0 single value, 4 linear,
     *                    5-8 hash map, more for the global palette)
     * @param firstIsAir  palette entry 0 is plain air (only meaningful for a linear palette)
     * @param originIsAir the block at the section origin (0, 0, 0) is plain air
     */
    public static Vote vote(int bits, boolean firstIsAir, boolean originIsAir) {
        // Both orders put air first then.
        if (originIsAir) return Vote.NONE;
        // A single non-air value: generation always leaves air in the palette, only saving drops it.
        if (bits == 0) return Vote.SAVED;
        // Hash map and global palettes are rebuilt during generation too.
        if (bits != LINEAR_BITS) return Vote.NONE;
        return firstIsAir ? Vote.FRESH : Vote.SAVED;
    }

    /**
     * A saved order can't come out of generation, so one SAVED section makes the chunk old. FRESH
     * sections in an old chunk only appear where the section origin was air when it was saved and
     * was filled later.
     */
    public static Verdict classify(int freshSections, int savedSections) {
        if (savedSections > 0) return Verdict.OLD;
        if (freshSections > 0) return Verdict.NEW;
        return Verdict.UNKNOWN;
    }
}
