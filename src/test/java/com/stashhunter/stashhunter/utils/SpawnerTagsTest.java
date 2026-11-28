package com.stashhunter.stashhunter.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SpawnerTagsTest {
    @Test
    void nullTagHasNoMob() {
        assertEquals("", SpawnerTags.mobIdOf(null));
    }

    @Test
    void nullTagCountsAsUntouched() {
        assertEquals(SpawnerTags.UNTOUCHED_DELAY, SpawnerTags.delayOf(null));
    }

    @Test
    void mobIdIsReadFromSpawnData() {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        CompoundTag tag = new CompoundTag();
        tag.put("SpawnData", spawnData);
        assertEquals("minecraft:zombie", SpawnerTags.mobIdOf(tag));
    }

    @Test
    void delayIsReadFromTag() {
        CompoundTag tag = new CompoundTag();
        tag.putShort("Delay", (short) 312);
        assertEquals(312, SpawnerTags.delayOf(tag));
    }
}
