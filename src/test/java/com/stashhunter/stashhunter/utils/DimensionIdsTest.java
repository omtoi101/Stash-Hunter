package com.stashhunter.stashhunter.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import meteordevelopment.meteorclient.utils.world.Dimension;
import org.junit.jupiter.api.Test;

class DimensionIdsTest {
    @Test
    void vanillaIdsMapToMeteorDimensions() {
        assertEquals(Dimension.Overworld, DimensionIds.toMeteor("minecraft:overworld"));
        assertEquals(Dimension.Nether, DimensionIds.toMeteor("minecraft:the_nether"));
        assertEquals(Dimension.End, DimensionIds.toMeteor("minecraft:the_end"));
    }

    @Test
    void otherIdsHaveNoMeteorDimension() {
        assertNull(DimensionIds.toMeteor("custom:mining_world"));
        assertNull(DimensionIds.toMeteor("Overworld"));
        assertNull(DimensionIds.toMeteor(null));
    }
}
