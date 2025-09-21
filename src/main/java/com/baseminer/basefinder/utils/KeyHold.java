package com.baseminer.basefinder.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.function.Consumer;

public class KeyHold {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static KeyBinding key;
    private static int durationTicks;
    private static Consumer<Void> onComplete;
    private static int ticksHeld;

    public static void hold(KeyBinding keyToHold, int durationSeconds, Consumer<Void> onCompleteCallback) {
        key = keyToHold;
        durationTicks = durationSeconds * 20;
        onComplete = onCompleteCallback;
        ticksHeld = 0;

        KeyBinding.setKeyPressed(key.getDefaultKey(), true);
    }

    @EventHandler
    private static void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            release();
            return;
        }

        ticksHeld++;

        if (ticksHeld >= durationTicks) {
            release();
        }
    }

    private static void release() {
        KeyBinding.setKeyPressed(key.getDefaultKey(), false);
        if (onComplete != null) {
            onComplete.accept(null);
        }
    }
}
