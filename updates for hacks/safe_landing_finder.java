package com.stashhunter.stashhunter.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class SafeLandingSpotFinder {
    
    // Blocks that are safe to land on
    private static final Set<Block> SAFE_LANDING_BLOCKS = Set.of(
        Blocks.GRASS_BLOCK,
        Blocks.DIRT,
        Blocks.STONE,
        Blocks.COBBLESTONE,
        Blocks.SAND,
        Blocks.GRAVEL,
        Blocks.NETHERRACK,
        Blocks.END_STONE,
        Blocks.OBSIDIAN,
        Blocks.DEEPSLATE,
        Blocks.ANDESITE,
        Blocks.DIORITE,
        Blocks.GRANITE,
        Blocks.SANDSTONE,
        Blocks.RED_SANDSTONE
    );

    // Blocks that are hazardous to land on or near
    private static final Set<Block> HAZARDOUS_BLOCKS = Set.of(
        Blocks.LAVA,
        Blocks.WATER,
        Blocks.CACTUS,
        Blocks.SWEET_BERRY_BUSH,
        Blocks.WITHER_ROSE,
        Blocks.FIRE,
        Blocks.SOUL_FIRE,
        Blocks.MAGMA_BLOCK,
        Blocks.CAMPFIRE,
        Blocks.SOUL_CAMPFIRE
    );

    // Blocks that would cause head bumping when taking off
    private static final Set<Block> OVERHEAD_HAZARDS = Set.of(
        Blocks.STONE,
        Blocks.COBBLESTONE,
        Blocks.DEEPSLATE,
        Blocks.ANDESITE,
        Blocks.DIORITE,
        Blocks.GRANITE,
        Blocks.OBSIDIAN,
        Blocks.BEDROCK,
        Blocks.NETHERRACK,
        Blocks.END_STONE,
        Blocks.SANDSTONE,
        Blocks.RED_SANDSTONE,
        Blocks.DIRT,
        Blocks.GRASS_BLOCK
    );

    /**
     * Finds a safe landing spot within the specified radius
     * @param startPos Current player position
     * @param radius Search radius in blocks
     * @param world The world to search in
     * @return A safe landing position, or null if none found
     */
    public static BlockPos findSafeLandingSpot(Vec3d startPos, int radius, ClientWorld world) {
        if (world == null) return null;

        BlockPos centerPos = BlockPos.ofFloored(startPos);
        
        // Start from current position and spiral outward
        for (int r = 0; r <= radius; r += 8) { // Check every 8 blocks for performance
            for (int x = -r; x <= r; x += 8) {
                for (int z = -r; z <= r; z += 8) {
                    // Only check positions roughly on the circle edge for this radius
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance < r - 4 || distance > r + 4) continue;

                    BlockPos checkPos = centerPos.add(x, 0, z);
                    BlockPos safeSpot = findSafeLandingAtColumn(checkPos, world);
                    
                    if (safeSpot != null) {
                        return safeSpot;
                    }
                }
            }
        }

        return null; // No safe spot found
    }

    /**
     * Finds a safe landing spot in a vertical column
     * @param columnPos The X,Z position to check vertically
     * @param world The world to search in
     * @return A safe landing position in this column, or null if none found
     */
    private static BlockPos findSafeLandingAtColumn(BlockPos columnPos, ClientWorld world) {
        // Start from a reasonable height and work down
        int maxY = Math.min(world.getHeight() - 1, columnPos.getY() + 50);
        int minY = Math.max(world.getBottomY(), columnPos.getY() - 100);

        for (int y = maxY; y >= minY; y--) {
            BlockPos landingPos = new BlockPos(columnPos.getX(), y, columnPos.getZ());
            
            if (isSafeLandingSpot(landingPos, world)) {
                return landingPos;
            }
        }

        return null;
    }

    /**
     * Checks if a specific position is safe for landing
     * @param pos The position to check
     * @param world The world context
     * @return true if safe to land here
     */
    public static boolean isSafeLandingSpot(BlockPos pos, ClientWorld world) {
        try {
            // Check the landing block itself
            BlockState landingBlock = world.getBlockState(pos);
            if (!SAFE_LANDING_BLOCKS.contains(landingBlock.getBlock()) || !landingBlock.isFullCube(world, pos)) {
                return false;
            }

            // Check that the two blocks above are air (space for player)
            BlockPos abovePos1 = pos.up();
            BlockPos abovePos2 = pos.up(2);
            
            if (!world.getBlockState(abovePos1).isAir() || !world.getBlockState(abovePos2).isAir()) {
                return false;
            }

            // Check for overhead hazards that would block takeoff (up to 10 blocks above)
            for (int i = 3; i <= 10; i++) {
                BlockPos overheadPos = pos.up(i);
                BlockState overheadBlock = world.getBlockState(overheadPos);
                
                if (OVERHEAD_HAZARDS.contains(overheadBlock.getBlock()) && overheadBlock.isFullCube(world, overheadPos)) {
                    return false; // Would hit head during takeoff
                }
            }

            // Check surrounding area for hazards (3x3 area)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos surroundingPos = pos.add(dx, 0, dz);
                    BlockState surroundingBlock = world.getBlockState(surroundingPos);
                    
                    if (HAZARDOUS_BLOCKS.contains(surroundingBlock.getBlock())) {
                        return false;
                    }

                    // Also check one block above surrounding area for hanging hazards
                    BlockPos aboveSurrounding = surroundingPos.up();
                    BlockState aboveSurroundingBlock = world.getBlockState(aboveSurrounding);
                    
                    if (HAZARDOUS_BLOCKS.contains(aboveSurroundingBlock.getBlock())) {
                        return false;
                    }
                }
            }

            // Check that we're not landing in a confined space (check 5x5 area for walls)
            int wallCount = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip center position
                    
                    BlockPos wallCheckPos = pos.add(dx, 1, dz); // Check at player height
                    BlockState wallBlock = world.getBlockState(wallCheckPos);
                    
                    if (!wallBlock.isAir() && wallBlock.isFullCube(world, wallCheckPos)) {
                        wallCount++;
                    }
                }
            }

            // If more than 60% of surrounding area is walled, it's too confined
            if (wallCount > 15) { // 60% of 24 surrounding blocks
                return false;
            }

            // Additional safety checks for specific biomes/situations
            
            // Check if we're above void (in End or below bedrock)
            if (pos.getY() < world.getBottomY() + 5) {
                return false;
            }

            // Check if landing spot has solid ground beneath (not floating)
            boolean hasGroundSupport = false;
            for (int i = 1; i <= 5; i++) {
                BlockPos belowPos = pos.down(i);
                BlockState belowBlock = world.getBlockState(belowPos);
                
                if (!belowBlock.isAir() && belowBlock.isFullCube(world, belowPos)) {
                    hasGroundSupport = true;
                    break;
                }
            }

            if (!hasGroundSupport) {
                return false; // Floating platform, not safe
            }

            return true; // All checks passed

        } catch (Exception e) {
            // If we can't check the blocks safely, assume it's not safe
            return false;
        }
    }

    /**
     * Evaluates the safety score of a landing spot (higher is better)
     * @param pos The position to evaluate
     * @param world The world context
     * @return Safety score from 0-100, or -1 if not safe at all
     */
    public static int evaluateLandingSpotSafety(BlockPos pos, ClientWorld world) {
        if (!isSafeLandingSpot(pos, world)) {
            return -1;
        }

        int safetyScore = 50; // Base score for any safe spot

        try {
            // Bonus points for being on natural ground blocks
            BlockState landingBlock = world.getBlockState(pos);
            if (landingBlock.getBlock() == Blocks.GRASS_BLOCK || 
                landingBlock.getBlock() == Blocks.STONE ||
                landingBlock.getBlock() == Blocks.DIRT) {
                safetyScore += 10;
            }

            // Bonus points for having more open space above
            int openSpaceAbove = 0;
            for (int i = 3; i <= 15; i++) {
                BlockPos abovePos = pos.up(i);
                if (world.getBlockState(abovePos).isAir()) {
                    openSpaceAbove++;
                } else {
                    break;
                }
            }
            safetyScore += Math.min(openSpaceAbove * 2, 20); // Up to 20 bonus points

            // Bonus points for having more open space around
            int openSpaceAround = 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    
                    BlockPos aroundPos = pos.add(dx, 1, dz);
                    if (world.getBlockState(aroundPos).isAir()) {
                        openSpaceAround++;
                    }
                }
            }
            safetyScore += Math.min(openSpaceAround, 15); // Up to 15 bonus points

            // Penalty for being too high above ground
            int distanceToGround = 0;
            for (int i = 1; i <= 50; i++) {
                BlockPos belowPos = pos.down(i);
                BlockState belowBlock = world.getBlockState(belowPos);
                
                if (!belowBlock.isAir()) {
                    distanceToGround = i - 1;
                    break;
                }
            }
            
            if (distanceToGround > 10) {
                safetyScore -= (distanceToGround - 10) * 2; // Penalty for being too high
            }

            return Math.max(0, Math.min(100, safetyScore));

        } catch (Exception e) {
            return 50; // Return base score if evaluation fails
        }
    }

    /**
     * Finds the best landing spot within radius, considering safety scores
     * @param startPos Current player position  
     * @param radius Search radius in blocks
     * @param world The world to search in
     * @return The best landing position found, or null if none found
     */
    public static BlockPos findBestLandingSpot(Vec3d startPos, int radius, ClientWorld world) {
        BlockPos bestSpot = null;
        int bestScore = -1;

        BlockPos centerPos = BlockPos.ofFloored(startPos);
        
        // Search in expanding rings for performance
        for (int r = 8; r <= radius; r += 8) {
            for (int x = -r; x <= r; x += 4) {
                for (int z = -r; z <= r; z += 4) {
                    // Only check positions roughly on the circle edge for this radius
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance < r - 4 || distance > r + 4) continue;

                    BlockPos checkPos = centerPos.add(x, 0, z);
                    BlockPos candidate = findSafeLandingAtColumn(checkPos, world);
                    
                    if (candidate != null) {
                        int score = evaluateLandingSpotSafety(candidate, world);
                        if (score > bestScore) {
                            bestScore = score;
                            bestSpot = candidate;
                        }
                        
                        // If we found a really good spot, use it
                        if (score >= 80) {
                            return candidate;
                        }
                    }
                }
            }
            
            // If we found any decent spot in this ring, don't search further
            if (bestScore >= 60) {
                break;
            }
        }

        return bestSpot;
    }

    /**
     * Quick check if the immediate area below is safe for emergency landing
     * @param pos Current position
     * @param world The world context
     * @return true if it's safe to descend here immediately
     */
    public static boolean isEmergencyLandingSafe(Vec3d pos, ClientWorld world) {
        BlockPos checkPos = BlockPos.ofFloored(pos);
        
        // Look for ground within reasonable distance below
        for (int i = 0; i < 50; i++) {
            BlockPos groundPos = checkPos.down(i);
            if (isSafeLandingSpot(groundPos, world)) {
                return true;
            }
            
            // If we hit a hazardous block, stop checking
            BlockState blockState = world.getBlockState(groundPos);
            if (HAZARDOUS_BLOCKS.contains(blockState.getBlock())) {
                return false;
            }
        }
        
        return false;
    }
}
                    