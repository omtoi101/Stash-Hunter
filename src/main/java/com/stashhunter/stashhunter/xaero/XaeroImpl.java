package com.stashhunter.stashhunter.xaero;

import com.stashhunter.stashhunter.StashHunter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.io.IOException;

/**
 * The only class in this addon allowed to reference {@code xaero.*} types. Only ever called by
 * {@link XaeroBridge} after it confirmed that Xaero's Minimap is installed.
 *
 * Checked against xaerominimap fabric-26.2-26.6.2; Xaero's own death waypoints take the same
 * route (current waypoint set, add, save the world through the session's IO).
 */
final class XaeroImpl {
    private XaeroImpl() {}

    private static final String SYMBOL = "S";

    /** Where a waypoint went, so it can be taken out of the same set again. */
    private record Added(MinimapSession session, MinimapWorld world, WaypointSet set, Waypoint waypoint) {}

    static Object addWaypoint(BlockPos pos, String name) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return null; // no world joined yet
        MinimapWorld world = session.getWorldManager().getCurrentWorld();
        ClientLevel level = Minecraft.getInstance().level;
        if (world == null || level == null) return null;
        // The current world follows the player's dimension. If Xaero still points at another one
        // (right after a portal), the coordinates would need that dimension's scale: skip instead.
        if (world.getDimId() != null && !world.getDimId().equals(level.dimension())) return null;
        WaypointSet set = world.getCurrentWaypointSet();
        if (set == null) return null;

        Waypoint waypoint = new Waypoint(pos.getX(), pos.getY(), pos.getZ(), name, SYMBOL,
            WaypointColor.GOLD, WaypointPurpose.NORMAL);
        set.add(waypoint);
        save(session, world);
        return new Added(session, world, set, waypoint);
    }

    static void removeWaypoint(Object handle) {
        Added added = (Added) handle;
        // After leaving the server the session is closed; its files belong to Xaero again.
        if (BuiltInHudModules.MINIMAP.getCurrentSession() != added.session()) return;
        added.set().remove(added.waypoint());
        save(added.session(), added.world());
    }

    // A failed save keeps the waypoint in memory; Xaero writes it with its next save of this world.
    private static void save(MinimapSession session, MinimapWorld world) {
        try {
            session.getWorldManagerIO().saveWorld(world);
        } catch (IOException e) {
            StashHunter.LOG.warn("Could not save Xaero waypoints", e);
        }
    }
}
