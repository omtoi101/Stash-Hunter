package com.stashhunter.stashhunter.utils;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldScanner {

    /**
     * Calculates the volume of the bounding box that contains all the given blocks
     */
    public static double getBoundingBoxVolume(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return 0;
        }

        // Find the minimum and maximum coordinates
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos block : blocks) {
            if (block == null) continue;

            minX = Math.min(minX, block.getX());
            minY = Math.min(minY, block.getY());
            minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX());
            maxY = Math.max(maxY, block.getY());
            maxZ = Math.max(maxZ, block.getZ());
        }

        // Calculate volume (add 1 to each dimension because we're dealing with discrete blocks)
        double volume = (maxX - minX + 1.0) * (maxY - minY + 1.0) * (maxZ - minZ + 1.0);
        return Math.max(volume, 1.0); // Ensure we never return 0 to avoid division by zero
    }

    /**
     * Counts the occurrences of each block type from the given positions
     */
    public static Map<Block, Integer> countBlocks(List<BlockPos> blockPositions) {
        Map<Block, Integer> counts = new HashMap<>();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || blockPositions == null) {
            return counts;
        }

        for (BlockPos blockPos : blockPositions) {
            if (blockPos == null) continue;

            try {
                Block block = mc.world.getBlockState(blockPos).getBlock();
                if (block != null) {
                    counts.put(block, counts.getOrDefault(block, 0) + 1);
                }
            } catch (Exception e) {
                // Silently ignore errors when accessing block states
                // This can happen if chunks are unloaded or positions are invalid
            }
        }

        return counts;
    }

    /**
     * Calculates the density of valuable blocks in the given area
     */
    public static double calculateDensity(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return 0.0;
        }

        double volume = getBoundingBoxVolume(blocks);
        return blocks.size() / volume;
    }

    /**
     * Gets a human-readable density rating
     */
    public static String getDensityRating(double density) {
        if (density > 0.5) return "Very High Density";
        if (density > 0.2) return "High Density";
        if (density > 0.1) return "Medium Density";
        if (density > 0.05) return "Low Density";
        return "Very Low Density";
    }

    /**
     * Calculates the center point of all given blocks
     */
    public static BlockPos calculateCenter(List<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return BlockPos.ORIGIN;
        }

        long totalX = 0, totalY = 0, totalZ = 0;
        int validBlocks = 0;

        for (BlockPos pos : blocks) {
            if (pos != null) {
                totalX += pos.getX();
                totalY += pos.getY();
                totalZ += pos.getZ();
                validBlocks++;
            }
        }

        if (validBlocks == 0) {
            return BlockPos.ORIGIN;
        }

        return new BlockPos(
            (int) (totalX / validBlocks),
            (int) (totalY / validBlocks),
            (int) (totalZ / validBlocks)
        );
    }

    /**
     * Calculates the maximum distance between any two blocks in the list
     */
    public static double getMaxDistance(List<BlockPos> blocks) {
        if (blocks == null || blocks.size() < 2) {
            return 0.0;
        }

        double maxDistance = 0.0;
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                BlockPos pos1 = blocks.get(i);
                BlockPos pos2 = blocks.get(j);
                if (pos1 != null && pos2 != null) {
                    double distance = Math.sqrt(pos1.getSquaredDistance(pos2));
                    maxDistance = Math.max(maxDistance, distance);
                }
            }
        }

        return maxDistance;
    }

    /**
     * Filters blocks to only include those within a certain radius of a center point
     */
    public static List<BlockPos> filterByRadius(List<BlockPos> blocks, BlockPos center, double radius) {
        if (blocks == null || center == null) {
            return blocks;
        }

        return blocks.stream()
            .filter(pos -> pos != null && center.isWithinDistance(pos, radius))
            .toList();
    }

    /**
     * Creates a summary string of the block counts for Discord reporting
     */
    public static String createBlockSummary(Map<Block, Integer> blockCounts) {
        if (blockCounts == null || blockCounts.isEmpty()) {
            return "No blocks found";
        }

        StringBuilder summary = new StringBuilder();
        blockCounts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())) // Sort by count descending
            .forEach(entry -> {
                String blockName = entry.getKey().getName().getString();
                // Clean up block names for better readability
                blockName = blockName.replace("_", " ");
                blockName = capitalizeWords(blockName);

                summary.append(entry.getValue())
                    .append("x ")
                    .append(blockName)
                    .append("\n");
            });

        return summary.toString().trim();
    }

    /**
     * Helper method to capitalize words in a string
     */
    private static String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }
}
