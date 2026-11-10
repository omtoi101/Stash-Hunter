package com.stashhunter.stashhunter.utils;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Looks for player trails (lines or a consistent direction) in the new chunks around a position. */
public final class TrailAnalysis {
    static final int TRAIL_ANALYSIS_RADIUS = 5; // chunks to analyze around player
    static final int MAX_LINEAR_PATTERN_CHUNKS = 24; // caps the O(n^3) collinearity search

    private TrailAnalysis() {}

    public static final class Trail {
        public Vec3 direction;
        public double confidence; // 0.0 to 1.0
        public String type; // "corridor", "line", "scattered", etc.
    }

    public static Trail analyze(Vec3 playerPos, Predicate<ChunkPos> isNewChunk) {
        ChunkPos playerChunk = new ChunkPos(Mth.floor(playerPos.x) >> 4, Mth.floor(playerPos.z) >> 4);

        // Find new chunks within analysis radius by probing the (small) neighbourhood instead of
        // iterating every new chunk ever detected.
        List<ChunkPos> nearbyNewChunks = new ArrayList<>();
        int radiusSq = TRAIL_ANALYSIS_RADIUS * TRAIL_ANALYSIS_RADIUS;
        for (int dx = -TRAIL_ANALYSIS_RADIUS; dx <= TRAIL_ANALYSIS_RADIUS; dx++) {
            for (int dz = -TRAIL_ANALYSIS_RADIUS; dz <= TRAIL_ANALYSIS_RADIUS; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                ChunkPos chunk = new ChunkPos(playerChunk.x() + dx, playerChunk.z() + dz);
                if (isNewChunk.test(chunk)) {
                    nearbyNewChunks.add(chunk);
                }
            }
        }

        if (nearbyNewChunks.size() < 3) {
            return null;
        }

        // Analyze patterns in nearby new chunks
        Trail bestTrail = null;
        double bestConfidence = 0;

        // Look for linear patterns (corridors, tunnels, roads)
        Trail linearTrail = analyzeLinearPattern(nearbyNewChunks, playerPos);
        if (linearTrail != null && linearTrail.confidence > bestConfidence) {
            bestTrail = linearTrail;
            bestConfidence = linearTrail.confidence;
        }

        // Look for directional patterns (consistent direction of travel)
        Trail directionalTrail = analyzeDirectionalPattern(nearbyNewChunks, playerPos);
        if (directionalTrail != null && directionalTrail.confidence > bestConfidence) {
            bestTrail = directionalTrail;
            bestConfidence = directionalTrail.confidence;
        }

        return bestTrail;
    }

    static Trail analyzeLinearPattern(List<ChunkPos> nearbyChunks, Vec3 playerPos) {
        // Only the chunks closest to the player matter for steering; capping keeps this bounded.
        List<ChunkPos> chunks = sortedByDistance(nearbyChunks, playerPos);
        if (chunks.size() > MAX_LINEAR_PATTERN_CHUNKS) {
            chunks = chunks.subList(0, MAX_LINEAR_PATTERN_CHUNKS);
        }

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
                        Trail trail = new Trail();
                        trail.confidence = linearity;
                        trail.type = "corridor";

                        // Calculate direction from first to last chunk
                        double dx = c3.x() - c1.x();
                        double dz = c3.z() - c1.z();
                        trail.direction = new Vec3(dx, 0, dz).normalize();

                        return trail;
                    }
                }
            }
        }
        return null;
    }

    static Trail analyzeDirectionalPattern(List<ChunkPos> nearbyChunks, Vec3 playerPos) {
        // Look for chunks that show consistent directional movement
        if (nearbyChunks.size() < 4) return null;

        List<ChunkPos> chunks = sortedByDistance(nearbyChunks, playerPos);

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
                Trail trail = new Trail();
                trail.direction = finalDirection.normalize();
                trail.confidence = consistency;
                trail.type = "directional";

                return trail;
            }
        }

        return null;
    }

    // Returns a sorted copy, so callers' lists are never reordered.
    private static List<ChunkPos> sortedByDistance(List<ChunkPos> chunks, Vec3 playerPos) {
        List<ChunkPos> sorted = new ArrayList<>(chunks);
        sorted.sort(Comparator.comparingDouble(c -> {
            double dx = c.x() * 16 - playerPos.x;
            double dz = c.z() * 16 - playerPos.z;
            return dx * dx + dz * dz;
        }));
        return sorted;
    }

    static double calculateLinearity(ChunkPos c1, ChunkPos c2, ChunkPos c3) {
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
}
