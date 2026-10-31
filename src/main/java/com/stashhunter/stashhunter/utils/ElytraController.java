package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.modules.NewerNewChunks;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class ElytraController {
    private static List<Vec3> waypoints = new ArrayList<>();
    private static int currentWaypoint = 0;
    // Saved-trip entry of the current flight; the next save replaces it instead of adding a copy.
    private static long tripTimestamp = 0;
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

    // Wall-clock when the post-scan landing was handed to Baritone, for the landing timeout below.
    private static long landingStartTime = 0;

    // If Baritone's elytra process hasn't finished landing this long after the scan ended, give up:
    // a stuck landing otherwise keeps the altitude-loss detector and auto-firework muted forever.
    private static final long LANDING_TIMEOUT_MS = 120_000; // 2 minutes

    // Ticks since the last rocket count, and how far ahead landSafely lets Baritone land.
    private static int fireworkCheckTicks = 0;
    private static final double SAFE_LANDING_DISTANCE = 64;

    // Restarting Baritone after it landed mid-scan: spacing between tries, how many before pausing,
    // and how close to a waypoint the landing counts as having reached it.
    private static long lastBaritoneRestartMs = 0;
    private static int baritoneRestarts = 0;
    private static final long BARITONE_RESTART_COOLDOWN_MS = 5_000;
    // After a restart Baritone may walk to a jump spot first; don't re-issue the path (which would
    // restart that walk) before it had this long to get airborne.
    private static final long BARITONE_TAKEOFF_GRACE_MS = 20_000;
    private static final int MAX_BARITONE_RESTARTS = 3;
    private static final double WAYPOINT_LANDED_RADIUS = 64;

    // deployElytra jumps at most this often, so repeated calls don't stack jump holds.
    private static long lastTakeoffJumpMs = 0;
    private static final long TAKEOFF_JUMP_INTERVAL_MS = 500;

    // Navigation state for edge following
    private static NavigationMode navigationMode = NavigationMode.NORMAL;
    private static long edgeFollowStartTime = 0;
    private static final long MAX_EDGE_FOLLOW_TIME = 30000; // 30 seconds max edge following
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
        landingStartTime = 0;
        navigationMode = NavigationMode.NORMAL;
        boundaryDirection = null;
        baritoneTargetPos = null;

        waypoints.addAll(generated);
        tripTimestamp = TripManager.saveTrip(0, waypoints, currentWaypoint);

        initiateFlight();
    }

    public static void stop() {
        stop(true);
    }

    // cancelBaritone=false keeps Baritone's elytra process running so it can finish the landing.
    private static void stop(boolean cancelBaritone) {
        active = false;
        if (cancelBaritone) {
            landingAfterCompletion = false;
            landingStartTime = 0;
        }
        currentTarget = null;
        navigationMode = NavigationMode.NORMAL;
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
        tripTimestamp = TripManager.saveTrip(tripTimestamp, waypoints, currentWaypoint);
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
        tripTimestamp = tripData.timestamp;
        currentWaypoint = tripData.currentWaypoint;
        // A trip keeps the altitude it was started with; the current setting wins on resume.
        waypoints = withAltitude(tripData.waypoints, currentWaypoint, Config.flightAltitude);
        active = true;
        justCompleted = false;
        landingAfterCompletion = false;
        landingStartTime = 0;
        navigationMode = NavigationMode.NORMAL;
        baritoneTargetPos = null;

        initiateFlight();
    }

    /**
     * Lets Baritone's elytra process land the player: once its path to the destination is
     * complete it searches a safe landing spot (solid, flat, open sky) nearby and lands there by
     * itself. So we only need the elytra path to the last waypoint running and must not cancel it.
     */
    private static boolean handOffLandingToBaritone(BlockPos destination) {
        if (!Config.useBaritonePathing || !BaritoneBridge.isElytraPathingReady()) return false;
        if (MeteorClient.mc.player == null || !MeteorClient.mc.player.isFallFlying() || MeteorClient.mc.player.onGround()) return false; // nothing to land
        if (destination.equals(baritoneTargetPos) && BaritoneBridge.isElytraActive()) return true;
        return BaritoneBridge.startElytraPath(destination);
    }

    private static void beginBaritoneLanding() {
        stop(false);
        landingAfterCompletion = true;
        landingStartTime = System.currentTimeMillis();
    }

    /**
     * Baritone's elytra flight needs rockets to hold altitude. Once the inventory runs low, save the
     * trip and let Baritone land a short way ahead (it picks a safe spot there by itself) instead
     * of gliding on until the player comes down wherever the last rocket ran out.
     */
    private static boolean landIfFireworksLow() {
        if (!Config.landOnLowFireworks || ++fireworkCheckTicks < 20) return false; // once per second
        fireworkCheckTicks = 0;
        if (!useBaritone() || !MeteorClient.mc.player.isFallFlying()) return false; // ElytraFly needs no rockets

        int rockets = countFireworkRockets();
        if (rockets > Config.lowFireworkThreshold) return false;

        String status = "Fireworks low (" + rockets + " left) - scan paused at waypoint " + (currentWaypoint + 1) + "/" + waypoints.size() + ".";
        if (landSafely()) {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e" + status + " Baritone is landing nearby. Restock and use .stashhunter resume."));
            DiscordWebhook.sendMessage("", new DiscordEmbed("Fireworks Low - Landing", status + " Baritone is landing at a safe spot nearby.", 0xFFAA00));
        } else {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c" + status + " Baritone couldn't take over the landing - land manually!"));
            DiscordWebhook.sendMessage("", new DiscordEmbed("Fireworks Low - Land Manually", status + " Automatic landing failed; the player is still gliding.", 0xFF0000));
        }
        return true;
    }

    /**
     * Saves the trip (resumable) and lets Baritone land a short way ahead; it picks a safe spot
     * there by itself. Returns false when Baritone can't take over: the flight is stopped then and
     * the player still glides.
     */
    public static boolean landSafely() {
        if (!active || MeteorClient.mc.player == null) return false;
        Vec3 pos = MeteorClient.mc.player.position();
        Vec3 heading = currentTarget != null ? currentTarget.subtract(pos) : MeteorClient.mc.player.getLookAngle();
        heading = new Vec3(heading.x, 0, heading.z);
        if (heading.lengthSqr() < 1.0E-4) heading = new Vec3(1, 0, 0);
        Vec3 ahead = pos.add(heading.normalize().scale(SAFE_LANDING_DISTANCE));
        BlockPos destination = BlockPos.containing(ahead.x, pos.y, ahead.z);

        tripTimestamp = TripManager.saveTrip(tripTimestamp, waypoints, currentWaypoint);
        if (handOffLandingToBaritone(destination)) {
            beginBaritoneLanding();
            return true;
        }
        stop();
        return false;
    }

    private static void initiateFlight() {
        baritoneRestarts = 0;
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
            } else if (useBaritone && MeteorClient.mc.player != null && Config.landOnLowFireworks
                && countFireworkRockets() <= Config.lowFireworkThreshold) {
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eWarning: only " + countFireworkRockets()
                    + " rockets (low-firework-threshold " + Config.lowFireworkThreshold + ") - the scan will land as soon as you are airborne."));
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
        clearGroundGlide();

        if (landingAfterCompletion) {
            if (!BaritoneBridge.isElytraActive()) {
                landingAfterCompletion = false; // Baritone landed ("Done"), gave up, or was cancelled elsewhere
            } else if (System.currentTimeMillis() - landingStartTime > LANDING_TIMEOUT_MS) {
                // Baritone couldn't find a safe landing spot in time. Give up so the altitude-loss
                // detector and auto-firework aren't muted indefinitely (a stuck landing never ends).
                landingAfterCompletion = false;
                BaritoneBridge.cancel();
                if (MeteorClient.mc.player != null) {
                    MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eLanding timed out - staying airborne."));
                }
            }
        }

        if (!active || MeteorClient.mc.player == null || waypoints.isEmpty()) {
            return;
        }

        if (landIfFireworksLow()) return;

        // Check if we've completed all waypoints
        if (currentWaypoint >= waypoints.size() && navigationMode == NavigationMode.NORMAL) {
            if (MeteorClient.mc.player != null) {
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aCompleted scanning area!"));
            }
            justCompleted = true;
            Vec3 last = waypoints.get(waypoints.size() - 1);
            if (Config.landAfterCompletion && handOffLandingToBaritone(BlockPos.containing(last.x, last.y, last.z))) {
                beginBaritoneLanding();
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

    /**
     * Applies a changed Config.flightAltitude to the running flight: the remaining waypoints move
     * to the new height (driveTowards re-issues Baritone's path for the changed target).
     */
    public static void applyFlightAltitude() {
        if (!active || waypoints.isEmpty()) return;
        waypoints = withAltitude(waypoints, currentWaypoint, Config.flightAltitude);
        if (MeteorClient.mc.player != null && MeteorClient.mc.player.getY() < Config.flightAltitude - 2) {
            climbToAltitude();
        }
    }

    private static List<Vec3> withAltitude(List<Vec3> source, int from, double altitude) {
        List<Vec3> result = new ArrayList<>(source);
        for (int i = Math.max(0, from); i < result.size(); i++) {
            Vec3 waypoint = result.get(i);
            result.set(i, new Vec3(waypoint.x, altitude, waypoint.z));
        }
        return result;
    }

    public static void climbToAltitude() {
        if (MeteorClient.mc.player == null) return;
        // Baritone climbs on its way to the next waypoint, which carries the altitude. A climb target
        // straight overhead would be a destination it reaches and then lands at.
        if (useBaritone()) return;
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
            driveTowards(lookAheadTarget(playerPos));

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
            driveTowards(lookAheadTarget(playerPos));

            // Continue following the trail of new chunks
            ChunkBoundaryInfo boundaryInfo = checkForBoundaries(playerPos);
            if (!boundaryInfo.hasNewChunks) {
                // Trail ended, return to normal navigation
                navigationMode = NavigationMode.NORMAL;
                StashHunter.LOG.info("New chunk trail ended, returning to normal navigation");
            }
        }
    }

    // Edge/trail following aims LOOK_AHEAD blocks ahead along the boundary. The point is kept until
    // the player is halfway there: re-aiming every tick handed Baritone a new destination each tick,
    // and every pathTo() starts a fresh elytra behaviour that repacks all loaded chunks.
    private static final double LOOK_AHEAD = 50;

    private static Vec3 lookAheadTarget(Vec3 playerPos) {
        if (currentTarget != null) {
            double dx = currentTarget.x - playerPos.x, dz = currentTarget.z - playerPos.z;
            if (dx * dx + dz * dz > LOOK_AHEAD * LOOK_AHEAD / 4) return currentTarget;
        }
        Vec3 ahead = playerPos.add(boundaryDirection.scale(LOOK_AHEAD));
        currentTarget = new Vec3(ahead.x, Config.flightAltitude, ahead.z);
        return currentTarget;
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
            ChunkPos checkChunk = new ChunkPos(Mth.floor(checkPos.x) >> 4, Mth.floor(checkPos.z) >> 4);

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
        currentTarget = null; // still the waypoint; lookAheadTarget aims along the edge

        StashHunter.LOG.info("Switching to edge following: " + boundaryInfo.description);

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eEdge following activated: " + boundaryInfo.description));
        }
    }

    private static void switchToTrailFollowing(ChunkBoundaryInfo boundaryInfo) {
        navigationMode = NavigationMode.TRAIL_FOLLOWING;
        edgeFollowStartTime = System.currentTimeMillis();
        boundaryDirection = boundaryInfo.boundaryDirection;
        currentTarget = null; // still the waypoint; lookAheadTarget aims along the trail

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
            if (MeteorClient.mc.player.onGround()) {
                // Baritone landed on its own (out of rockets, destination reached, gave up). The
                // built-in controller can't take off without ElytraFly, so hand it back to Baritone.
                restartBaritoneAfterLanding(target);
                return;
            }
            // Baritone's elytra process dropped the path in mid-air - fall back to the built-in
            // controller for this stretch. StuckDetector also watches for this and can trigger recovery.
            baritoneRestarts = 0; // airborne again, so the last restart worked
            setForwardHeld(true);
            controlFlight(target);
            return;
        }

        // Baritone is actively driving movement toward targetPos - nothing to do this tick.
        baritoneRestarts = 0;
        setForwardHeld(false);
    }

    private static void restartBaritoneAfterLanding(Vec3 target) {
        setForwardHeld(false);
        long now = System.currentTimeMillis();
        long wait = baritoneRestarts == 0 ? BARITONE_RESTART_COOLDOWN_MS : BARITONE_TAKEOFF_GRACE_MS;
        if (now - lastBaritoneRestartMs < wait) return;
        lastBaritoneRestartMs = now;

        String reason = !hasFireworkRockets() ? "no firework rockets left"
            : baritoneRestarts >= MAX_BARITONE_RESTARTS ? "Baritone did not take off after " + MAX_BARITONE_RESTARTS + " tries"
            : null;
        if (reason != null) {
            String status = "Landed and " + reason + " - flight paused at waypoint " + (currentWaypoint + 1) + "/" + waypoints.size() + ".";
            pause();
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e" + status + " Use .stashhunter resume to continue."));
            DiscordWebhook.sendMessage("", new DiscordEmbed("Flight Paused After Landing", status, 0xFFAA00));
            return;
        }

        // Landed close to the waypoint: count it as reached so Baritone isn't sent to land there again.
        Vec3 pos = MeteorClient.mc.player.position();
        double dx = target.x - pos.x, dz = target.z - pos.z;
        if (dx * dx + dz * dz < WAYPOINT_LANDED_RADIUS * WAYPOINT_LANDED_RADIUS) {
            currentWaypoint++;
            if (currentWaypoint >= waypoints.size()) return; // onTick finishes the scan next tick
            flyToNextWaypoint();
        }
        baritoneRestarts++;
        baritoneTargetPos = null; // driveTowards re-issues the path; Baritone takes off via elytraAutoJump
    }

    // Holds/releases the forward key for the built-in controller. Refcounted via KeyHold so it
    // never steals the key from the player or another module; Baritone's pathing releases it.
    private static void setForwardHeld(boolean hold) {
        if (hold == forwardHeldByUs) return;
        forwardHeldByUs = hold;
        if (hold) KeyHold.press(MeteorClient.mc.options.keyUp);
        else KeyHold.release(MeteorClient.mc.options.keyUp);
    }

    /** True while Baritone's elytra process (not the built-in controller) does the steering. */
    public static boolean isBaritoneFlight() {
        return useBaritone();
    }

    // Single decision point for "Baritone drives the flight", used by both initiateFlight() and
    // driveTowards() so the two can never disagree about whether the fallback/ElytraFly is active.
    private static boolean useBaritone() {
        return Config.useBaritonePathing && BaritoneBridge.isElytraPathingReady();
    }

    // Baritone's elytra process refuses to take off without rockets (and cannot climb without them).
    private static boolean hasFireworkRockets() {
        return countFireworkRockets() > 0;
    }

    private static int countFireworkRockets() {
        if (MeteorClient.mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < MeteorClient.mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.FIREWORK_ROCKET) count += stack.getCount();
        }
        return count;
    }

    /**
     * Opens the elytra the way vanilla does: jump off the ground, deploy in the air and tell the
     * server. A client-only startFallFlying() left the player gliding while standing on the ground,
     * a state the server never corrects (and Baritone then thinks it is still flying).
     */
    public static void deployElytra() {
        var player = MeteorClient.mc.player;
        if (player == null || player.isFallFlying()) return;
        if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return;

        if (player.onGround()) {
            long now = System.currentTimeMillis();
            if (now - lastTakeoffJumpMs > TAKEOFF_JUMP_INTERVAL_MS) {
                lastTakeoffJumpMs = now;
                KeyHold.hold(ElytraController.class, MeteorClient.mc.options.keyJump, 4, null);
            }
            return;
        }
        if (player.tryToStartFallFlying() && MeteorClient.mc.getConnection() != null) {
            MeteorClient.mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    // Undoes a glide that only exists client-side: on the ground the server has ended it already.
    private static void clearGroundGlide() {
        var player = MeteorClient.mc.player;
        if (player != null && player.isFallFlying() && player.onGround()) player.stopFallFlying();
    }

    private static void controlFlight(Vec3 target) {
        if (MeteorClient.mc.player == null) return;

        // Get (and keep) the player airborne. On a server the client can only move itself by
        // sending real inputs (jump + deploy the elytra), not by writing the velocity vector.
        deployElytra();

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
