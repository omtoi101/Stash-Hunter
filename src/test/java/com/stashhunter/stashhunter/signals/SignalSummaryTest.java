package com.stashhunter.stashhunter.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SignalSummaryTest {
    private static List<Signal> signals(Object... kindAndCount) {
        List<Signal> list = new ArrayList<>();
        int x = 0;
        for (int i = 0; i < kindAndCount.length; i += 2) {
            for (int n = 0; n < (int) kindAndCount[i + 1]; n++) {
                list.add(new Signal(new BlockPos(x++, 64, 0), (SignalKind) kindAndCount[i]));
            }
        }
        return list;
    }

    @Test
    void mostFrequentFirstWithPlurals() {
        assertEquals("2 shulker boxes, 1 written sign",
                SignalSummary.top(signals(SignalKind.WRITTEN_SIGN, 1, SignalKind.SHULKER_BOX, 2), 3));
    }

    @Test
    void keepsAtMostTheGivenNumberOfKinds() {
        assertEquals("3 chests, 2 beds, 1 shulker box",
                SignalSummary.top(signals(SignalKind.BED, 2, SignalKind.CHEST, 3, SignalKind.SHULKER_BOX, 1,
                        SignalKind.CRAFTING_TABLE, 1), 3));
    }

    @Test
    void tiesGoToTheHeavierKind() {
        assertEquals("1 shulker box, 1 bed", SignalSummary.top(signals(SignalKind.BED, 1, SignalKind.SHULKER_BOX, 1), 3));
    }

    @Test
    void tracesListOnlyTraceKinds() {
        assertEquals("2 named entities, 1 bed",
                SignalSummary.traces(signals(SignalKind.CHEST, 5, SignalKind.NAMED_ENTITY, 2, SignalKind.BED, 1)));
        assertEquals("", SignalSummary.traces(signals(SignalKind.CHEST, 5)));
    }

    @Test
    void emptyForNoSignals() {
        assertEquals("", SignalSummary.top(List.of(), 3));
    }
}
