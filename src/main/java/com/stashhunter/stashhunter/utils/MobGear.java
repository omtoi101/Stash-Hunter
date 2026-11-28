package com.stashhunter.stashhunter.utils;

import java.util.List;
import java.util.Set;

/**
 * Decides whether an item a mob carries looks like gear a player lost or handed over (mobs pick up dropped items).
 * Pure string logic on the item's registry id, so it can be tested without a Minecraft bootstrap.
 */
public final class MobGear {
    private static final Set<String> ALWAYS = Set.of(
        "minecraft:elytra", "minecraft:totem_of_undying", "minecraft:enchanted_golden_apple");
    private static final List<String> TIER_PREFIXES = List.of("minecraft:diamond_", "minecraft:netherite_");
    private static final List<String> GEAR_SUFFIXES = List.of(
        "_helmet", "_chestplate", "_leggings", "_boots",
        "_sword", "_axe", "_pickaxe", "_shovel", "_hoe", "_spear");

    private MobGear() {}

    public static boolean isPlayerGear(String itemId, boolean enchanted) {
        if (ALWAYS.contains(itemId) || itemId.endsWith("shulker_box")) return true;
        if (!enchanted) return false;
        // Diamond and netherite armor, weapons and tools count only when enchanted: plain diamond pieces also
        // come from mob spawns and loot, enchanted ones are far more likely a player's.
        return TIER_PREFIXES.stream().anyMatch(itemId::startsWith)
            && GEAR_SUFFIXES.stream().anyMatch(itemId::endsWith);
    }
}
