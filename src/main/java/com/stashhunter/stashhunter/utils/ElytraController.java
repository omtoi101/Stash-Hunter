package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.modules.NewerNewChunks;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class ElytraController {
    private static List<Vec3> waypoints = new ArrayList<>();
    private static int currentWaypoint = 0;
    private static boolean active = false;
    private static boolean justCompleted = false;
    private static Vec3 currentTarget = null;

    // Set to the target most recently handed to Baritone's elytra pathing, so driveTowards()
    // can tell "no path issued yet for this target" apart from "Baritone dropped its path".
    private static BlockPos baritoneTargetPos = null;

    // True only if initiateFlight() switched ElytraFly on, so stop() leaves a user-enabled ElytraFly alone.
    private static boolean elytraFlyEnabledByUs = false;

    // Saved so a flight can leave the user's ElytraFly auto-takeoff setting exactly as it found it.
    private static boolean originalAutoTakeOffState = false;
    private static boolean autoTakeOffSaved = false;

    // Baritone's elytraAutoJump is turned on for the flight so the elytra process can walk to a
    // jump spot and take off from flat ground; the user's value is restored in stop().
    private static boolean originalElytraAutoJump = false;
    private static boolean elytraAutoJumpSaved = false;

    // True while the built-in controller holds the forward key (the fallback when Baritone is
    // unavailable). Refcounted via KeyHold so it never steals the key from the player or another module.
    private static boolean forwardHeldByUs = false;

    // True while Baritone's elytra process lands the player after a completed scan (opt-in,
    // Config.landAfterCompletion). Cleared once Baritone lets go or anything stops/starts a flight.
    private static boolean landingAfterCompletion = false;

    // Navigation state for edge following
    private static NavigationMode navigationMode = NavigationMode.NORMAL;
    private static long edgeFollowStartTime = 0;
    private static final long MAX_EDGE_FOLLOW_TIME = 30000; // 30 seconds max edge following
    private static Set<ChunkPos> visitedChunks = new HashSet<>();
    private static Vec3 boundaryDirection = null;

    // Trail analysis state
    private static final double MIN_TRAIL_CONFIDENCE = 0.6; // minimum confidence to follow a trail

    private enum NavigationMode {
        NORMAL,           // Following waypoints normally
        EDGE_FOLLOWING,   // Following chunk boundary
        TRAIL_FOLLOWING,  // Following new chunk trail
        CLIMBING          // Climbing to target altitude
    }

    public static void start(int x1, int z1, int x2, int z2, int stripWidth) {
        // Checked before any state changes, so a bad call can't leave an active flight without waypoints.
        if (stripWidth <= 0) throw new IllegalArgumentException("stripWidth must be positive, got " + stripWidth);
        // Always generate grid-based waypoints for systematic exploration
        // The bot will dynamically avoid new chunks during flight
        startWith(GridWaypoints.generate(x1, z1, x2, z2, stripWidth, Config.flightAltitude));
    }

    public static void startSpiral(int centerX, int centerZ, int radius, int gap) {
        // Generated before any state changes, so bad arguments throw without leaving an active empty flight.
        startWith(SpiralWaypoints.generate(centerX, centerZ, radius, gap, Config.flightAltitude));
    }

    // Trips store the plain waypoint list, so pause/resume works the same for every search pattern.
    private static void startWith(List<Vec3> generated) {
        waypoints.clear();
        currentWaypoint = 0;
        active = true;
        justCompleted = false;
        landingAfterCompletion = false;
        navigationMode = NavigationMode.NORMAL;
        visitedChunks.clear();
        boundaryDirection = null;
        baritoneTargetPos = null;

        waypoints.addAll(generated);
        TripManager.addTrip(waypoints, currentWaypoint);

        initiateFlight();
    }

    public static void stop() {
        stop(true);
    }

    // cancelBaritone=false keeps Baritone's elytra process running so it can finish the landing.
    private static void stop(boolean cancelBaritone) {
        active = false;
        if (cancelBaritone) landingAfterCompletion = false;
        currentTarget = null;
        navigationMode = NavigationMode.NORMAL;
        visitedChunks.clear();
        boundaryDirection = null;
        baritoneTargetPos = null;
        if (cancelBaritone) BaritoneBridge.cancel();

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFlyEnabledByUs && elytraFly != null && elytraFly.isActive()) {
            elytraFly.toggle();
        }
        elytraFlyEnabledByUs = false;
        if (elytraFly != null && autoTakeOffSaved) {
            elytraFly.autoTakeOff.set(originalAutoTakeOffState);
            autoTakeOffSaved = false;
        }
        if (elytraAutoJumpSaved) {
            BaritoneBridge.setElytraAutoJump(originalElytraAutoJump);
            elytraAutoJumpSaved = false;
        }
        setForwardHeld(false);

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aStash hunter flight stopped"));
        }
    }

    public static void pause() {
        if (!active) return;
        TripManager.addTrip(waypoints, currentWaypoint);
        stop();
    }

    public static void resume() {
        if (active) return;
        TripManager.TripData tripData = TripManager.getLatestTrip();
        if (tripData != null) {
            resume(tripData);
        }
    }

    public static void resume(long timestamp) {
        if (active) return;
        TripManager.TripData tripData = TripManager.getTrip(timestamp);
        if (tripData != null) {
            resume(tripData);
        }
    }

    private static void resume(TripManager.TripData tripData) {
        waypoints = tripData.waypoints;
        currentWaypoint = tripData.currentWaypoint;
        active = true;
        justCompleted = false;
        landingAfterCompletion = false;
        navigationMode = NavigationMode.NORMAL;
        baritoneTargetPos = null;

        initiateFlight();
    }

    /**
     * Lets Baritone's elytra process land the player: once its path to the destination is
     * complete it searches a safe landing spot (solid, flat, open sky) nearby and lands there by
     * itself. So we only need the elytra path to the last waypoint running and must not cancel it.
     */
    private static boolean handOffLandingToBaritone() {
        if (!Config.useBaritonePathing || !BaritoneBridge.isElytraPathingReady()) return false;
        if (MeteorClient.mc.player == null || !MeteorClient.mc.player.isFallFlying() || MeteorClient.mc.player.onGround()) return false; // nothing to land
        Vec3 last = waypoints.get(waypoints.size() - 1);
        BlockPos lastPos = BlockPos.containing(last.x, last.y, last.z);
        if (lastPos.equals(baritoneTargetPos) && BaritoneBridge.isElytraActive()) return true;
        return BaritoneBridge.startElytraPath(lastPos);
    }

    private static void initiateFlight() {
        if (!waypoints.isEmpty()) {
            if (MeteorClient.mc.player != null &&
                MeteorClient.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cPlease equip an Elytra before starting!"));
                stop();
                return;
            }

            // Meteor's ElytraFly is only used by the built-in fallback controller. When Baritone's
            // elytra process drives the flight, leaving ElytraFly off avoids two flight controllers
            // fighting over the player, which caused rubber-banding and "stuck" flights/landings.
            boolean useBaritone = useBaritone();
            if (useBaritone && MeteorClient.mc.player != null && !hasFireworkRockets()) {
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eWarning: no firework rockets in inventory - Baritone's elytra flight needs rockets to take off and climb."));
            }
            ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
            if (useBaritone && elytraFly != null && elytraFly.isActive() && MeteorClient.mc.player != null) {
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eWarning: ElytraFly is enabled and fights Baritone's elytra flight - turn it off (and ElytraBoost) for reliable flight."));
            }
            if (!useBaritone && elytraFly != null && !elytraFly.isActive()) {
                elytraFly.toggle();
                elytraFlyEnabledByUs = true;
            }
            // Launch from the ground (fallback only): ElytraFly's auto-takeoff jumps + deploys the
            // elytra. Save the user's setting once so stop() can restore it.
            if (!useBaritone && elytraFly != null && !autoTakeOffSaved) {
                originalAutoTakeOffState = elytraFly.autoTakeOff.get();
                autoTakeOffSaved = true;
                elytraFly.autoTakeOff.set(true);
            }
            // Baritone's elytra process only takes off from flat ground when elytraAutoJump is on;
            // without it the player just stands there. Save + enable it, restore it in stop().
            if (useBaritone && !elytraAutoJumpSaved) {
                originalElytraAutoJump = BaritoneBridge.getElytraAutoJump();
                elytraAutoJumpSaved = true;
                BaritoneBridge.setElytraAutoJump(true);
            }

            flyToNextWaypoint();
        }
    }

    public static void onTick() {
        if (landingAfterCompletion && !BaritoneBridge.isElytraActive()) {
            landingAfterCompletion = false; // Baritone landed ("Done"), gave up, or was cancelled elsewhere
        }

        if (!active || MeteorClient.mc.player == null || waypoints.isEmpty()) {
            return;
        }

        Vec3 playerPos = MeteorClient.mc.player.position();
        ChunkPos currentChunkPos = new ChunkPos((int)playerPos.x >> 4, (int)playerPos.z >> 4);
        visitedChunks.add(currentChunkPos);

        // Check if we've completed all waypoints
        if (currentWaypoint >= waypoints.size() && navigationMode == NavigationMode.NORMAL) {
            if (MeteorClient.mc.player != null) {
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aCompleted scanning area!"));
            }
            justCompleted = true;
            if (Config.landAfterCompletion && handOffLandingToBaritone()) {
                stop(false);
                landingAfterCompletion = true;
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aBaritone is landing at the end of the area..."));
            } else {
                if (Config.landAfterCompletion && MeteorClient.mc.player != null) {
                    if (MeteorClient.mc.player.onGround()) {
                        MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aAlready on the ground - nothing to land."));
                    } else if (MeteorClient.mc.player.isFallFlying()) {
                        // No built-in landing: an untested descent could crash into terrain at glide speed.
                        MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eLand after completion needs Baritone elytra pathing - staying airborne."));
                    }
                }
                stop();
            }
            return;
        }

        // Handle different navigation modes
        switch (navigationMode) {
            case NORMAL:
                handleNormalNavigation();
                break;
            case EDGE_FOLLOWING:
                handleEdgeFollowing();
                break;
            case TRAIL_FOLLOWING:
                handleTrailFollowing();
                break;
            case CLIMBING:
                handleClimbing();
                break;
        }
    }

    public static void climbToAltitude() {
        if (MeteorClient.mc.player == null) return;
        navigationMode = NavigationMode.CLIMBING;
        Vec3 playerPos = MeteorClient.mc.player.position();
        currentTarget = new Vec3(playerPos.x, Config.flightAltitude, playerPos.z);
        StashHunter.LOG.info("Climbing to altitude: " + Config.flightAltitude);
    }

    private static void handleClimbing() {
        if (MeteorClient.mc.player == null) return;
        if (MeteorClient.mc.player.position().y >= Config.flightAltitude - 2) {
            navigationMode = NavigationMode.NORMAL;
            StashHunter.LOG.info("Reached target altitude, resuming normal navigation.");
        } else {
            driveTowards(currentTarget);
        }
    }

    private static void handleNormalNavigation() {
        if (currentWaypoint >= waypoints.size()) return;

        Vec3 playerPos = MeteorClient.mc.player.position();
        Vec3 target = waypoints.get(currentWaypoint);
        currentTarget = target;

        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;

        if (dx * dx + dz * dz < 400.0) { // 20², equivalent to horizontalDistance < 20.0
            currentWaypoint++;
            if (currentWaypoint < waypoints.size()) {
                flyToNextWaypoint();
            }
            return;
        }

        // Check for obstacles and boundaries
        ChunkBoundaryInfo boundaryInfo = checkForBoundaries(playerPos);

        NewerNewChunks newerNewChunks = newerNewChunks();
        if (boundaryInfo.hasNewChunks && newerNewChunks != null && newerNewChunks.dynamicTrailDetection.get()) {
            // Found new chunks - switch to trail following mode
            switchToTrailFollowing(boundaryInfo);
        } else if (boundaryInfo.hasUnloadedArea) {
            // Hit unloaded area - switch to edge following mode
            switchToEdgeFollowing(boundaryInfo);
        } else {
            // Normal flight
            driveTowards(target);
        }
    }

    private static void handleEdgeFollowing() {
        Vec3 playerPos = MeteorClient.mc.player.position();

        // Check if we've been edge following too long
        if (System.currentTimeMillis() - edgeFollowStartTime > MAX_EDGE_FOLLOW_TIME) {
            // Return to normal navigation or try the next waypoint
            navigationMode = NavigationMode.NORMAL;
            if (currentWaypoint < waypoints.size() - 1) {
                currentWaypoint++;
                flyToNextWaypoint();
            }
            return;
        }

        // Follow the edge by maintaining direction parallel to the boundary
        if (boundaryDirection != null) {
            Vec3 edgeTarget = playerPos.add(boundaryDirection.scale(50)); // Look 50 blocks ahead along edge
            currentTarget = new Vec3(edgeTarget.x, Config.flightAltitude, edgeTarget.z);
            driveTowards(currentTarget);

            // Check if we can return to normal navigation
            ChunkBoundaryInfo boundaryInfo = checkForBoundaries(playerPos);
            if (!boundaryInfo.hasUnloadedArea) {
                // Boundary cleared, return to normal navigation
                navigationMode = NavigationMode.NORMAL;
                StashHunter.LOG.info("Edge following complete, returning to normal navigation");
            }
        }
    }

    private static void handleTrailFollowing() {
        Vec3 playerPos = MeteorClient.mc.player.position();

        // Similar to edge following but specifically for new chunk boundaries
        if (System.currentTimeMillis() - edgeFollowStartTime > MAX_EDGE_FOLLOW_TIME) {
            navigationMode = NavigationMode.NORMAL;
            return;
        }

        if (boundaryDirection != null) {
            Vec3 trailTarget = playerPos.add(boundaryDirection.scale(50));
            currentTarget = new Vec3(trailTarget.x, Config.flightAltitude, trailTarget.z);
            driveTowards(currentTarget);

            // Continue following the trail of new chunks
            ChunkBoundaryInfo boundaryInfo = checkForBoundaries(playerPos);
            if (!boundaryInfo.hasNewChunks) {
                // Trail ended, return to normal navigation
                navigationMode = NavigationMode.NORMAL;
                StashHunter.LOG.info("New chunk trail ended, returning to normal navigation");
            }
        }
    }

    private static class ChunkBoundaryInfo {
        boolean hasNewChunks = false;
        boolean hasUnloadedArea = false;
        Vec3 boundaryDirection = null;
        String description = "";
    }

    private static ChunkBoundaryInfo checkForBoundaries(Vec3 playerPos) {
        ChunkBoundaryInfo info = new ChunkBoundaryInfo();

        NewerNewChunks newerNewChunks = newerNewChunks();
        if (newerNewChunks == null || !newerNewChunks.isActive()) {
            return info;
        }

        Vec3 forwardVec = Vec3.directionFromRotation(0, MeteorClient.mc.player.getYRot()).normalize();

        // First, analyze if there's a meaningful trail in the area
        TrailAnalysis.Trail trailInfo = TrailAnalysis.analyze(playerPos, newerNewChunks::isNewChunk);
        if (trailInfo != null && trailInfo.confidence >= MIN_TRAIL_CONFIDENCE) {
            info.hasNewChunks = true;
            info.boundaryDirection = trailInfo.direction;
            info.description = String.format("Detected %s trail (confidence: %.1f)",
                trailInfo.type, trailInfo.confidence);
            return info; // Priority: follow meaningful trails
        }

        // If no good trail, check for immediate obstacles to avoid
        for (int distance = 16; distance <= 64; distance += 16) {
            Vec3 checkPos = playerPos.add(forwardVec.scale(distance));
            ChunkPos checkChunk = new ChunkPos((int)checkPos.x >> 4, (int)checkPos.z >> 4);

            // Check if chunk is new (AVOID these unless they form a trail)
            if (newerNewChunks.isNewChunk(checkChunk)) {
                info.hasNewChunks = true;
                info.description = "New chunk ahead - avoiding (no clear trail detected)";

                // Calculate avoidance direction (perpendicular to forward)
                info.boundaryDirection = new Vec3(-forwardVec.z, 0, forwardVec.x);
                break;
            }

            // Check if chunk is unloaded
            if (MeteorClient.mc.level != null) {
                boolean isLoaded = MeteorClient.mc.level.hasChunk(checkChunk.x(), checkChunk.z());
                if (!isLoaded) {
                    info.hasUnloadedArea = true;
                    info.description = "Unloaded area ahead - following boundary";

                    info.boundaryDirection = new Vec3(-forwardVec.z, 0, forwardVec.x);
                    break;
                }
            }
        }

        return info;
    }

    private static void switchToEdgeFollowing(ChunkBoundaryInfo boundaryInfo) {
        navigationMode = NavigationMode.EDGE_FOLLOWING;
        edgeFollowStartTime = System.currentTimeMillis();
        boundaryDirection = boundaryInfo.boundaryDirection;

        StashHunter.LOG.info("Switching to edge following: " + boundaryInfo.description);

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eEdge following activated: " + boundaryInfo.description));
        }
    }

    private static void switchToTrailFollowing(ChunkBoundaryInfo boundaryInfo) {
        navigationMode = NavigationMode.TRAIL_FOLLOWING;
        edgeFollowStartTime = System.currentTimeMillis();
        boundaryDirection = boundaryInfo.boundaryDirection;

        StashHunter.LOG.info("New chunk boundary detected - following trail for potential stash locations");

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Following new chunk boundary - potential player trail detected"));
        }
    }

    /**
     * Movement-execution entry point: hands {@code target} to Baritone's elytra pathing when
     * it's available and enabled, falling back to the built-in {@link #controlFlight(Vec3)}
     * otherwise - either because Baritone isn't installed, or because it dropped the path it
     * was given before actually reaching the target. This is purely an execution-layer choice;
     * the caller's target-selection logic (grid/trail following etc.) is unchanged either way.
     */
    private static void driveTowards(Vec3 target) {
        if (!useBaritone()) {
            baritoneTargetPos = null;
            setForwardHeld(true);
            controlFlight(target);
            return;
        }

        BlockPos targetPos = BlockPos.containing(target.x, target.y, target.z);

        if (!targetPos.equals(baritoneTargetPos)) {
            // New target - (re)issue the path.
            if (BaritoneBridge.startElytraPath(targetPos)) {
                baritoneTargetPos = targetPos;
                setForwardHeld(false);
            } else {
                setForwardHeld(true);
                controlFlight(target);
            }
            return;
        }

        if (!BaritoneBridge.isElytraActive()) {
            // Baritone's elytra process dropped the path before we arrived - fall back to the built-in
            // controller for this stretch. StuckDetector also watches for this and can trigger recovery.
            setForwardHeld(true);
            controlFlight(target);
            return;
        }

        // Baritone is actively driving movement toward targetPos - nothing to do this tick.
        setForwardHeld(false);
    }

    // Holds/releases the forward key for the built-in controller. Refcounted via KeyHold so it
    // never steals the key from the player or another module; Baritone's pathing releases it.
    private static void setForwardHeld(boolean hold) {
        if (hold == forwardHeldByUs) return;
        forwardHeldByUs = hold;
        if (hold) KeyHold.press(MeteorClient.mc.options.keyUp);
        else KeyHold.release(MeteorClient.mc.options.keyUp);
    }

    // Single decision point for "Baritone drives the flight", used by both initiateFlight() and
    // driveTowards() so the two can never disagree about whether the fallback/ElytraFly is active.
    private static boolean useBaritone() {
        return Config.useBaritonePathing && BaritoneBridge.isElytraPathingReady();
    }

    // Baritone's elytra process refuses to take off without rockets (and cannot climb without them).
    private static boolean hasFireworkRockets() {
        if (MeteorClient.mc.player == null) return false;
        for (int i = 0; i < MeteorClient.mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.FIREWORK_ROCKET) return true;
        }
        return false;
    }

    private static void controlFlight(Vec3 target) {
        if (MeteorClient.mc.player == null) return;

        // Get (and keep) the player airborne. On a server the client can only move itself by
        // sending real inputs (jump + deploy the elytra), not by writing the velocity vector.
        if (MeteorClient.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
                && !MeteorClient.mc.player.isFallFlying()) {
            if (MeteorClient.mc.player.onGround()) KeyHold.hold(ElytraController.class, MeteorClient.mc.options.keyJump, 4, null);
            MeteorClient.mc.player.startFallFlying();
        }

        Vec3 playerPos = MeteorClient.mc.player.position();
        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDistance > 1.0) {
            double yaw = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;
            double dy = target.y - playerPos.y;
            // Minecraft's pitch is positive looking down, so negate the atan2 result to climb
            // toward a higher target. The old un-negated value aimed the wrong way vertically.
            double pitch = -Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI;

            pitch = Math.max(-30.0, Math.min(30.0, pitch));

            if (MeteorClient.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                // Elytra can't sustain a very steep climb, but allow enough to gain cruise altitude.
                pitch = Math.max(-25.0, Math.min(10.0, pitch));
            }

            float currentYaw = MeteorClient.mc.player.getYRot();
            float currentPitch = MeteorClient.mc.player.getXRot();

            float yawDiff = (float) (yaw - currentYaw);
            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;

            float newYaw = currentYaw + yawDiff * 0.1f;
            float newPitch = currentPitch + ((float) pitch - currentPitch) * 0.1f;

            MeteorClient.mc.player.setYRot(newYaw);
            MeteorClient.mc.player.setXRot(newPitch);
        }

        // Forward motion comes from the forward key held by driveTowards(). The old
        // setDeltaMovement() here was client-side only (it does not move the player on a server)
        // and fought Meteor's ElytraFly, which is what made the flight stall/get stuck.
    }

    private static void flyToNextWaypoint() {
        if (currentWaypoint >= waypoints.size()) {
            stop();
            return;
        }

        Vec3 waypoint = waypoints.get(currentWaypoint);
        currentTarget = waypoint;

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§bFlying to waypoint " + (currentWaypoint + 1) + "/" + waypoints.size() +
                ": " + (int)waypoint.x + ", " + (int)waypoint.z));
        }
    }

    // Looked up per call: a static final field filled at class-load time stays null for good if
    // this class happens to load before the module is registered.
    private static NewerNewChunks newerNewChunks() {
        return Modules.get().get(NewerNewChunks.class);
    }

    public static boolean isActive() {
        return active;
    }

    /** True while Baritone lands the player after a completed scan (see Config.landAfterCompletion). */
    public static boolean isLandingAfterCompletion() {
        return landingAfterCompletion;
    }

    public static Vec3 getCurrentTarget() {
        return currentTarget;
    }

    public static int getCurrentWaypoint() {
        return currentWaypoint;
    }

    public static int getTotalWaypoints() {
        return waypoints.size();
    }

    public static String getStatus() {
        if (!active) {
            return "Idle";
        }

        if (currentWaypoint >= waypoints.size() && navigationMode == NavigationMode.NORMAL) {
            return "Completed";
        }

        String modeStr = switch (navigationMode) {
            case EDGE_FOLLOWING -> "Edge Following";
            case TRAIL_FOLLOWING -> "Trail Following";
            default -> "Flying to waypoint " + (currentWaypoint + 1) + "/" + waypoints.size();
        };

        return modeStr;
    }

    public static boolean justCompleted() {
        if (justCompleted) {
            justCompleted = false;
            return true;
        }
        return false;
    }
}
