package com.baseminer.basefinder.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ElytraController {
    private static List<Vec3d> waypoints = new ArrayList<>();
    private static int currentWaypoint = 0;
    private static boolean active = false;
    private static Vec3d currentTarget = null;
    private static long lastWaypointTime = 0;

    public static void start(int x1, int z1, int x2, int z2, int stripWidth) {
        waypoints.clear();
        currentWaypoint = 0;
        active = true;
        lastWaypointTime = System.currentTimeMillis();

        // Generate waypoints in a lawnmower pattern
        generateWaypoints(x1, z1, x2, z2, stripWidth);

        if (!waypoints.isEmpty()) {
            // Ensure player has elytra equipped
            if (MeteorClient.mc.player != null &&
                MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                MeteorClient.mc.player.sendMessage(
                    net.minecraft.text.Text.of("§cPlease equip an Elytra before starting!"), false);
                stop();
                return;
            }

            // Enable Meteor's ElytraFly module if available
            ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
            if (elytraFly != null && !elytraFly.isActive()) {
                elytraFly.toggle();
            }

            flyToNextWaypoint();
        }
    }

    private static void generateWaypoints(int x1, int z1, int x2, int z2, int stripWidth) {
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

        // Try to disable ElytraFly if it was enabled
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null && elytraFly.isActive()) {
            elytraFly.toggle();
        }

        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.sendMessage(
                net.minecraft.text.Text.of("§aBase finder flight stopped"), false);
        }
    }

    public static void onTick() {
        if (!active || MeteorClient.mc.player == null || waypoints.isEmpty()) {
            return;
        }

        // Check if we've completed all waypoints
        if (currentWaypoint >= waypoints.size()) {
            if (MeteorClient.mc.player != null) {
                MeteorClient.mc.player.sendMessage(
                    net.minecraft.text.Text.of("§aCompleted scanning area!"), false);
            }
            stop();
            return;
        }

        Vec3d playerPos = MeteorClient.mc.player.getPos();
        Vec3d target = waypoints.get(currentWaypoint);
        currentTarget = target;

        // Check if we're close enough to the current waypoint (horizontal distance only)
        double horizontalDistance = Math.sqrt(
            Math.pow(target.x - playerPos.x, 2) +
            Math.pow(target.z - playerPos.z, 2)
        );

        if (horizontalDistance < 20.0) { // Increased threshold for more reliable waypoint progression
            flyToNextWaypoint();
        } else {
            // Check if we're stuck (haven't made progress in a while)
            if (System.currentTimeMillis() - lastWaypointTime > 45000) { // Increased timeout to 45 seconds
                if (MeteorClient.mc.player != null) {
                    MeteorClient.mc.player.sendMessage(
                        net.minecraft.text.Text.of("§cStuck at waypoint, skipping..."), false);
                }
                flyToNextWaypoint();
            }

            // Only control flight if we're reasonably close to the target altitude
            double altitudeDifference = Math.abs(playerPos.y - target.y);
            if (altitudeDifference < 100) { // Only control if within reasonable altitude range
                controlFlight(target);
            } else {
                // If too far from target altitude, just fly horizontally
                Vec3d horizontalTarget = new Vec3d(target.x, playerPos.y, target.z);
                controlFlight(horizontalTarget);
            }
        }
    }

    private static void controlFlight(Vec3d target) {
        if (MeteorClient.mc.player == null) return;

        Vec3d playerPos = MeteorClient.mc.player.getPos();

        // Calculate horizontal direction to target
        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Only update direction if we have a reasonable horizontal distance
        if (horizontalDistance > 1.0) {
            // Calculate yaw (horizontal direction)
            double yaw = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;

            // Calculate pitch (vertical angle) - limit it to prevent looking straight up/down
            double dy = target.y - playerPos.y;
            double pitch = Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI;

            // Clamp pitch to reasonable values (-30 to +30 degrees)
            pitch = Math.max(-30.0, Math.min(30.0, pitch));

            // For elytra flight, prefer slightly downward angle for better gliding
            if (MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                pitch = Math.max(-15.0, Math.min(5.0, pitch));
            }

            // Apply rotation smoothly
            float currentYaw = MeteorClient.mc.player.getYaw();
            float currentPitch = MeteorClient.mc.player.getPitch();

            // Smooth rotation to prevent sudden snapping
            float yawDiff = (float) (yaw - currentYaw);
            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;

            float newYaw = currentYaw + yawDiff * 0.1f; // Smooth turning
            float newPitch = currentPitch + ((float) pitch - currentPitch) * 0.1f;

            MeteorClient.mc.player.setYaw(newYaw);
            MeteorClient.mc.player.setPitch(newPitch);
        }

        // Handle elytra flight
        if (MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            boolean isGliding = MeteorClient.mc.player.isGliding();

            if (isGliding) {
                // Apply consistent forward velocity for elytra
                Vec3d forward = new Vec3d(
                    -Math.sin(Math.toRadians(MeteorClient.mc.player.getYaw())),
                    -Math.sin(Math.toRadians(MeteorClient.mc.player.getPitch())) * 0.3, // Reduced vertical component
                    Math.cos(Math.toRadians(MeteorClient.mc.player.getYaw()))
                ).normalize().multiply(0.8); // Consistent speed

                MeteorClient.mc.player.setVelocity(forward);
            } else {
                // Try to start elytra flight if falling
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

        if (currentWaypoint >= waypoints.size()) {
            return "Completed";
        }

        return "Flying to waypoint " + currentWaypoint + "/" + waypoints.size();
    }
}
