package com.stashhunter.stashhunter.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;

/**
 * The only class in this addon allowed to reference {@code baritone.api.*} types.
 *
 * Every method here is only ever invoked by {@link BaritoneBridge} after it has already
 * confirmed (via {@code FabricLoader.isModLoaded}) that Baritone is actually installed, so
 * this class's bytecode is never loaded/verified by the JVM otherwise - keeping Baritone a
 * true optional/soft dependency rather than a hard requirement.
 */
final class BaritoneImpl {
    private BaritoneImpl() {}

    private static IBaritone primary() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    /** True only if the native nether-pathfinder library loaded and elytra pathing is actually usable. */
    static boolean isElytraLoaded() {
        return primary().getElytraProcess().isLoaded();
    }

    static void pathElytraTo(BlockPos target) {
        primary().getElytraProcess().pathTo(target);
    }

    static void pathGroundTo(BlockPos target) {
        primary().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target.getX(), target.getY(), target.getZ()));
    }

    static void cancel() {
        IBaritone baritone = primary();
        baritone.getElytraProcess().resetState();
        baritone.getPathingBehavior().cancelEverything();
    }

    static boolean isPathing() {
        return primary().getPathingBehavior().isPathing();
    }

    static boolean hasPath() {
        return primary().getPathingBehavior().hasPath();
    }
}
