package com.stashhunter.stashhunter.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.LongPredicate;

/** Queue of chunks waiting for a rescan: each chunk once, oldest first. Client thread only. */
public final class DirtyChunks {
    private final LinkedHashSet<Long> queue = new LinkedHashSet<>();

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int x(long key) {
        return (int) (key >> 32);
    }

    public static int z(long key) {
        return (int) key;
    }

    public void mark(int x, int z) {
        queue.add(key(x, z));
    }

    public boolean contains(int x, int z) {
        return queue.contains(key(x, z));
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    /** Removes and returns up to {@code max} queued chunks for which {@code ready} holds; the others stay queued. */
    public List<Long> drain(int max, LongPredicate ready) {
        List<Long> out = new ArrayList<>();
        Iterator<Long> it = queue.iterator();
        while (it.hasNext() && out.size() < max) {
            long k = it.next();
            if (ready.test(k)) {
                out.add(k);
                it.remove();
            }
        }
        return out;
    }

    /** Drops every queued chunk that {@code keep} rejects. */
    public void retain(LongPredicate keep) {
        queue.removeIf(k -> !keep.test(k));
    }
}
