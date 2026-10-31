package com.stashhunter.stashhunter.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Decodes a chunk packet into a private LevelChunk copy once and shares it between the modules that
 * analyse chunk packets on the Netty thread (NewerNewChunks, ActivatedSpawnerDetector). Vanilla
 * decodes its own copy anyway, so every extra decode was a second full read of all sections.
 * <p>
 * The copy has no block entities: in a chunk of a client level, creating one registers it and its
 * ticker in the real level (LevelChunk.isInLevel() is always true client-side) - from the Netty
 * thread, and nothing ever removes them again.
 * <p>
 * The copy is only valid while the packet event is being handled; callers must not keep it.
 */
public final class ChunkPacketDecoder {
    private static ClientboundLevelChunkWithLightPacket cachedPacket;
    private static ClientLevel cachedLevel;
    private static LevelChunk cachedChunk; // null if decoding the cached packet failed

    private ChunkPacketDecoder() {
    }

    /** Subscribes the listener that drops the cached copy once every module has seen the packet. */
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(new Listener());
    }

    /** The decoded chunk, or null if the packet could not be decoded (a half-decoded chunk only gives false positives). */
    public static synchronized LevelChunk decode(ClientLevel level, ClientboundLevelChunkWithLightPacket packet) {
        if (packet == cachedPacket && level == cachedLevel) return cachedChunk;

        LevelChunk chunk = new LevelChunk(level, new ChunkPos(packet.getX(), packet.getZ()));
        try {
            chunk.replaceWithPacketData(packet.getChunkData().getReadBuffer(), packet.getChunkData().getHeightmaps(), output -> {});
        } catch (RuntimeException e) {
            chunk = null;
        }
        cachedPacket = packet;
        cachedLevel = level;
        cachedChunk = chunk;
        return chunk;
    }

    // Holding on to the copy would keep its level alive after leaving the world.
    private static synchronized void clear() {
        cachedPacket = null;
        cachedLevel = null;
        cachedChunk = null;
    }

    private static final class Listener {
        // Lowest priority: runs after the modules' own handlers for the same packet. If another
        // listener cancels the event first, the copy stays until the next packet or GameLeftEvent.
        @EventHandler(priority = EventPriority.LOWEST)
        private void onReceive(PacketEvent.Receive event) {
            if (event.packet instanceof ClientboundLevelChunkWithLightPacket) clear();
        }

        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            clear();
        }
    }
}
