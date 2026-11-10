package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.StashHunter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a file through a temp file in the same folder that then replaces the target. A crash
 * mid-write leaves the old file intact instead of a truncated one.
 */
public final class AtomicFiles {
    private AtomicFiles() {}

    private static final String TEMP_SUFFIX = ".tmp";

    // Windows can't rename onto or from a file another process holds open without delete sharing
    // (virus scanner, cloud sync, an editor). Scanners and sync clients let go within a few ms.
    private static final int MOVE_ATTEMPTS = 5;
    private static final long MOVE_RETRY_DELAY_MS = 30;

    @FunctionalInterface
    public interface Content {
        void writeTo(OutputStream out) throws IOException;
    }

    public static void write(Path target, Content content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, target.getFileName().toString(), TEMP_SUFFIX);
        try {
            try (FileOutputStream out = new FileOutputStream(temp.toFile())) {
                content.writeTo(out);
                out.flush();
                // Without this a power loss right after the move can leave an empty file behind.
                out.getFD().sync();
            }
            replace(temp, target);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                // Still held open by someone else; deleteLeftovers() removes it on the next start.
                // Throwing here would turn a finished save into a failed one.
            }
        }
    }

    /** Deletes temp files that a write killed mid-way (task manager, JVM crash) left next to target. */
    public static void deleteLeftovers(Path target) {
        Path dir = target.toAbsolutePath().getParent();
        String prefix = target.getFileName().toString();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, p -> isTempFileOf(p.getFileName().toString(), prefix))) {
            for (Path file : files) Files.deleteIfExists(file);
        } catch (NoSuchFileException e) {
            // no folder yet, nothing left behind
        } catch (IOException e) {
            StashHunter.LOG.warn("Could not delete leftover temp files of {}", target, e);
        }
    }

    // Files.createTempFile names them prefix + random number + suffix.
    static boolean isTempFileOf(String name, String prefix) {
        if (!name.startsWith(prefix) || !name.endsWith(TEMP_SUFFIX)) return false;
        String number = name.substring(prefix.length(), name.length() - TEMP_SUFFIX.length());
        return !number.isEmpty() && number.chars().allMatch(Character::isDigit);
    }

    private static void replace(Path temp, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                move(temp, target);
                return;
            } catch (FileSystemException e) {
                if (attempt < MOVE_ATTEMPTS && waitBeforeRetry()) continue;
                overwrite(temp, target, e);
                return;
            }
        }
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Still locked: write into the target like before the temp file existed. Not crash-safe, but
    // the save isn't lost, and the share modes that block a rename still allow writing.
    private static void overwrite(Path temp, Path target, IOException moveFailure) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            Files.copy(temp, out);
            out.getFD().sync();
        } catch (IOException e) {
            e.addSuppressed(moveFailure);
            throw e;
        }
    }

    private static boolean waitBeforeRetry() {
        try {
            Thread.sleep(MOVE_RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
