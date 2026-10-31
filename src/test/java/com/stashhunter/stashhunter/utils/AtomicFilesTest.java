package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AtomicFilesTest {
    @TempDir
    Path dir;

    @Test
    void replacesExistingFile() throws IOException {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, "old");
        AtomicFiles.write(file, out -> out.write("new".getBytes(StandardCharsets.UTF_8)));
        assertEquals("new", Files.readString(file));
        assertNoTempFiles();
    }

    @Test
    void createsMissingFolder() throws IOException {
        Path file = dir.resolve("sub/trips.json");
        AtomicFiles.write(file, out -> out.write('x'));
        assertEquals("x", Files.readString(file));
    }

    @Test
    void failedWriteKeepsOldFile() throws IOException {
        Path file = dir.resolve("trips.json");
        Files.writeString(file, "intact");
        assertThrows(IOException.class, () -> AtomicFiles.write(file, out -> {
            out.write("half".getBytes(StandardCharsets.UTF_8));
            throw new IOException("crash mid-write");
        }));
        assertEquals("intact", Files.readString(file));
        assertNoTempFiles();
    }

    private void assertNoTempFiles() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".tmp")), "temp file left behind");
        }
    }
}
