package com.stashhunter.stashhunter.utils;

import net.minecraft.world.level.ChunkPos;

/** One chunk per line, written as "[x, z]" - the format NewerNewChunks' data files have always used. */
public final class ChunkDataFormat {
    private ChunkDataFormat() {}

    // Written explicitly instead of via ChunkPos.toString(), whose format is not a stable contract.
    public static String format(ChunkPos pos) {
        return "[" + pos.x() + ", " + pos.z() + "]";
    }

    /** Returns null for anything that isn't a "[x, z]" line. */
    public static ChunkPos parse(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null;
        String[] parts = trimmed.substring(1, trimmed.length() - 1).split(",");
        if (parts.length != 2) return null;
        try {
            return new ChunkPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
