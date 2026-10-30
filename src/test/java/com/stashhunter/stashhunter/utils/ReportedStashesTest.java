package com.stashhunter.stashhunter.utils;

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
        stashes.add("Overworld", new BlockPos(100, 64, -200));

        ReportedStashes reloaded = ReportedStashes.load(file);
        assertTrue(reloaded.isNear("Overworld", new BlockPos(110, 70, -195), 48));
        assertFalse(reloaded.isNear("Overworld", new BlockPos(200, 64, -200), 48));
        assertFalse(reloaded.isNear("Nether", new BlockPos(100, 64, -200), 48), "dimensions are separate");
    }

    @Test
    void missingFileIsEmpty() {
        assertFalse(ReportedStashes.load(dir.resolve("none.txt")).isNear("Overworld", BlockPos.ZERO, 1000));
    }

    @Test
    void skipsBrokenLines() throws IOException {
        Path file = dir.resolve("server.txt");
        Files.writeString(file, "garbage\nOverworld 1 2\nOverworld x 2 3\n\nEnd 5 60 5\n");
        ReportedStashes stashes = ReportedStashes.load(file);
        assertTrue(stashes.isNear("End", new BlockPos(5, 60, 5), 1));
    }

    @Test
    void clearDeletesTheFile() {
        Path file = dir.resolve("server.txt");
        ReportedStashes stashes = ReportedStashes.load(file);
        stashes.add("Overworld", BlockPos.ZERO);
        stashes.clear();
        assertFalse(stashes.isNear("Overworld", BlockPos.ZERO, 10));
        assertFalse(Files.exists(file));
    }
}
