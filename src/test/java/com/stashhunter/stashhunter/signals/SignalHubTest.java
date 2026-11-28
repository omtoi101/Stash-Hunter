package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignalHubTest {

    @BeforeEach
    void setUp() {
        SignalHub.clearAll();
    }

    @Test
    void hubReturnsOnlyNearbySignalsOfTheDimension() {
        SignalHub.put("a", "minecraft:overworld", new BlockPos(10, 64, 10), SignalKind.CHEST);
        SignalHub.put("a", "minecraft:overworld", new BlockPos(500, 64, 500), SignalKind.CHEST);
        SignalHub.put("a", "minecraft:the_nether", new BlockPos(10, 64, 10), SignalKind.BARREL);

        List<Signal> near = SignalHub.near("minecraft:overworld", 0, 0, 32);

        assertEquals(1, near.size());
        assertEquals(new BlockPos(10, 64, 10), near.get(0).pos());
        assertEquals(SignalKind.CHEST, near.get(0).kind());
    }

    @Test
    void putSamePositionReplacesKindWithoutDuplicate() {
        BlockPos pos = new BlockPos(1, 60, 1);
        SignalHub.put("a", "d", pos, SignalKind.CHEST);
        SignalHub.put("a", "d", pos, SignalKind.BARREL);

        List<Signal> near = SignalHub.near("d", 0, 0, 10);

        assertEquals(1, near.size());
        assertEquals(SignalKind.BARREL, near.get(0).kind());
    }

    @Test
    void clearingASourceKeepsOthers() {
        SignalHub.put("a", "d", new BlockPos(1, 60, 1), SignalKind.CHEST);
        SignalHub.put("b", "d", new BlockPos(2, 60, 2), SignalKind.BARREL);

        SignalHub.clear("a");

        List<Signal> near = SignalHub.near("d", 0, 0, 10);
        assertEquals(1, near.size());
        assertEquals(SignalKind.BARREL, near.get(0).kind());
    }

    @Test
    void removeDropsOnlyThatEntry() {
        SignalHub.put("a", "d", new BlockPos(1, 60, 1), SignalKind.CHEST);
        SignalHub.put("a", "d", new BlockPos(2, 60, 2), SignalKind.CHEST);

        SignalHub.remove("a", "d", new BlockPos(1, 60, 1));

        List<Signal> near = SignalHub.near("d", 0, 0, 10);
        assertEquals(1, near.size());
        assertEquals(new BlockPos(2, 60, 2), near.get(0).pos());
    }

    @Test
    void hubCapsEachSource() {
        for (int i = 0; i < 5000; i++) {
            SignalHub.put("a", "d", new BlockPos(i, 60, 0), SignalKind.CHEST);
        }

        List<Signal> near = SignalHub.near("d", 0, 0, 100000);

        assertEquals(4096, near.size());
        assertTrue(near.stream().noneMatch(s -> s.pos().getX() == 0), "oldest entries must be evicted");
    }
}
