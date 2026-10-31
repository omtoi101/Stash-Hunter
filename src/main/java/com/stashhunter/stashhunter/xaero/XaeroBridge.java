package com.stashhunter.stashhunter.xaero;

import com.stashhunter.stashhunter.StashHunter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * Public facade for Stash Hunter's optional Xaero's Minimap integration.
 *
 * Like BaritoneBridge, it never mentions a {@code xaero.*} type in its own signatures or fields,
 * and every method gates on {@link #isModLoaded()} before touching {@link XaeroImpl}. So the JVM
 * never has to resolve Xaero's classes when the mod isn't installed. Waypoint handles are plain
 * Objects for the same reason.
 */
public final class XaeroBridge {
    private static final String XAERO_MINIMAP_MOD_ID = "xaerominimap";
    private static Boolean modLoadedCache;
    // Set when Xaero's API doesn't match what this addon was compiled against (another Xaero
    // version): warn once, then leave Xaero alone instead of failing on every stash.
    private static boolean incompatible;

    private XaeroBridge() {}

    public static boolean isModLoaded() {
        if (modLoadedCache == null) {
            modLoadedCache = FabricLoader.getInstance().isModLoaded(XAERO_MINIMAP_MOD_ID);
        }
        return modLoadedCache;
    }

    /**
     * Adds a waypoint to Xaero's current waypoint set (current server and dimension) and saves it.
     * Returns a handle for {@link #removeWaypoint}, or null if Xaero isn't installed or has no
     * world open.
     */
    public static Object addWaypoint(BlockPos pos, String name) {
        if (!isModLoaded() || incompatible) return null;
        try {
            return XaeroImpl.addWaypoint(pos, name);
        } catch (LinkageError e) {
            markIncompatible(e);
        } catch (RuntimeException e) {
            StashHunter.LOG.warn("Could not add Xaero waypoint {}", name, e);
        }
        return null;
    }

    /** Removes a waypoint added by {@link #addWaypoint}; does nothing if its Xaero session is over. */
    public static void removeWaypoint(Object handle) {
        if (handle == null || !isModLoaded() || incompatible) return;
        try {
            XaeroImpl.removeWaypoint(handle);
        } catch (LinkageError e) {
            markIncompatible(e);
        } catch (RuntimeException e) {
            StashHunter.LOG.warn("Could not remove Xaero waypoint", e);
        }
    }

    private static void markIncompatible(LinkageError e) {
        incompatible = true;
        StashHunter.LOG.warn("This Xaero's Minimap version is not supported; stash waypoints stay Meteor-only", e);
    }
}
