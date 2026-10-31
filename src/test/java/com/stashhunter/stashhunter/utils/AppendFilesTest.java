package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppendFilesTest {
    @TempDir
    Path dir;

    @Test
    void createsFileAndFolder() throws IOException {
        Path file = dir.resolve("sub/data.txt");
        AppendFiles.append(file, "a\n");
        assertEquals("a\n", Files.readString(file));
    }

    @Test
    void appendsAfterCompleteLines() throws IOException {
        Path file = dir.resolve("data.txt");
        Files.writeString(file, "a\n");
        AppendFiles.append(file, "b\n");
        assertEquals("a\nb\n", Files.readString(file));
    }

    @Test
    void unfinishedLastLineDoesNotSwallowTheNextEntry() throws IOException {
        // As after a power loss mid-append: "Overworld 100 6" lost the rest of its line.
        Path file = dir.resolve("data.txt");
        Files.writeString(file, "End 1 2 3\nOverworld 100 6");
        AppendFiles.append(file, "Overworld 5 60 5\n");
        assertEquals(List.of("End 1 2 3", "Overworld 100 6", "Overworld 5 60 5"), Files.readAllLines(file));
    }

    @Test
    void lineCutBetweenCarriageReturnAndNewlineAddsNoEmptyEntry() throws IOException {
        Path file = dir.resolve("data.txt");
        Files.writeString(file, "a\r");
        AppendFiles.append(file, "b\n");
        assertEquals(List.of("a", "b"), Files.readAllLines(file));
    }
}
