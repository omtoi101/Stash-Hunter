package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SignalCollapseTest {
    private static long count(Map<BlockPos, SignalKind> signals, SignalKind kind) {
        return signals.values().stream().filter(k -> k == kind).count();
    }

    @Test
    void concreteWallInOneSectionIsOneSignal() {
        Map<BlockPos, SignalKind> in = new HashMap<>();
        for (int x = 0; x < 10; x++)
            for (int y = 64; y < 67; y++) in.put(new BlockPos(x, y, 3), SignalKind.UNNATURAL_BLOCK);
        Map<BlockPos, SignalKind> out = SignalCollapse.perSection(in);
        assertEquals(1, out.size());
        assertEquals(1, count(out, SignalKind.UNNATURAL_BLOCK));
    }

    @Test
    void containersAndStrongKindsStayPerBlock() {
        Map<BlockPos, SignalKind> in = new HashMap<>();
        for (int x = 0; x < 5; x++) in.put(new BlockPos(x, 64, 0), SignalKind.CHEST);
        for (int x = 0; x < 3; x++) in.put(new BlockPos(x, 65, 0), SignalKind.SHULKER_BOX);
        for (int x = 0; x < 4; x++) in.put(new BlockPos(x, 66, 0), SignalKind.BED);
        Map<BlockPos, SignalKind> out = SignalCollapse.perSection(in);
        assertEquals(5, count(out, SignalKind.CHEST));
        assertEquals(3, count(out, SignalKind.SHULKER_BOX));
        assertEquals(1, count(out, SignalKind.BED));
    }

    @Test
    void portalBlocksCollapseButKindsStaySeparate() {
        Map<BlockPos, SignalKind> in = new HashMap<>();
        for (int x = 1; x <= 2; x++)
            for (int y = 61; y <= 63; y++) in.put(new BlockPos(x, y, 5), SignalKind.OPEN_PORTAL);
        in.put(new BlockPos(0, 60, 5), SignalKind.OBSIDIAN_FRAME);
        Map<BlockPos, SignalKind> out = SignalCollapse.perSection(in);
        assertEquals(1, count(out, SignalKind.OPEN_PORTAL));
        assertEquals(1, count(out, SignalKind.OBSIDIAN_FRAME));
    }

    @Test
    void oneSignalPerSection() {
        Map<BlockPos, SignalKind> in = new HashMap<>();
        // Sections y 48..63 and 64..79, and a second chunk column at x 16.
        in.put(new BlockPos(0, 60, 0), SignalKind.UNNATURAL_BLOCK);
        in.put(new BlockPos(0, 63, 0), SignalKind.UNNATURAL_BLOCK);
        in.put(new BlockPos(0, 64, 0), SignalKind.UNNATURAL_BLOCK);
        in.put(new BlockPos(16, 64, 0), SignalKind.UNNATURAL_BLOCK);
        assertEquals(3, SignalCollapse.perSection(in).size());
    }

    @Test
    void keptSignalIsTheMemberNearestTheMiddle() {
        Map<BlockPos, SignalKind> in = new HashMap<>();
        for (int x = 0; x <= 8; x++) in.put(new BlockPos(x, 64, 0), SignalKind.UNNATURAL_BLOCK);
        Map<BlockPos, SignalKind> out = SignalCollapse.perSection(in);
        assertEquals(Map.of(new BlockPos(4, 64, 0), SignalKind.UNNATURAL_BLOCK), out);
    }
}
