package com.stashhunter.stashhunter.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a file through a temp file in the same folder that then replaces the target. A crash
 * mid-write leaves the old file intact instead of a truncated one.
 */
public final class AtomicFiles {
    private AtomicFiles() {}

    @FunctionalInterface
    public interface Content {
        void writeTo(OutputStream out) throws IOException;
    }

    public static void write(Path target, Content content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp.toFile())) {
                content.writeTo(out);
                out.flush();
                // Without this a power loss right after the move can leave an empty file behind.
                out.getFD().sync();
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
