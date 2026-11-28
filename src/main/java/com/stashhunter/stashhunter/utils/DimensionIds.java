package com.stashhunter.stashhunter.utils;

import meteordevelopment.meteorclient.utils.world.Dimension;

/**
 * Maps vanilla dimension ids ("minecraft:overworld", ...) to Meteor's Dimension. Meteor 26.3 dropped
 * the Dimension.toString() override that used to return exactly these ids, so they are spelled out.
 */
public final class DimensionIds {
    private DimensionIds() {
    }

    /** Meteor's dimension for a vanilla dimension id, or null for any other (modded/custom) dimension. */
    public static Dimension toMeteor(String id) {
        if (id == null) return null;
        return switch (id) {
            case "minecraft:overworld" -> Dimension.Overworld;
            case "minecraft:the_nether" -> Dimension.Nether;
            case "minecraft:the_end" -> Dimension.End;
            default -> null;
        };
    }
}
