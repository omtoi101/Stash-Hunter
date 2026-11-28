package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** The classic (26.2.2) decision with the 26.2.2 defaults: threshold 10, link 30, radius 32, volume 5000, density 0.002. */
class ClassicRuleTest {
    private static final ClassicRule.Settings DEFAULTS = new ClassicRule.Settings(10, 30, 32, true, false, 5000, 0.002);

    private static Map<BlockPos, SignalKind> chestsInARow(int count) {
        Map<BlockPos, SignalKind> m = new HashMap<>();
        for (int x = 0; x < count; x++) m.put(new BlockPos(x, 64, 0), SignalKind.CHEST);
        return m;
    }

    private static ClassicRule.Outcome decideNew(List<BlockPos> part, Map<BlockPos, SignalKind> types,
                                                 ClassicRule.Settings settings) {
        return ClassicRule.decide(new BlockPos(0, 64, 0), part, types, false, false, () -> Tier.NONE, settings,
            center -> false);
    }

    @Test
    void tenDenseChestsAtThresholdTenAreReported() {
        Map<BlockPos, SignalKind> found = chestsInARow(10);
        List<List<BlockPos>> parts = ClassicRule.parts(found, DEFAULTS);
        assertEquals(1, parts.size());
        assertEquals(10, parts.get(0).size());
        assertEquals(ClassicRule.Action.REPORT, decideNew(parts.get(0), found, DEFAULTS).action());
    }

    @Test
    void nineChestsAreNothing() {
        assertTrue(ClassicRule.parts(chestsInARow(9), DEFAULTS).isEmpty());
    }

    @Test
    void sparseClusterIsTightenedToItsDenseCore() {
        Map<BlockPos, SignalKind> found = chestsInARow(10);
        found.put(new BlockPos(15, 80, 15), SignalKind.CHEST);
        found.put(new BlockPos(-15, 48, -15), SignalKind.CHEST);
        List<List<BlockPos>> parts = ClassicRule.parts(found, DEFAULTS);
        assertEquals(1, parts.size());
        assertEquals(new HashSet<>(chestsInARow(10).keySet()), new HashSet<>(parts.get(0)));
        assertEquals(ClassicRule.Action.REPORT, decideNew(parts.get(0), found, DEFAULTS).action());
    }

    @Test
    void withoutTheStructureFilterTheWholeClusterCounts() {
        Map<BlockPos, SignalKind> found = chestsInARow(10);
        found.put(new BlockPos(15, 80, 15), SignalKind.CHEST);
        found.put(new BlockPos(-15, 48, -15), SignalKind.CHEST);
        ClassicRule.Settings noFilter = new ClassicRule.Settings(10, 30, 32, false, false, 5000, 0.002);
        List<List<BlockPos>> parts = ClassicRule.parts(found, noFilter);
        assertEquals(1, parts.size());
        assertEquals(12, parts.get(0).size());
        assertEquals(ClassicRule.Action.REPORT, decideNew(parts.get(0), found, noFilter).action());
    }

    @Test
    void sparseClusterIsRejectedByShape() {
        Map<BlockPos, SignalKind> found = new HashMap<>();
        for (int i = 0; i < 10; i++) found.put(new BlockPos(i * 3, 64 + i * 3, i * 3), SignalKind.CHEST);
        List<BlockPos> all = new ArrayList<>(found.keySet());
        ClassicRule.Outcome outcome = decideNew(all, found, DEFAULTS);
        assertEquals(ClassicRule.Action.REJECT, outcome.action());
        assertEquals(Rejection.STRUCTURE_SHAPE, outcome.reason());
    }

    @Test
    void dungeonWithIncludeSpawnersIsNothing() {
        ClassicRule.Settings spawners = new ClassicRule.Settings(3, 30, 32, true, true, 5000, 0.002);
        Map<BlockPos, SignalKind> found = new HashMap<>();
        found.put(new BlockPos(0, 65, 0), SignalKind.SPAWNER);
        found.put(new BlockPos(3, 64, 0), SignalKind.CHEST);
        found.put(new BlockPos(-3, 64, 0), SignalKind.CHEST);
        List<List<BlockPos>> parts = ClassicRule.parts(found, spawners);
        assertEquals(1, parts.size());
        ClassicRule.Outcome outcome = decideNew(parts.get(0), found, spawners);
        assertEquals(ClassicRule.Action.REJECT, outcome.action());
        assertEquals(Rejection.STRUCTURE_DUNGEON, outcome.reason());
    }

    @Test
    void nearADetectedStructureSpawnerIsNothing() {
        Map<BlockPos, SignalKind> found = chestsInARow(10);
        List<BlockPos> part = ClassicRule.parts(found, DEFAULTS).get(0);
        ClassicRule.Outcome outcome = ClassicRule.decide(new BlockPos(4, 64, 0), part, found, false, false,
            () -> Tier.NONE, DEFAULTS, center -> true);
        assertEquals(Rejection.STRUCTURE_SPAWNER, outcome.reason());
    }

    @Test
    void stashReportedThisSessionIsOnlyReportedAgainWhenGrown() {
        Map<BlockPos, SignalKind> found = chestsInARow(10);
        List<BlockPos> part = new ArrayList<>(found.keySet());
        BlockPos c = new BlockPos(4, 64, 0);
        assertEquals(ClassicRule.Action.SKIP,
            ClassicRule.decide(c, part, found, true, false, () -> Tier.NONE, DEFAULTS, p -> false).action());
        assertEquals(ClassicRule.Action.UPDATE,
            ClassicRule.decide(c, part, found, true, true, () -> Tier.NONE, DEFAULTS, p -> false).action());
    }

    @Test
    void knownStashIsAlreadyReportedAndKnownHintIsUpdated() {
        Map<BlockPos, SignalKind> found = chestsInARow(10);
        List<BlockPos> part = new ArrayList<>(found.keySet());
        BlockPos c = new BlockPos(4, 64, 0);
        ClassicRule.Outcome known = ClassicRule.decide(c, part, found, false, false, () -> Tier.STASH, DEFAULTS,
            p -> false);
        assertEquals(ClassicRule.Action.REJECT, known.action());
        assertEquals(Rejection.ALREADY_REPORTED, known.reason());
        assertEquals(ClassicRule.Action.UPDATE,
            ClassicRule.decide(c, part, found, false, false, () -> Tier.HINT, DEFAULTS, p -> false).action());
    }
}
