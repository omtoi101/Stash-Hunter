package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.StashHunter;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stashes already reported on one server, per dimension. Persisted because stashes below the
 * notification threshold get no waypoint, so after a reconnect or module toggle nothing else would
 * stop them from being reported again. One line per stash: "dimension x y z".
 */
public final class ReportedStashes {
    private final Path file;
    private final Map<String, List<BlockPos>> byDimension = new HashMap<>();

    private ReportedStashes(Path file) {
        this.file = file;
    }

    public static ReportedStashes load(Path file) {
        ReportedStashes stashes = new ReportedStashes(file);
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return stashes;
        } catch (IOException e) {
            StashHunter.LOG.warn("Could not read reported stashes from {}", file, e);
            return stashes;
        }
        int skipped = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            if (!stashes.parseInto(line)) skipped++;
        }
        if (skipped > 0) StashHunter.LOG.warn("Skipped {} unreadable line(s) in {}", skipped, file);
        return stashes;
    }

    public boolean isNear(String dimension, BlockPos pos, int radius) {
        for (BlockPos reported : byDimension.getOrDefault(dimension, List.of())) {
            if (reported.closerThan(pos, radius)) return true;
        }
        return false;
    }

    public void add(String dimension, BlockPos pos) {
        byDimension.computeIfAbsent(dimension, d -> new ArrayList<>()).add(pos);
        try {
            AppendFiles.append(file, format(dimension, pos) + System.lineSeparator());
        } catch (IOException e) {
            StashHunter.LOG.warn("Could not save reported stash to {}", file, e);
        }
    }

    public void clear() {
        byDimension.clear();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            StashHunter.LOG.warn("Could not delete {}", file, e);
        }
    }

    static String format(String dimension, BlockPos pos) {
        return dimension + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private boolean parseInto(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 4) return false;
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            byDimension.computeIfAbsent(parts[0], d -> new ArrayList<>()).add(pos);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
