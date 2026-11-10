package com.stashhunter.stashhunter.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.stashhunter.stashhunter.StashHunter;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What the containers on one server held when they were last opened, including the contents of
 * shulker boxes inside them. Stored as JSON so other tools can read it. One file per server.
 * Not thread-safe: the chest-index module and its command use it on the client thread only. The
 * file is written on a background thread (a big index takes a while to serialize and fsync).
 */
public final class ChestIndex {
    /** One stack as read from a slot. contents is set for items that hold items (shulker boxes). */
    public record Stack(String id, int count, String customName, List<Stack> contents) {}

    public record Pos(int x, int y, int z) {}

    public record ItemCount(String id, int count) {}

    /** A shulker box (or another item holding items) inside the container, with what it holds. */
    public record Shulker(String id, String name, List<ItemCount> items) {}

    /**
     * A container as last seen. pair is the other half of a double chest (one shared inventory),
     * items are the top-level stacks summed per item, shulkers in slot order.
     */
    public record Entry(String dimension, Pos pos, Pos pair, String block, int slots, String indexedAt,
                        List<ItemCount> items, List<Shulker> shulkers) {
        /** Same container content, whenever it was seen. */
        public boolean sameContents(Entry other) {
            return other != null && slots == other.slots && Objects.equals(block, other.block)
                && Objects.equals(pair, other.pair) && items.equals(other.items) && shulkers.equals(other.shulkers);
        }

        /** All items, including those inside shulker boxes. */
        public int totalItems() {
            int total = 0;
            for (ItemCount item : items) total += item.count();
            for (Shulker shulker : shulkers) {
                for (ItemCount item : shulker.items()) total += item.count();
            }
            return total;
        }
    }

    /** A container holding items that match a search, and how many of them (shulker contents included). */
    public record Hit(Entry entry, int count) {}

    private record FileFormat(int version, List<Entry> containers) {}

    // What the save thread writes. entries is immutable (records of immutable lists); null deletes the file.
    private record Snapshot(List<Entry> entries) {}

    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    // How long close() waits for the last write.
    private static final long CLOSE_TIMEOUT_MS = 2000;

    private final Path file;
    // Both halves of a double chest map to the same entry.
    private final Map<String, Entry> byPos = new LinkedHashMap<>();
    private boolean dirty;
    // Set if the file exists but couldn't be read: saving would replace it with a partial index.
    private boolean readFailed;
    // Created on the first save. Writes and deletes go through it in order.
    private BackgroundSaver<Snapshot> saver;
    // Set by the save thread when a write failed; the next saveIfDirty() tries again.
    private final AtomicBoolean writeFailed = new AtomicBoolean();

    private ChestIndex(Path file) {
        this.file = file;
    }

    public static Entry entry(String dimension, Pos pos, Pos pair, String block, int slots, Instant time, List<Stack> stacks) {
        Map<String, Integer> totals = new HashMap<>();
        List<Shulker> shulkers = new ArrayList<>();
        for (Stack stack : stacks) {
            totals.merge(stack.id(), stack.count(), Integer::sum);
            if (stack.contents() != null) {
                Map<String, Integer> inner = new HashMap<>();
                for (Stack content : stack.contents()) inner.merge(content.id(), content.count(), Integer::sum);
                shulkers.add(new Shulker(stack.id(), stack.customName(), sorted(inner)));
            }
        }
        return new Entry(dimension, pos, pair, block, slots, time.toString(), sorted(totals), List.copyOf(shulkers));
    }

    private static List<ItemCount> sorted(Map<String, Integer> totals) {
        List<ItemCount> items = new ArrayList<>();
        totals.forEach((id, count) -> items.add(new ItemCount(id, count)));
        items.sort(Comparator.comparingInt(ItemCount::count).reversed().thenComparing(ItemCount::id));
        return List.copyOf(items);
    }

    public static ChestIndex load(Path file) {
        ChestIndex index = new ChestIndex(file);
        AtomicFiles.deleteLeftovers(file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            FileFormat data = GSON.fromJson(reader, FileFormat.class);
            int skipped = 0;
            if (data != null && data.containers() != null) {
                for (Entry entry : data.containers()) {
                    Entry valid = validated(entry);
                    if (valid != null) index.put(valid);
                    else skipped++;
                }
            }
            if (skipped > 0) StashHunter.LOG.warn("Skipped {} unreadable container(s) in {}", skipped, file);
        } catch (NoSuchFileException e) {
            // nothing indexed on this server yet
        } catch (IOException e) {
            index.readFailed = true;
            StashHunter.LOG.warn("Could not read chest index {}; it won't be saved this session", file, e);
        } catch (JsonParseException | IllegalStateException e) {
            // Keep the broken file: the next save would overwrite it.
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                StashHunter.LOG.warn("Could not read chest index {} ({}), moved it to {}", file, e.getMessage(), backup.getFileName());
            } catch (IOException moveFailure) {
                index.readFailed = true;
                StashHunter.LOG.warn("Could not read chest index {} ({}); it won't be saved this session", file, e.getMessage());
            }
        }
        index.dirty = false;
        return index;
    }

    // Hand-edited or damaged files can leave fields out; Gson then fills in nulls.
    private static Entry validated(Entry entry) {
        if (entry == null || entry.dimension() == null || entry.pos() == null) return null;
        List<Shulker> shulkers = new ArrayList<>();
        for (Shulker shulker : nonNull(entry.shulkers())) {
            if (shulker != null) shulkers.add(new Shulker(shulker.id(), shulker.name(), validItems(shulker.items())));
        }
        return new Entry(entry.dimension(), entry.pos(), entry.pair(), entry.block(), entry.slots(), entry.indexedAt(),
            validItems(entry.items()), List.copyOf(shulkers));
    }

    private static List<ItemCount> validItems(List<ItemCount> items) {
        return nonNull(items).stream().filter(item -> item != null && item.id() != null).toList();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list != null ? list : List.of();
    }

    public Path file() {
        return file;
    }

    public Entry get(String dimension, Pos pos) {
        return byPos.get(key(dimension, pos));
    }

    /** True if the container at pos (either half of a double chest) has been indexed. */
    public boolean contains(String dimension, Pos pos) {
        return byPos.containsKey(key(dimension, pos));
    }

    /** Adds or replaces a container; a double chest replaces whatever was indexed at either half. */
    public void put(Entry entry) {
        remove(entry.dimension(), entry.pos());
        if (entry.pair() != null) remove(entry.dimension(), entry.pair());
        byPos.put(key(entry.dimension(), entry.pos()), entry);
        if (entry.pair() != null) byPos.put(key(entry.dimension(), entry.pair()), entry);
        dirty = true;
    }

    private void remove(String dimension, Pos pos) {
        Entry old = byPos.remove(key(dimension, pos));
        if (old == null) return;
        byPos.remove(key(old.dimension(), old.pos()));
        if (old.pair() != null) byPos.remove(key(old.dimension(), old.pair()));
    }

    public Collection<Entry> entries() {
        return new LinkedHashSet<>(byPos.values());
    }

    public int size() {
        return entries().size();
    }

    /** Containers holding items whose id contains the query (e.g. "diamond"), most items first. */
    public List<Hit> search(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Hit> hits = new ArrayList<>();
        if (needle.isEmpty()) return hits;
        for (Entry entry : entries()) {
            int count = matching(entry.items(), needle);
            for (Shulker shulker : entry.shulkers()) count += matching(shulker.items(), needle);
            if (count > 0) hits.add(new Hit(entry, count));
        }
        hits.sort(Comparator.comparingInt(Hit::count).reversed());
        return hits;
    }

    private static int matching(List<ItemCount> items, String needle) {
        int count = 0;
        for (ItemCount item : items) {
            if (item.id().contains(needle)) count += item.count();
        }
        return count;
    }

    /** A container holding shulker boxes whose custom name matched a search, in slot order. */
    public record NameHit(Entry entry, List<Shulker> shulkers) {}

    /**
     * Containers holding shulker boxes (or other item holders) whose custom name contains the query,
     * ignoring case, e.g. "kit" finds "PvP Kit". Most matching boxes first.
     */
    public List<NameHit> searchShulkerNames(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<NameHit> hits = new ArrayList<>();
        if (needle.isEmpty()) return hits;
        for (Entry entry : entries()) {
            List<Shulker> named = entry.shulkers().stream()
                .filter(shulker -> shulker.name() != null && shulker.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
            if (!named.isEmpty()) hits.add(new NameHit(entry, named));
        }
        hits.sort(Comparator.comparingInt((NameHit hit) -> hit.shulkers().size()).reversed());
        return hits;
    }

    /** Queues a save if something changed since the last one; returns at once. */
    public void saveIfDirty() {
        if (writeFailed.getAndSet(false)) dirty = true;
        if (!dirty || readFailed) return;
        saver().save(new Snapshot(List.copyOf(entries())));
        dirty = false;
    }

    /** Forgets every container on this server, on disk too. */
    public void clear() {
        byPos.clear();
        dirty = false;
        readFailed = false;
        writeFailed.set(false);
        // Queued behind a pending write, so that write can't bring the file back afterwards.
        saver().save(new Snapshot(null));
    }

    /**
     * Saves what changed and waits (at most 2 s) until it is on disk, then stops the save thread.
     * For leaving the server; later saves would be written on the calling thread.
     */
    public void close() {
        saveIfDirty();
        if (saver != null) saver.shutdown(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private BackgroundSaver<Snapshot> saver() {
        if (saver == null) saver = new BackgroundSaver<>("StashHunter-ChestIndexSave", this::write);
        return saver;
    }

    // Runs on the save thread. Reads only the snapshot and the final file field.
    private void write(Snapshot snapshot) {
        try {
            if (snapshot.entries() == null) {
                Files.deleteIfExists(file);
                return;
            }
            AtomicFiles.write(file, out -> {
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                GSON.toJson(new FileFormat(FORMAT_VERSION, snapshot.entries()), writer);
                writer.flush();
            });
        } catch (IOException | JsonIOException e) {
            if (snapshot.entries() != null) writeFailed.set(true);
            StashHunter.LOG.warn("Could not {} chest index {}", snapshot.entries() == null ? "delete" : "save", file, e);
        }
    }

    private static String key(String dimension, Pos pos) {
        return dimension + " " + pos.x() + " " + pos.y() + " " + pos.z();
    }
}
