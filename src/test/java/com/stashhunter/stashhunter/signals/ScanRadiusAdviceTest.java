package com.stashhunter.stashhunter.signals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScanRadiusAdviceTest {

    @Test
    void adviceWhenRadiusFarBelowViewDistance() {
        var advice = ScanRadiusAdvice.advise(64, 12);
        assertTrue(advice.isPresent());
        assertTrue(advice.get().contains("176"), advice.get());
        assertEquals("The server sends chunks about 192 blocks out; scan-radius 64 could be raised to 176.",
                advice.get());
    }

    @Test
    void noAdviceWhenRadiusFits() {
        assertTrue(ScanRadiusAdvice.advise(160, 12).isEmpty());
    }
}
