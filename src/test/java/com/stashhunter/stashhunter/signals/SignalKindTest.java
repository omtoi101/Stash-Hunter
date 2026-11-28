package com.stashhunter.stashhunter.signals;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SignalKindTest {
    @Test
    void strongKindAloneReachesHintInNormalAndSensitive() {
        for (Sensitivity s : List.of(Sensitivity.NORMAL, Sensitivity.SENSITIVE))
            for (SignalKind k : SignalKind.values())
                if (k.category() == SignalCategory.STRONG) assertTrue(k.weight() >= s.hintScore());
    }

    @Test
    void noSingleTraceReachesHintInNormal() {
        for (SignalKind k : SignalKind.values())
            if (k.category() == SignalCategory.TRACE || k.category() == SignalCategory.WEAK_TRACE)
                assertTrue(k.weight() < Sensitivity.NORMAL.hintScore(), k.name());
    }

    @Test
    void containerThresholdCapsTheSetting() {
        assertEquals(10, Sensitivity.CLASSIC.containerThreshold(10));
        assertEquals(6, Sensitivity.NORMAL.containerThreshold(10));
        assertEquals(4, Sensitivity.NORMAL.containerThreshold(4));
        assertEquals(4, Sensitivity.SENSITIVE.containerThreshold(10));
    }

    @Test
    void classicHasNoHints() {
        assertFalse(Sensitivity.CLASSIC.hints());
    }

    @Test
    void containersAndStrongKindsHaveNoTraceGroup() {
        for (SignalKind k : SignalKind.values())
            if (k.category() == SignalCategory.STRONG || k.category() == SignalCategory.CONTAINER)
                assertEquals(TraceGroup.NONE, k.group());
    }
}
