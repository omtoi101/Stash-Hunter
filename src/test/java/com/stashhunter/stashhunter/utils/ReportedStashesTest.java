package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.signals.Tier;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReportedStashesTest {
    @TempDir
    Path dir;

    @Test
    void survivesReload() {
        Path file = dir.resolve("server.txt");
        ReportedStashes stashes = ReportedStashes.load(file);
        stashes.add("Overworld", new BlockPos(100, 64, -200), Tier.STASH);

        ReportedStashes reloaded = ReportedStashes.load(file);
        assertEquals(Tier.STASH, reloaded.highestTierNear("Overworld", new BlockPos(110, 70, -195), 48));
        assertEquals(Tier.NONE, reloaded.highestTierNear("Overworld", new BlockPos(200, 64, -200), 48));
        assertEquals(Tier.NONE, reloaded.highestTierNear("Nether", new BlockPos(100, 64, -200), 48), "dimensions are separate");
    }

    @Test
    void missingFileIsEmpty() {
        assertEquals(Tier.NONE, ReportedStashes.load(dir.resolve("none.txt")).highestTierNear("Overworld", BlockPos.ZERO, 1000));
    }

    @Test
    void skipsBrokenLines() throws IOException {
        Path file = dir.resolve("server.txt");
        Files.writeString(file, "garbage\nOverworld 1 2\nOverworld x 2 3\nOverworld 1 2 3 BOGUS\n\nEnd 5 60 5 HINT\n");
        ReportedStashes stashes = ReportedStashes.load(file);
        assertEquals(Tier.HINT, stashes.highestTierNear("End", new BlockPos(5, 60, 5), 1));
        assertEquals(Tier.NONE, stashes.highestTierNear("Overworld", new BlockPos(1, 2, 3), 1), "unknown tier line is unreadable");
    }

    @Test
    void clearDeletesTheFile() {
        Path file = dir.resolve("server.txt");
        ReportedStashes stashes = ReportedStashes.load(file);
        stashes.add("Overworld", BlockPos.ZERO, Tier.STASH);
        stashes.clear();
        assertEquals(Tier.NONE, stashes.highestTierNear("Overworld", BlockPos.ZERO, 10));
        assertFalse(Files.exists(file));
    }

    @Test
    void legacyLinesCountAsStash() throws IOException {
        Path file = dir.resolve("server.txt");
        Files.writeString(file, "OVERWORLD 1 64 1\n");
        assertEquals(Tier.STASH, ReportedStashes.load(file).highestTierNear("OVERWORLD", new BlockPos(1, 64, 1), 1));
    }

    @Test
    void hintIsStoredWithItsTier() {
        Path file = dir.resolve("server.txt");
        ReportedStashes.load(file).add("Overworld", new BlockPos(10, 20, 30), Tier.HINT);
        assertEquals(Tier.HINT, ReportedStashes.load(file).highestTierNear("Overworld", new BlockPos(10, 20, 30), 1));
    }

    @Test
    void highestTierWins() {
        Path file = dir.resolve("server.txt");
        ReportedStashes stashes = ReportedStashes.load(file);
        stashes.add("Overworld", new BlockPos(0, 64, 0), Tier.HINT);
        stashes.add("Overworld", new BlockPos(5, 64, 0), Tier.STASH);
        assertEquals(Tier.STASH, stashes.highestTierNear("Overworld", new BlockPos(2, 64, 0), 48));
    }

    @Test
    void nothingNearIsNone() {
        Path file = dir.resolve("server.txt");
        ReportedStashes stashes = ReportedStashes.load(file);
        stashes.add("Overworld", new BlockPos(0, 64, 0), Tier.STASH);
        assertEquals(Tier.NONE, stashes.highestTierNear("Overworld", new BlockPos(500, 64, 500), 48));
    }
}
