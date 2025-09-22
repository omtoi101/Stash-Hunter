package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.modules.NewerNewChunks;
import com.stashhunter.stashhunter.utils.TripManager;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class ElytraController {
    private static final NewerNewChunks newerNewChunks = Modules.get().get(NewerNewChunks.class);
    private static List<Vec3d> waypoints = new ArrayList<>();
    private static int currentWaypoint = 0;
    private static boolean active = false;
    private static boolean justCompleted = false;
    private static Vec3d currentTarget = null;
    private static long lastWaypointTime = 0;

    // Navigation state for edge following
    private static NavigationMode navigationMode = NavigationMode.NORMAL;
    private static Vec3d lastKnownGoodPosition = null;
    private static int edgeFollowDirection = 90; // 90 for right, -90 for left
    private static long edgeFollowStartTime = 0;
    private static final long MAX_EDGE_FOLLOW_TIME = 30000; // 30 seconds max edge following
    private static Set<ChunkPos> visitedChunks = new HashSet<>();
    private static Vec3d boundaryDirection = null;

    // Trail analysis state
    private static List<ChunkPos> recentNewChunks = new ArrayList<>();
    private static TrailInfo currentTrail = null;
    private static final int TRAIL_ANALYSIS_RADIUS = 5; // chunks to analyze around player
    private static final double MIN_TRAIL_CONFIDENCE = 0.6; // minimum confidence to follow a trail

    private enum NavigationMode {
        NORMAL,           // Following waypoints normally
        EDGE_FOLLOWING,   // Following chunk boundary
        TRAIL_FOLLOWING   // Following new chunk trail
    }

    public static void start(int x1, int z1, int x2, int z2, int stripWidth) {
        waypoints.clear();
        currentWaypoint = 0;
        active = true;
        justCompleted = false;
        lastWaypointTime = System.currentTimeMillis();
        navigationMode = NavigationMode.NORMAL;
        visitedChunks.clear();
        lastKnownGoodPosition = null;
        boundaryDirection = null;

        generateWaypoints(x1, z1, x2, z2, stripWidth);
        TripManager.addTrip(waypoints, currentWaypoint);

        initiateFlight();
    }

    private static void generateWaypoints(int x1, int z1, int x2, int z2, int stripWidth) {
        // Always generate grid-based waypoints for systematic exploration
        // The bot will dynamically avoid new chunks during flight
        generateGridWaypoints(x1, z1, x2, z2, stripWidth);
    }

    private static void generateChunkBasedWaypoints(List<ChunkPos> oldChunks) {
        // This method is now unused - we don't want to restrict flight to only old chunks
        // Old chunks indicate where players have been, but we want to explore systematically
        // while avoiding new chunks dynamically
    }

    private static void generateGridWaypoints(int x1, int z1, int x2, int z2, int stripWidth) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        boolean forward = true;

        for (int x = minX; x <= maxX; x += stripWidth) {
            if (forward) {
                waypoints.add(new Vec3d(x, Config.flightAltitude, minZ));
                waypoints.add(new Vec3d(x, Config.flightAltitude, maxZ));
            } else {
                waypoints.add(new Vec3d(x, Config.flightAltitude, maxZ));
                waypoints.add(new Vec3d(x, Config.flightAltitude, minZ));
            }
            forward = !forward;
        }
    }

    public static void stop() {
        active = false;
        currentTarget = null;
        navigationMode = NavigationMode.NORMAL;
        visitedChunks.clear();
        lastKnownGoodPosition = null;
        boundaryDirection = null;

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null && elytraFly.isActive()) {
            elytraFly.toggle();
        }

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendMessage(
                net.minecraft.text.Text.of("§aStash hunter flight stopped"), false);
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
        lastWaypointTime = System.currentTimeMillis();
        navigationMode = NavigationMode.NORMAL;

        initiateFlight();
    }

    private static void initiateFlight() {
        if (!waypoints.isEmpty()) {
            if (MeteorClient.mc.player != null &&
                MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                MeteorClient.mc.player.sendMessage(
                    net.minecraft.text.Text.of("§cPlease equip an Elytra before starting!"), false);
                stop();
                return;
            }

            ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
            if (elytraFly != null && !elytraFly.isActive()) {
                elytraFly.toggle();
            }

            flyToNextWaypoint();
        }
    }

    public static void onTick() {
        if (!active || MeteorClient.mc.player == null || waypoints.isEmpty()) {
            return;
        }

        Vec3d playerPos = MeteorClient.mc.player.getPos();
        ChunkPos currentChunkPos = new ChunkPos(new BlockPos((int)playerPos.x, (int)playerPos.y, (int)playerPos.z));
        visitedChunks.add(currentChunkPos);

        // Check if we've completed all waypoints
        if (currentWaypoint >= waypoints.size() && navigationMode == NavigationMode.NORMAL) {
            if (MeteorClient.mc.player != null) {
                MeteorClient.mc.player.sendMessage(
                    net.minecraft.text.Text.of("§aCompleted scanning area!"), false);
            }
            justCompleted = true;
            stop();
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
        }
    }

    private static void handleNormalNavigation() {
        if (currentWaypoint >= waypoints.size()) return;

        Vec3d playerPos = MeteorClient.mc.player.getPos();
        Vec3d target = waypoints.get(currentWaypoint);
        currentTarget = target;

        double horizontalDistance = Math.sqrt(
            Math.pow(target.x - playerPos.x, 2) +
            Math.pow(target.z - playerPos.z, 2)
        );

        if (horizontalDistance < 20.0) {
            flyToNextWaypoint();
            return;
        }

        // Check for obstacles and boundaries
        ChunkBoundaryInfo boundaryInfo = checkForBoundaries(playerPos);

        if (boundaryInfo.hasNewChunks && newerNewChunks.dynamicTrailDetection.get()) {
            // Found new chunks - switch to trail following mode
            switchToTrailFollowing(boundaryInfo);
        } else if (boundaryInfo.hasUnloadedArea) {
            // Hit unloaded area - switch to edge following mode
            switchToEdgeFollowing(boundaryInfo);
        } else {
            // Normal flight
            controlFlight(target);
        }
    }

    private static void handleEdgeFollowing() {
        Vec3d playerPos = MeteorClient.mc.player.getPos();

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
            Vec3d edgeTarget = playerPos.add(boundaryDirection.multiply(50)); // Look 50 blocks ahead along edge
            currentTarget = new Vec3d(edgeTarget.x, Config.flightAltitude, edgeTarget.z);
            controlFlight(currentTarget);

            // Check if we can return to normal navigation
            ChunkBoundaryInfo boundaryInfo = checkForBoundaries(playerPos);
            if (!boundaryInfo.hasUnloadedArea) {
                // Boundary cleared, return to normal navigation
                navigationMode = NavigationMode.NORMAL;
                Logger.log("Edge following complete, returning to normal navigation");
            }
        }
    }

    private static void handleTrailFollowing() {
        Vec3d playerPos = MeteorClient.mc.player.getPos();

        // Similar to edge following but specifically for new chunk boundaries
        if (System.currentTimeMillis() - edgeFollowStartTime > MAX_EDGE_FOLLOW_TIME) {
            navigationMode = NavigationMode.NORMAL;
            return;
        }

        if (boundaryDirection != null) {
            Vec3d trailTarget = playerPos.add(boundaryDirection.multiply(50));
            currentTarget = new Vec3d(trailTarget.x, Config.flightAltitude, trailTarget.z);
            controlFlight(currentTarget);

            // Continue following the trail of new chunks
            ChunkBoundaryInfo boundaryInfo = checkForBoundaries(playerPos);
            if (!boundaryInfo.hasNewChunks) {
                // Trail ended, return to normal navigation
                navigationMode = NavigationMode.NORMAL;
                Logger.log("New chunk trail ended, returning to normal navigation");
            }
        }
    }

    private static class ChunkBoundaryInfo {
        boolean hasNewChunks = false;
        boolean hasUnloadedArea = false;
        Vec3d boundaryDirection = null;
        String description = "";
        TrailInfo trailInfo = null; // Added trail analysis
    }

    private static class TrailInfo {
        Vec3d direction;
        double confidence; // 0.0 to 1.0
        int length; // number of chunks in trail
        ChunkPos startChunk;
        ChunkPos endChunk;
        String type; // "corridor", "line", "scattered", etc.
    }

    private static TrailInfo analyzeNewChunkTrail(Vec3d playerPos, List<ChunkPos> newChunks) {
        if (newChunks == null || newChunks.size() < 3) {
            return null; // Need at least 3 chunks to form a trail
        }

        ChunkPos playerChunk = new ChunkPos(new BlockPos((int)playerPos.x, (int)playerPos.y, (int)playerPos.z));

        // Find new chunks within analysis radius
        List<ChunkPos> nearbyNewChunks = new ArrayList<>();
        for (ChunkPos chunk : newChunks) {
            double distance = Math.sqrt(
                Math.pow(chunk.x - playerChunk.x, 2) +
                Math.pow(chunk.z - playerChunk.z, 2)
            );
            if (distance <= TRAIL_ANALYSIS_RADIUS) {
                nearbyNewChunks.add(chunk);
            }
        }

        if (nearbyNewChunks.size() < 3) {
            return null;
        }

        // Analyze patterns in nearby new chunks
        TrailInfo bestTrail = null;
        double bestConfidence = 0;

        // Look for linear patterns (corridors, tunnels, roads)
        TrailInfo linearTrail = analyzeLinearPattern(nearbyNewChunks, playerPos);
        if (linearTrail != null && linearTrail.confidence > bestConfidence) {
            bestTrail = linearTrail;
            bestConfidence = linearTrail.confidence;
        }

        // Look for directional patterns (consistent direction of travel)
        TrailInfo directionalTrail = analyzeDirectionalPattern(nearbyNewChunks, playerPos);
        if (directionalTrail != null && directionalTrail.confidence > bestConfidence) {
            bestTrail = directionalTrail;
            bestConfidence = directionalTrail.confidence;
        }

        return bestTrail;
    }

    private static TrailInfo analyzeLinearPattern(List<ChunkPos> chunks, Vec3d playerPos) {
        // Look for chunks that form roughly straight lines
        for (int i = 0; i < chunks.size() - 2; i++) {
            for (int j = i + 1; j < chunks.size() - 1; j++) {
                for (int k = j + 1; k < chunks.size(); k++) {
                    ChunkPos c1 = chunks.get(i);
                    ChunkPos c2 = chunks.get(j);
                    ChunkPos c3 = chunks.get(k);

                    // Calculate if these three chunks are roughly collinear
                    double linearity = calculateLinearity(c1, c2, c3);

                    if (linearity > 0.7) { // Threshold for considering chunks "linear"
                        TrailInfo trail = new TrailInfo();
                        trail.confidence = linearity;
                        trail.type = "corridor";
                        trail.length = 3;

                        // Calculate direction from first to last chunk
                        double dx = c3.x - c1.x;
                        double dz = c3.z - c1.z;
                        trail.direction = new Vec3d(dx, 0, dz).normalize();

                        trail.startChunk = c1;
                        trail.endChunk = c3;

                        return trail;
                    }
                }
            }
        }
        return null;
    }

    private static TrailInfo analyzeDirectionalPattern(List<ChunkPos> chunks, Vec3d playerPos) {
        // Look for chunks that show consistent directional movement
        if (chunks.size() < 4) return null;

        // Sort chunks by distance from player
        chunks.sort(Comparator.comparingDouble(c ->
            Math.pow(c.x * 16 - playerPos.x, 2) + Math.pow(c.z * 16 - playerPos.z, 2)
        ));

        Vec3d avgDirection = Vec3d.ZERO;
        int validDirections = 0;

        // Calculate average direction between consecutive chunks
        for (int i = 0; i < chunks.size() - 1; i++) {
            ChunkPos from = chunks.get(i);
            ChunkPos to = chunks.get(i + 1);

            Vec3d direction = new Vec3d(to.x - from.x, 0, to.z - from.z);
            if (direction.lengthSquared() > 0) {
                avgDirection = avgDirection.add(direction.normalize());
                validDirections++;
            }
        }

        if (validDirections >= 2) {
            Vec3d finalDirection = avgDirection.multiply(1.0 / validDirections);
            double consistency = calculateDirectionConsistency(chunks, finalDirection);

            if (consistency > 0.6) {
                TrailInfo trail = new TrailInfo();
                trail.direction = finalDirection.normalize();
                trail.confidence = consistency;
                trail.type = "directional";
                trail.length = chunks.size();
                trail.startChunk = chunks.get(0);
                trail.endChunk = chunks.get(chunks.size() - 1);

                return trail;
            }
        }

        return null;
    }

    private static double calculateLinearity(ChunkPos c1, ChunkPos c2, ChunkPos c3) {
        // Calculate how close three points are to forming a straight line
        // Using the cross product method to find deviation from straight line

        Vec3d v1 = new Vec3d(c2.x - c1.x, 0, c2.z - c1.z);
        Vec3d v2 = new Vec3d(c3.x - c2.x, 0, c3.z - c2.z);

        if (v1.lengthSquared() < 0.01 || v2.lengthSquared() < 0.01) {
            return 0; // Points too close together
        }

        // Calculate angle between vectors
        double dot = v1.normalize().dotProduct(v2.normalize());
        dot = Math.max(-1.0, Math.min(1.0, dot)); // Clamp to valid range

        double angle = Math.acos(Math.abs(dot));
        double linearity = 1.0 - (angle / (Math.PI / 2)); // 1.0 = perfectly linear, 0.0 = perpendicular

        return Math.max(0, linearity);
    }

    private static double calculateDirectionConsistency(List<ChunkPos> chunks, Vec3d targetDirection) {
        double totalConsistency = 0;
        int comparisons = 0;

        for (int i = 0; i < chunks.size() - 1; i++) {
            ChunkPos from = chunks.get(i);
            ChunkPos to = chunks.get(i + 1);

            Vec3d direction = new Vec3d(to.x - from.x, 0, to.z - from.z);
            if (direction.lengthSquared() > 0) {
                double dot = direction.normalize().dotProduct(targetDirection.normalize());
                totalConsistency += Math.max(0, dot); // Only positive correlations
                comparisons++;
            }
        }

        return comparisons > 0 ? totalConsistency / comparisons : 0;
    }

    private static ChunkBoundaryInfo checkForBoundaries(Vec3d playerPos) {
        ChunkBoundaryInfo info = new ChunkBoundaryInfo();

        if (newerNewChunks == null || !newerNewChunks.isActive()) {
            return info;
        }

        List<ChunkPos> newChunks = newerNewChunks.getNewChunks();
        Vec3d forwardVec = Vec3d.fromPolar(0, MeteorClient.mc.player.getYaw()).normalize();

        // First, analyze if there's a meaningful trail in the area
        TrailInfo trailInfo = analyzeNewChunkTrail(playerPos, newChunks);
        if (trailInfo != null && trailInfo.confidence >= MIN_TRAIL_CONFIDENCE) {
            info.hasNewChunks = true;
            info.trailInfo = trailInfo;
            info.boundaryDirection = trailInfo.direction;
            info.description = String.format("Detected %s trail (confidence: %.1f)",
                trailInfo.type, trailInfo.confidence);
            return info; // Priority: follow meaningful trails
        }

        // If no good trail, check for immediate obstacles to avoid
        for (int distance = 16; distance <= 64; distance += 16) {
            Vec3d checkPos = playerPos.add(forwardVec.multiply(distance));
            ChunkPos checkChunk = new ChunkPos(new BlockPos((int)checkPos.x, (int)checkPos.y, (int)checkPos.z));

            // Check if chunk is new (AVOID these unless they form a trail)
            if (newChunks != null && newChunks.contains(checkChunk)) {
                info.hasNewChunks = true;
                info.description = "New chunk ahead - avoiding (no clear trail detected)";

                // Calculate avoidance direction (perpendicular to forward)
                info.boundaryDirection = new Vec3d(-forwardVec.z, 0, forwardVec.x);
                break;
            }

            // Check if chunk is unloaded
            if (MeteorClient.mc.world != null) {
                boolean isLoaded = MeteorClient.mc.world.isChunkLoaded(checkChunk.x, checkChunk.z);
                if (!isLoaded) {
                    info.hasUnloadedArea = true;
                    info.description = "Unloaded area ahead - following boundary";

                    info.boundaryDirection = new Vec3d(-forwardVec.z, 0, forwardVec.x);
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
        lastKnownGoodPosition = MeteorClient.mc.player.getPos();

        Logger.log("Switching to edge following: " + boundaryInfo.description);

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendMessage(
                net.minecraft.text.Text.of("§eEdge following activated: " + boundaryInfo.description), false);
        }
    }

    private static void switchToTrailFollowing(ChunkBoundaryInfo boundaryInfo) {
        navigationMode = NavigationMode.TRAIL_FOLLOWING;
        edgeFollowStartTime = System.currentTimeMillis();
        boundaryDirection = boundaryInfo.boundaryDirection;
        lastKnownGoodPosition = MeteorClient.mc.player.getPos();

        Logger.log("New chunk boundary detected - following trail for potential stash locations");

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendMessage(
                net.minecraft.text.Text.of("§6Following new chunk boundary - potential player trail detected"), false);
        }
    }

    private static void controlFlight(Vec3d target) {
        if (MeteorClient.mc.player == null) return;

        Vec3d playerPos = MeteorClient.mc.player.getPos();
        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDistance > 1.0) {
            double yaw = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;
            double dy = target.y - playerPos.y;
            double pitch = Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI;

            pitch = Math.max(-30.0, Math.min(30.0, pitch));

            if (MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                pitch = Math.max(-15.0, Math.min(5.0, pitch));
            }

            float currentYaw = MeteorClient.mc.player.getYaw();
            float currentPitch = MeteorClient.mc.player.getPitch();

            float yawDiff = (float) (yaw - currentYaw);
            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;

            float newYaw = currentYaw + yawDiff * 0.1f;
            float newPitch = currentPitch + ((float) pitch - currentPitch) * 0.1f;

            MeteorClient.mc.player.setYaw(newYaw);
            MeteorClient.mc.player.setPitch(newPitch);
        }

        if (MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            boolean isGliding = MeteorClient.mc.player.isGliding();

            if (isGliding) {
                Vec3d forward = new Vec3d(
                    -Math.sin(Math.toRadians(MeteorClient.mc.player.getYaw())),
                    -Math.sin(Math.toRadians(MeteorClient.mc.player.getPitch())) * 0.3,
                    Math.cos(Math.toRadians(MeteorClient.mc.player.getYaw()))
                ).normalize().multiply(0.8);

                MeteorClient.mc.player.setVelocity(forward);
            } else {
                if (MeteorClient.mc.player.getVelocity().y < -0.5 && !MeteorClient.mc.player.isOnGround()) {
                    MeteorClient.mc.player.startGliding();
                }
            }
        }
    }

    private static void flyToNextWaypoint() {
        if (currentWaypoint >= waypoints.size()) {
            stop();
            return;
        }

        Vec3d waypoint = waypoints.get(currentWaypoint);
        currentTarget = waypoint;
        lastWaypointTime = System.currentTimeMillis();

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendMessage(
                net.minecraft.text.Text.of("§bFlying to waypoint " + (currentWaypoint + 1) + "/" + waypoints.size() +
                ": " + (int)waypoint.x + ", " + (int)waypoint.z), false);
        }

        currentWaypoint++;
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3d getCurrentTarget() {
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
            default -> "Flying to waypoint " + currentWaypoint + "/" + waypoints.size();
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
