package com.stashhunter.stashhunter.utils;

import net.minecraft.nbt.CompoundTag;

/**
 * Reads the fields activated-spawner-detector needs from a spawner's block-entity tag. The chunk
 * packet can carry a spawner without a tag; that counts as an empty tag (no mob, untouched delay).
 */
public final class SpawnerTags {
    /** A vanilla spawner starts with Delay 20 and only changes it once a player has been in range. */
    public static final int UNTOUCHED_DELAY = 20;

    private static final CompoundTag EMPTY = new CompoundTag();

    private SpawnerTags() {
    }

    /** The spawned entity id ("minecraft:zombie"), or "" if the tag names none. */
    public static String mobIdOf(CompoundTag tag) {
        CompoundTag t = tag != null ? tag : EMPTY;
        return t.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getStringOr("id", "");
    }

    /** The spawn delay in ticks; {@link #UNTOUCHED_DELAY} if the tag has none. */
    public static int delayOf(CompoundTag tag) {
        CompoundTag t = tag != null ? tag : EMPTY;
        return t.getShortOr("Delay", (short) UNTOUCHED_DELAY);
    }
}
