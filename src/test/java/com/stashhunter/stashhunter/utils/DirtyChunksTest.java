package com.stashhunter.stashhunter.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DirtyChunksTest {
    @Test
    void drainsAtMostMaxInInsertionOrder() {
        DirtyChunks d = new DirtyChunks();
        d.mark(0, 0);
        d.mark(1, 0);
        d.mark(2, 0);
        assertEquals(List.of(DirtyChunks.key(0, 0), DirtyChunks.key(1, 0)), d.drain(2, k -> true));
        assertEquals(1, d.size());
    }

    @Test
    void markingTwiceKeepsOneEntry() {
        DirtyChunks d = new DirtyChunks();
        d.mark(3, -4);
        d.mark(3, -4);
        assertEquals(1, d.size());
    }

    @Test
    void notReadyChunksStayQueued() {
        DirtyChunks d = new DirtyChunks();
        d.mark(0, 0);
        d.mark(1, 0);
        List<Long> got = d.drain(5, k -> k == DirtyChunks.key(1, 0));
        assertEquals(List.of(DirtyChunks.key(1, 0)), got);
        assertTrue(d.contains(0, 0));
    }

    @Test
    void retainDropsRejected() {
        DirtyChunks d = new DirtyChunks();
        d.mark(0, 0);
        d.mark(9, 9);
        d.retain(k -> k == DirtyChunks.key(0, 0));
        assertTrue(d.contains(0, 0));
        assertFalse(d.contains(9, 9));
    }

    @Test
    void keyRoundTrips() {
        long k = DirtyChunks.key(-7, 12);
        assertEquals(-7, DirtyChunks.x(k));
        assertEquals(12, DirtyChunks.z(k));
    }
}
