package com.stashhunter.stashhunter.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobGearTest {
    @Test
    void enchantedNetheriteIsPlayerGear() {
        assertTrue(MobGear.isPlayerGear("minecraft:netherite_helmet", true));
    }

    @Test
    void plainDiamondSwordIsNot() {
        assertFalse(MobGear.isPlayerGear("minecraft:diamond_sword", false));
    }

    @Test
    void totemAlwaysCounts() {
        assertTrue(MobGear.isPlayerGear("minecraft:totem_of_undying", false));
    }

    @Test
    void ironArmorNeverCounts() {
        assertFalse(MobGear.isPlayerGear("minecraft:iron_chestplate", true));
    }
}
