package com.stashhunter.stashhunter.baritone;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * Public facade for Stash Hunter's optional Baritone integration.
 *
 * This is the only class outside this package that may be imported for Baritone access - it
 * never mentions a {@code baritone.api.*} type in its own signatures or fields, and every
 * method gates on {@link #isModLoaded()} before touching {@link BaritoneImpl}. That keeps the
 * JVM from ever having to resolve Baritone's classes when the mod isn't installed, so the addon
 * starts and runs normally without it (Baritone is a soft/optional dependency, see fabric.mod.json).
 */
public final class BaritoneBridge {
    private static final String BARITONE_MOD_ID = "baritone-meteor";
    private static Boolean modLoadedCache;

    private BaritoneBridge() {}

    public static boolean isModLoaded() {
        if (modLoadedCache == null) {
            modLoadedCache = FabricLoader.getInstance().isModLoaded(BARITONE_MOD_ID);
        }
        return modLoadedCache;
    }

    /** True only if Baritone is installed and its elytra process (native nether-pathfinder lib) actually loaded. */
    public static boolean isElytraPathingReady() {
        if (!isModLoaded()) return false;
        try {
            return BaritoneImpl.isElytraLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Starts (or redirects) Baritone's elytra pathing toward the given target. Returns false if unavailable. */
    public static boolean startElytraPath(BlockPos target) {
        if (!isElytraPathingReady()) return false;
        try {
            BaritoneImpl.pathElytraTo(target);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Starts Baritone's ground pathfinder toward the exact target block. Returns false if unavailable. */
    public static boolean startGroundPath(BlockPos target) {
        if (!isModLoaded()) return false;
        try {
            BaritoneImpl.pathGroundTo(target);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void cancel() {
        if (!isModLoaded()) return;
        try {
            BaritoneImpl.cancel();
        } catch (Throwable t) {
            // Nothing sensible to do if Baritone itself is in a bad state - ignore.
        }
    }

    public static boolean isPathing() {
        if (!isModLoaded()) return false;
        try {
            return BaritoneImpl.isPathing();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPath() {
        if (!isModLoaded()) return false;
        try {
            return BaritoneImpl.hasPath();
        } catch (Throwable t) {
            return false;
        }
    }
}
