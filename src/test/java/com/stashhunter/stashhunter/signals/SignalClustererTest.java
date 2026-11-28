package com.stashhunter.stashhunter.signals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SignalClustererTest {
    private static final int LINK = 16;
    private static final int RADIUS = 48;
    private static final int MIN = 5;

    private record TestFilter(boolean on, Predicate<List<BlockPos>> shape, boolean nearSpawner)
            implements ContainerFilter {
        @Override
        public boolean filterNaturalStructures() {
            return on;
        }

        @Override
        public boolean hasStashShape(List<BlockPos> containers) {
            return shape.test(containers);
        }

        @Override
        public boolean nearStructureSpawner(BlockPos center) {
            return nearSpawner;
        }
    }

    private static Signal sig(int x, int y, int z, SignalKind kind) {
        return new Signal(new BlockPos(x, y, z), kind);
    }

    private static List<Candidate> run(List<Signal> signals, Set<BlockPos> spawners, ContainerFilter filter) {
        return SignalClusterer.candidates(signals, spawners, LINK, RADIUS, MIN, filter);
    }

    private static boolean has(Candidate c, SignalKind kind) {
        return c.signals().stream().anyMatch(s -> s.kind() == kind);
    }

    @Test
    void bedBetweenTwoChestGroupsDoesNotRescueSparseChests() {
        List<Signal> signals = new ArrayList<>(List.of(
                sig(0, 64, 0, SignalKind.CHEST), sig(1, 64, 0, SignalKind.CHEST),
                sig(10, 64, 0, SignalKind.BED),
                sig(20, 64, 0, SignalKind.CHEST), sig(21, 64, 0, SignalKind.CHEST)));
        List<Candidate> result = run(signals, Set.of(), new TestFilter(true, c -> false, false));
        assertEquals(1, result.size());
        Candidate c = result.get(0);
        assertTrue(c.keptContainers().isEmpty());
        assertTrue(has(c, SignalKind.BED));
        assertEquals(4, c.filtered().get(Rejection.STRUCTURE_SHAPE));
        assertFalse(c.containerShapeOk());
    }

    @Test
    void sparseContainersAreKeptWhenShapeAccepts() {
        List<Signal> signals = List.of(sig(0, 64, 0, SignalKind.CHEST), sig(1, 64, 0, SignalKind.CHEST));
        Candidate c = run(signals, Set.of(), new TestFilter(true, cs -> true, false)).get(0);
        assertEquals(2, c.keptContainers().size());
        assertTrue(c.filtered().isEmpty());
    }

    @Test
    void dungeonChestsAreFilteredButShulkerStays() {
        BlockPos spawner = new BlockPos(0, 64, 0);
        List<Signal> signals = List.of(
                new Signal(spawner, SignalKind.SPAWNER),
                sig(2, 63, 0, SignalKind.CHEST), sig(-2, 63, 0, SignalKind.CHEST),
                sig(5, 64, 5, SignalKind.SHULKER_BOX));
        Candidate c = run(signals, Set.of(spawner), new TestFilter(true, cs -> true, false)).get(0);
        assertTrue(c.keptContainers().isEmpty());
        assertTrue(has(c, SignalKind.SHULKER_BOX));
        assertEquals(2, c.filtered().get(Rejection.STRUCTURE_DUNGEON));
    }

    @Test
    void spawnerProximityFiltersOnlyContainers() {
        List<Signal> signals = List.of(
                sig(0, 64, 0, SignalKind.CHEST), sig(1, 64, 0, SignalKind.CHEST),
                sig(2, 64, 0, SignalKind.WRITTEN_SIGN));
        Candidate c = run(signals, Set.of(), new TestFilter(true, cs -> true, true)).get(0);
        assertTrue(c.keptContainers().isEmpty());
        assertTrue(has(c, SignalKind.WRITTEN_SIGN));
        assertEquals(2, c.filtered().get(Rejection.STRUCTURE_SPAWNER));
    }

    @Test
    void filterOffKeepsAllContainers() {
        List<Signal> signals = List.of(sig(0, 64, 0, SignalKind.CHEST), sig(1, 64, 0, SignalKind.BARREL));
        Candidate c = run(signals, Set.of(), new TestFilter(false, cs -> false, true)).get(0);
        assertEquals(2, c.keptContainers().size());
        assertTrue(c.filtered().isEmpty());
    }

    @Test
    void separateGroupsGiveSeparateCandidates() {
        List<Signal> signals = List.of(
                sig(0, 64, 0, SignalKind.CHEST), sig(1, 64, 0, SignalKind.CHEST),
                sig(500, 64, 0, SignalKind.CHEST), sig(501, 64, 0, SignalKind.CHEST));
        assertEquals(2, run(signals, Set.of(), new TestFilter(true, cs -> true, false)).size());
    }

    @Test
    void strongKindsCountForShapeButAreNeverFiltered() {
        List<Signal> signals = new ArrayList<>();
        for (int i = 0; i < 10; i++) signals.add(sig(i, 64, 0, SignalKind.SHULKER_BOX));
        Candidate c = run(signals, Set.of(), new TestFilter(true, cs -> cs.size() == 10, false)).get(0);
        assertTrue(c.keptContainers().isEmpty());
        assertTrue(c.containerShapeOk());
        assertEquals(10, c.signals().size());
        assertTrue(c.filtered().isEmpty());
    }

    @Test
    void clusterWithOnlyRejectedContainersIsDropped() {
        List<Signal> signals = List.of(sig(0, 64, 0, SignalKind.CHEST), sig(1, 64, 0, SignalKind.CHEST));
        assertTrue(run(signals, Set.of(), new TestFilter(true, cs -> false, false)).isEmpty());
    }
}
