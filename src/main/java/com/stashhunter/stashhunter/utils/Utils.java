package com.stashhunter.stashhunter.utils;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.math.BlockPos;

public class Utils {
    public static boolean isPlayerAt(BlockPos pos, int tolerance) {
        if (MeteorClient.mc.player == null) return false;
        return Math.abs(MeteorClient.mc.player.getX() - pos.getX()) <= tolerance &&
               Math.abs(MeteorClient.mc.player.getZ() - pos.getZ()) <= tolerance;
    }
}
