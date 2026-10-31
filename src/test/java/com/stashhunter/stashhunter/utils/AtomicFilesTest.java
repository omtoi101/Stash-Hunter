package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
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

    @Test
    void replacesFileThatIsHeldOpen() throws IOException {
        // On Windows the rename fails while another handle without delete sharing is open
        // (virus scanner, cloud sync); the save must still land.
        Path file = dir.resolve("config.properties");
        Files.writeString(file, "old");
        try (FileInputStream reader = new FileInputStream(file.toFile())) {
            AtomicFiles.write(file, out -> out.write("new".getBytes(StandardCharsets.UTF_8)));
            assertEquals("new", Files.readString(file));
        }
        assertNoTempFiles();
    }

    @Test
    void deletesLeftoverTempFilesOfTheTargetOnly() throws IOException {
        Path file = dir.resolve("stash-hunter.properties");
        Files.writeString(file, "keep");
        Files.writeString(dir.resolve("stash-hunter.properties8123456789.tmp"), "leftover");
        Files.writeString(dir.resolve("stash-hunter.properties.tmp"), "not ours");
        Files.writeString(dir.resolve("stashhunter_trips.json42.tmp"), "other target");

        AtomicFiles.deleteLeftovers(file);

        assertEquals("keep", Files.readString(file));
        assertFalse(Files.exists(dir.resolve("stash-hunter.properties8123456789.tmp")));
        assertTrue(Files.exists(dir.resolve("stash-hunter.properties.tmp")));
        assertTrue(Files.exists(dir.resolve("stashhunter_trips.json42.tmp")));
    }

    @Test
    void deleteLeftoversWithoutFolderDoesNothing() {
        assertDoesNotThrow(() -> AtomicFiles.deleteLeftovers(dir.resolve("missing/trips.json")));
    }

    private void assertNoTempFiles() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".tmp")), "temp file left behind");
        }
    }
}
