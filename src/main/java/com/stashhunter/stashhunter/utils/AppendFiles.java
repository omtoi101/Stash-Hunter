package com.stashhunter.stashhunter.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Appends lines to a data file with one entry per line. Appending never touches old entries, so
 * these files need no temp file; but a crash mid-write can leave the last line unfinished, and the
 * next entry must not be glued onto it (both would be unreadable then).
 */
public final class AppendFiles {
    private AppendFiles() {}

    /** Appends {@code lines} (each ending with a line break), creating the file and its folder if needed. */
    public static void append(Path file, String lines) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long end = channel.size();
            String text = endsWithLineBreak(channel, end) ? lines : System.lineSeparator() + lines;
            ByteBuffer data = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (data.hasRemaining()) end += channel.write(data, end);
        }
    }

    private static boolean endsWithLineBreak(FileChannel channel, long size) throws IOException {
        if (size == 0) return true;
        ByteBuffer last = ByteBuffer.allocate(1);
        if (channel.read(last, size - 1) != 1) return true;
        // A lone '\r' (cut off in the middle of "\r\n") already ends the line for readAllLines.
        byte b = last.get(0);
        return b == '\n' || b == '\r';
    }
}
