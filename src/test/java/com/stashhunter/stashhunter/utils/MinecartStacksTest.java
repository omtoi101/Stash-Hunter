package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MinecartStacksTest {
    @Test
    void cartsOnTheSameSpotFormAStack() {
        MinecartStacks stacks = new MinecartStacks(0.5);
        for (int i = 0; i < 5; i++) stacks.add(10.5, 64, -3.5);
        stacks.add(20.5, 64, -3.5);

        List<MinecartStacks.Stack> result = stacks.stacks(2);
        assertEquals(1, result.size());
        assertEquals(5, result.get(0).count());
        assertEquals(10.5, result.get(0).x());
    }

    @Test
    void radiusDecidesWhatCountsAsStacked() {
        MinecartStacks tight = new MinecartStacks(0.5);
        tight.add(0, 0, 0);
        tight.add(0.9, 0, 0);
        assertTrue(tight.stacks(2).isEmpty());

        MinecartStacks loose = new MinecartStacks(1.0);
        loose.add(0, 0, 0);
        loose.add(0.9, 0, 0);
        assertEquals(1, loose.stacks(2).size());
    }

    @Test
    void zeroRadiusNeedsTheExactSpot() {
        MinecartStacks stacks = new MinecartStacks(0);
        stacks.add(1, 2, 3);
        stacks.add(1, 2, 3);
        stacks.add(1, 2, 3.01);
        assertEquals(2, stacks.stacks(2).get(0).count());
    }

    @Test
    void worksAcrossCellBordersAndNegativeCoordinates() {
        MinecartStacks stacks = new MinecartStacks(0.5);
        stacks.add(-0.1, 64, -0.1);
        stacks.add(0.1, 64, 0.1);
        assertEquals(1, stacks.stacks(2).size());
    }

    @Test
    void minSizeFilters() {
        MinecartStacks stacks = new MinecartStacks(0.5);
        stacks.add(0, 0, 0);
        stacks.add(0, 0, 0);
        assertEquals(1, stacks.stacks(2).size());
        assertTrue(stacks.stacks(3).isEmpty());
    }
}
