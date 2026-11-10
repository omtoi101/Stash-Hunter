package com.stashhunter.stashhunter.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** File-name-safe name of the current server (or singleplayer world), for per-server data files. */
public final class ServerKey {
    private ServerKey() {}

    public static String current() {
        Minecraft mc = Minecraft.getInstance();
        String key;
        if (mc.hasSingleplayerServer()) {
            Path world = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            key = "singleplayer_" + (world != null ? world : "world");
        } else {
            key = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "unknown";
        }
        return key.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
