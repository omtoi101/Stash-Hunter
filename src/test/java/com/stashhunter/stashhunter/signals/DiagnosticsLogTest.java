package com.stashhunter.stashhunter.signals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsLogTest {
    @TempDir
    Path dir;

    @Test
    void writesOneLinePerCandidate() throws IOException {
        Path file = dir.resolve("diag.log");
        DiagnosticsLog log = new DiagnosticsLog(file, 5_000_000L);
        log.candidate("minecraft:overworld", new BlockPos(1, 2, 3),
                List.of(new Signal(new BlockPos(1, 2, 3), SignalKind.CHEST),
                        new Signal(new BlockPos(1, 2, 4), SignalKind.CHEST),
                        new Signal(new BlockPos(1, 2, 5), SignalKind.SHULKER_BOX)),
                Rejection.LOW_SCORE);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.contains(" minecraft:overworld 1 2 3 LOW_SCORE "), line);
        assertTrue(line.endsWith("chest x2, shulker box x1"), line);
    }

    @Test
    void logRotatesWhenTooBig() throws IOException {
        Path file = dir.resolve("diag.log");
        Path old = dir.resolve("diag.log.old");
        DiagnosticsLog log = new DiagnosticsLog(file, 200L);
        for (int i = 0; i < 10; i++)
            log.candidate("minecraft:overworld", new BlockPos(i, 64, i),
                    List.of(new Signal(new BlockPos(i, 64, i), SignalKind.BARREL)), Rejection.STRUCTURE_SHAPE);
        assertTrue(Files.exists(old));
        assertTrue(Files.size(file) <= 200, "size " + Files.size(file));
        assertTrue(Files.size(old) <= 200, "old size " + Files.size(old));
    }

    @Test
    void ioErrorsAreSwallowed() throws IOException {
        // A directory where the log file should be makes the append fail.
        Path file = Files.createDirectories(dir.resolve("blocked"));
        DiagnosticsLog log = new DiagnosticsLog(file, 5_000_000L);
        assertDoesNotThrow(() -> log.candidate("minecraft:overworld", new BlockPos(0, 0, 0),
                List.of(), Rejection.ALREADY_REPORTED));
    }
}
