package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.utils.CaveAirPockets.Cell;
import com.stashhunter.stashhunter.utils.CaveAirPockets.Pocket;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.stashhunter.stashhunter.utils.CaveAirPockets.AIR;
import static com.stashhunter.stashhunter.utils.CaveAirPockets.CAVE_AIR;
import static com.stashhunter.stashhunter.utils.CaveAirPockets.UNKNOWN;
import static org.junit.jupiter.api.Assertions.*;

class CaveAirPocketsTest {
    private static final int MAX_SIZE = 8;

    /** Solid (OTHER) everywhere unless set. */
    private static final class World implements CaveAirPockets.Blocks {
        private final Map<List<Integer>, Integer> blocks = new HashMap<>();
        private int unknownBeyondX = Integer.MAX_VALUE;

        World fill(int x1, int y1, int z1, int x2, int y2, int z2, int kind) {
            for (int x = x1; x <= x2; x++)
                for (int y = y1; y <= y2; y++)
                    for (int z = z1; z <= z2; z++) blocks.put(List.of(x, y, z), kind);
            return this;
        }

        World set(int x, int y, int z, int kind) {
            return fill(x, y, z, x, y, z, kind);
        }

        @Override
        public int at(int x, int y, int z) {
            if (x > unknownBeyondX) return UNKNOWN;
            return blocks.getOrDefault(List.of(x, y, z), CaveAirPockets.OTHER);
        }
    }

    // A mineshaft-like corridor of cave air along x, 3 wide and 3 high.
    private static World corridor() {
        return new World().fill(0, 10, 0, 15, 12, 2, CAVE_AIR);
    }

    private static List<Pocket> find(World world) {
        return CaveAirPockets.find(world, 0, 0, 0, 15, 30, 15, MAX_SIZE);
    }

    @Test
    void airInsideCaveAirIsAPocket() {
        // A cobweb or rail broken in the corridor.
        World world = corridor().set(5, 10, 1, AIR);
        List<Pocket> pockets = find(world);
        assertEquals(1, pockets.size());
        assertEquals(List.of(new Cell(5, 10, 1)), pockets.get(0).cells());
        assertTrue(pockets.get(0).resolved());
    }

    @Test
    void minedBlocksInTheWallAreOnePocket() {
        // Two ore blocks mined out of the corridor wall, one behind the other.
        World world = corridor().set(7, 11, 3, AIR).set(7, 11, 4, AIR);
        List<Pocket> pockets = find(world);
        assertEquals(1, pockets.size());
        assertEquals(2, pockets.get(0).cells().size());
    }

    @Test
    void separatePocketsAreReportedSeparately() {
        World world = corridor().set(2, 10, 1, AIR).set(12, 12, 1, AIR);
        assertEquals(2, find(world).size());
    }

    @Test
    void airNotTouchingCaveAirIsIgnored() {
        // A noise cave (plain air from generation) with no cave air nearby.
        World world = new World().fill(0, 10, 0, 2, 11, 2, AIR);
        assertTrue(find(world).isEmpty());
    }

    @Test
    void openSpaceTouchingCaveAirIsIgnored() {
        // The corridor runs into a natural air cave much larger than a pocket, touching it in
        // several places: every touching block belongs to the same too-big group.
        World world = corridor().fill(0, 13, 0, 15, 15, 2, AIR);
        assertTrue(find(world).isEmpty());
    }

    @Test
    void sizeLimitIsInclusive() {
        World exactly = corridor().fill(4, 10, 3, 4 + MAX_SIZE - 1, 10, 3, AIR);
        assertEquals(MAX_SIZE, find(exactly).get(0).cells().size());

        World oneMore = corridor().fill(4, 10, 3, 4 + MAX_SIZE, 10, 3, AIR);
        assertTrue(find(oneMore).isEmpty());
    }

    @Test
    void pocketReachingUnreadableBlocksIsUnresolved() {
        // Mined block at the chunk edge: what lies beyond isn't known yet.
        World world = corridor().set(15, 11, 3, AIR);
        world.unknownBeyondX = 15;
        List<Pocket> pockets = find(world);
        assertEquals(1, pockets.size());
        assertFalse(pockets.get(0).resolved());
    }

    @Test
    void bigGroupReachingUnreadableBlocksIsStillIgnored() {
        World world = corridor().fill(0, 13, 0, 15, 15, 2, AIR);
        world.unknownBeyondX = 15;
        assertTrue(find(world).isEmpty());
    }

    @Test
    void fillLeavesTheSeedBox() {
        // The seed is in the box, the rest of the pocket above it isn't; the whole pocket counts.
        World world = corridor().set(3, 13, 1, AIR).set(3, 14, 1, AIR).set(3, 15, 1, AIR);
        List<Pocket> pockets = CaveAirPockets.find(world, 0, 0, 0, 15, 13, 15, MAX_SIZE);
        assertEquals(1, pockets.size());
        assertEquals(3, pockets.get(0).cells().size());

        assertTrue(CaveAirPockets.find(world, 0, 14, 0, 15, 30, 15, MAX_SIZE).isEmpty(), "no seed in the box");
    }

    @Test
    void worksWithNegativeWorldCoordinates() {
        World world = new World().fill(-100, -40, -100, -98, -38, -98, CAVE_AIR).set(-99, -37, -99, AIR);
        List<Pocket> pockets = CaveAirPockets.find(world, -110, -64, -110, -90, 0, -90, MAX_SIZE);
        assertEquals(List.of(new Cell(-99, -37, -99)), pockets.get(0).cells());
    }
}
