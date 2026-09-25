package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.modules.NewerNewChunks;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class ElytraController {
    private static final NewerNewChunks newerNewChunks = Modules.get().get(NewerNewChunks.class);
    private static List<Vec3> waypoints = new ArrayList<>();
    private static int currentWaypoint = 0;
    private static boolean active = false;
    private static boolean justCompleted = false;
    private static Vec3 currentTarget = null;

    // Navigation state for edge following
    private static NavigationMode navigationMode = NavigationMode.NORMAL;
    private static Vec3 lastKnownGoodPosition = null;
    private static int edgeFollowDirection = 90; // 90 for right, -90 for left
    private static long edgeFollowStartTime = 0;
    private static final long MAX_EDGE_FOLLOW_TIME = 30000; // 30 seconds max edge following
    private static Set<ChunkPos> visitedChunks = new HashSet<>();
    private static Vec3 boundaryDirection = null;

    // Trail analysis state
    private static final int TRAIL_ANALYSIS_RADIUS = 5; // chunks to analyze around player
    private static final double MIN_TRAIL_CONFIDENCE = 0.6; // minimum confidence to follow a trail

    private enum NavigationMode {
        NORMAL,           // Following waypoints normally
        EDGE_FOLLOWING,   // Following chunk boundary
        TRAIL_FOLLOWING,  // Following new chunk trail
        CLIMBING          // Climbing to target altitude
    }

    public static void start(int x1, int z1, int x2, int z2, int stripWidth) {
        waypoints.clear();
        currentWaypoint = 0;
        active = true;
        justCompleted = false;
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

    private static void generateGridWaypoints(int x1, int z1, int x2, int z2, int stripWidth) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        boolean forward = true;

        for (int x = minX; x <= maxX; x += stripWidth) {
            if (forward) {
                waypoints.add(new Vec3(x, Config.flightAltitude, minZ));
                waypoints.add(new Vec3(x, Config.flightAltitude, maxZ));
            } else {
                waypoints.add(new Vec3(x, Config.flightAltitude, maxZ));
                waypoints.add(new Vec3(x, Config.flightAltitude, minZ));
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
        navigationMode = NavigationMode.NORMAL;

        initiateFlight();
    }

    private static void initiateFlight() {
        if (!waypoints.isEmpty()) {
            if (MeteorClient.mc.player != null &&
                MeteorClient.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cPlease equip an Elytra before starting!"));
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

        Vec3 playerPos = MeteorClient.mc.player.position();
        ChunkPos currentChunkPos = new ChunkPos((int)playerPos.x >> 4, (int)playerPos.z >> 4);
        visitedChunks.add(currentChunkPos);

        // Check if we've completed all waypoints
        if (currentWaypoint >= waypoints.size() && navigationMode == NavigationMode.NORMAL) {
            if (MeteorClient.mc.player != null) {
                MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aCompleted scanning area!"));
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
        Logger.log("Climbing to altitude: " + Config.flightAltitude);
    }

    private static void handleClimbing() {
        if (MeteorClient.mc.player == null) return;
        if (MeteorClient.mc.player.position().y >= Config.flightAltitude - 2) {
            navigationMode = NavigationMode.NORMAL;
            Logger.log("Reached target altitude, resuming normal navigation.");
        } else {
            controlFlight(currentTarget);
        }
    }

    private static void handleNormalNavigation() {
        if (currentWaypoint >= waypoints.size()) return;

        Vec3 playerPos = MeteorClient.mc.player.position();
        Vec3 target = waypoints.get(currentWaypoint);
        currentTarget = target;

        double horizontalDistance = Math.sqrt(
            Math.pow(target.x - playerPos.x, 2) +
            Math.pow(target.z - playerPos.z, 2)
        );

        if (horizontalDistance < 20.0) {
            currentWaypoint++;
            if (currentWaypoint < waypoints.size()) {
                flyToNextWaypoint();
            }
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
        Vec3 playerPos = MeteorClient.mc.player.position();

        // Similar to edge following but specifically for new chunk boundaries
        if (System.currentTimeMillis() - edgeFollowStartTime > MAX_EDGE_FOLLOW_TIME) {
            navigationMode = NavigationMode.NORMAL;
            return;
        }

        if (boundaryDirection != null) {
            Vec3 trailTarget = playerPos.add(boundaryDirection.scale(50));
            currentTarget = new Vec3(trailTarget.x, Config.flightAltitude, trailTarget.z);
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
        Vec3 boundaryDirection = null;
        String description = "";
        TrailInfo trailInfo = null; // Added trail analysis
    }

    private static class TrailInfo {
        Vec3 direction;
        double confidence; // 0.0 to 1.0
        int length; // number of chunks in trail
        ChunkPos startChunk;
        ChunkPos endChunk;
        String type; // "corridor", "line", "scattered", etc.
    }

    private static TrailInfo analyzeNewChunkTrail(Vec3 playerPos, List<ChunkPos> newChunks) {
        if (newChunks == null || newChunks.size() < 3) {
            return null; // Need at least 3 chunks to form a trail
        }

        ChunkPos playerChunk = new ChunkPos((int)playerPos.x >> 4, (int)playerPos.z >> 4);

        // Find new chunks within analysis radius
        List<ChunkPos> nearbyNewChunks = new ArrayList<>();
        for (ChunkPos chunk : newChunks) {
            double distance = Math.sqrt(
                Math.pow(chunk.x() - playerChunk.x(), 2) +
                Math.pow(chunk.z() - playerChunk.z(), 2)
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

    private static TrailInfo analyzeLinearPattern(List<ChunkPos> chunks, Vec3 playerPos) {
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
                        double dx = c3.x() - c1.x();
                        double dz = c3.z() - c1.z();
                        trail.direction = new Vec3(dx, 0, dz).normalize();

                        trail.startChunk = c1;
                        trail.endChunk = c3;

                        return trail;
                    }
                }
            }
        }
        return null;
    }

    private static TrailInfo analyzeDirectionalPattern(List<ChunkPos> chunks, Vec3 playerPos) {
        // Look for chunks that show consistent directional movement
        if (chunks.size() < 4) return null;

        // Sort chunks by distance from player
        chunks.sort(Comparator.comparingDouble(c ->
            Math.pow(c.x() * 16 - playerPos.x, 2) + Math.pow(c.z() * 16 - playerPos.z, 2)
        ));

        Vec3 avgDirection = Vec3.ZERO;
        int validDirections = 0;

        // Calculate average direction between consecutive chunks
        for (int i = 0; i < chunks.size() - 1; i++) {
            ChunkPos from = chunks.get(i);
            ChunkPos to = chunks.get(i + 1);

            Vec3 direction = new Vec3(to.x() - from.x(), 0, to.z() - from.z());
            if (direction.lengthSqr() > 0) {
                avgDirection = avgDirection.add(direction.normalize());
                validDirections++;
            }
        }

        if (validDirections >= 2) {
            Vec3 finalDirection = avgDirection.scale(1.0 / validDirections);
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

        Vec3 v1 = new Vec3(c2.x() - c1.x(), 0, c2.z() - c1.z());
        Vec3 v2 = new Vec3(c3.x() - c2.x(), 0, c3.z() - c2.z());

        if (v1.lengthSqr() < 0.01 || v2.lengthSqr() < 0.01) {
            return 0; // Points too close together
        }

        // Calculate angle between vectors
        double dot = v1.normalize().dot(v2.normalize());
        dot = Math.max(-1.0, Math.min(1.0, dot)); // Clamp to valid range

        double angle = Math.acos(Math.abs(dot));
        double linearity = 1.0 - (angle / (Math.PI / 2)); // 1.0 = perfectly linear, 0.0 = perpendicular

        return Math.max(0, linearity);
    }

    private static double calculateDirectionConsistency(List<ChunkPos> chunks, Vec3 targetDirection) {
        double totalConsistency = 0;
        int comparisons = 0;

        for (int i = 0; i < chunks.size() - 1; i++) {
            ChunkPos from = chunks.get(i);
            ChunkPos to = chunks.get(i + 1);

            Vec3 direction = new Vec3(to.x() - from.x(), 0, to.z() - from.z());
            if (direction.lengthSqr() > 0) {
                double dot = direction.normalize().dot(targetDirection.normalize());
                totalConsistency += Math.max(0, dot); // Only positive correlations
                comparisons++;
            }
        }

        return comparisons > 0 ? totalConsistency / comparisons : 0;
    }

    private static ChunkBoundaryInfo checkForBoundaries(Vec3 playerPos) {
        ChunkBoundaryInfo info = new ChunkBoundaryInfo();

        if (newerNewChunks == null || !newerNewChunks.isActive()) {
            return info;
        }

        List<ChunkPos> newChunks = newerNewChunks.getNewChunks();
        Vec3 forwardVec = Vec3.directionFromRotation(0, MeteorClient.mc.player.getYRot()).normalize();

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
            Vec3 checkPos = playerPos.add(forwardVec.scale(distance));
            ChunkPos checkChunk = new ChunkPos((int)checkPos.x >> 4, (int)checkPos.z >> 4);

            // Check if chunk is new (AVOID these unless they form a trail)
            if (newChunks != null && newChunks.contains(checkChunk)) {
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
        lastKnownGoodPosition = MeteorClient.mc.player.position();

        Logger.log("Switching to edge following: " + boundaryInfo.description);

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eEdge following activated: " + boundaryInfo.description));
        }
    }

    private static void switchToTrailFollowing(ChunkBoundaryInfo boundaryInfo) {
        navigationMode = NavigationMode.TRAIL_FOLLOWING;
        edgeFollowStartTime = System.currentTimeMillis();
        boundaryDirection = boundaryInfo.boundaryDirection;
        lastKnownGoodPosition = MeteorClient.mc.player.position();

        Logger.log("New chunk boundary detected - following trail for potential stash locations");

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Following new chunk boundary - potential player trail detected"));
        }
    }

    private static void controlFlight(Vec3 target) {
        if (MeteorClient.mc.player == null) return;

        Vec3 playerPos = MeteorClient.mc.player.position();
        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDistance > 1.0) {
            double yaw = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;
            double dy = target.y - playerPos.y;
            double pitch = Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI;

            pitch = Math.max(-30.0, Math.min(30.0, pitch));

            if (MeteorClient.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                pitch = Math.max(-15.0, Math.min(5.0, pitch));
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

        if (MeteorClient.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            boolean isGliding = MeteorClient.mc.player.isFallFlying();

            if (isGliding) {
                Vec3 forward = new Vec3(
                    -Math.sin(Math.toRadians(MeteorClient.mc.player.getYRot())),
                    -Math.sin(Math.toRadians(MeteorClient.mc.player.getXRot())) * 0.3,
                    Math.cos(Math.toRadians(MeteorClient.mc.player.getYRot()))
                ).normalize().scale(0.8);

                MeteorClient.mc.player.setDeltaMovement(forward);
            } else {
                if (MeteorClient.mc.player.getDeltaMovement().y < -0.5 && !MeteorClient.mc.player.onGround()) {
                    MeteorClient.mc.player.startFallFlying();
                }
            }
        }
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

    public static boolean isActive() {
        return active;
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
