package com.stashhunter.stashhunter.signals;

import com.stashhunter.stashhunter.utils.AppendFiles;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only log of rejected candidates. When a new line would push the file past
 * maxBytes, the file moves to the same name with ".old" appended and a fresh file is started.
 */
public final class DiagnosticsLog {
    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsLog.class);

    private final Path file;
    private final Path old;
    private final long maxBytes;

    public DiagnosticsLog(Path file, long maxBytes) {
        this.file = file;
        this.old = file.resolveSibling(file.getFileName() + ".old");
        this.maxBytes = maxBytes;
    }

    /** Appends one line for a candidate. IO errors are logged and never thrown. */
    public synchronized void candidate(String dimension, BlockPos center, List<Signal> signals, Rejection reason) {
        String line = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) + " " + dimension + " "
                + center.getX() + " " + center.getY() + " " + center.getZ() + " " + reason + " "
                + describe(signals) + System.lineSeparator();
        try {
            rotateIfNeeded(line.getBytes(StandardCharsets.UTF_8).length);
            AppendFiles.append(file, line);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not write diagnostics log {}", file, e);
        }
    }

    private void rotateIfNeeded(int newBytes) throws IOException {
        if (!Files.exists(file)) return;
        if (Files.size(file) + newBytes <= maxBytes) return;
        Files.move(file, old, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String describe(List<Signal> signals) {
        if (signals.isEmpty()) return "-";
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Signal s : signals) counts.merge(s.kind().label(), 1, Integer::sum);
        StringBuilder sb = new StringBuilder();
        counts.forEach((label, n) -> {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(label).append(" x").append(n);
        });
        return sb.toString();
    }
}
