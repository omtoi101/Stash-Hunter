package com.baseminer.basefinder.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.function.Consumer;

public class KeyHold {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private KeyBinding key;
    private int durationTicks;
    private Consumer<Void> onComplete;
    private int ticksHeld;

    public static void hold(KeyBinding keyToHold, int durationSeconds, Consumer<Void> onCompleteCallback) {
        new KeyHold(keyToHold, durationSeconds * 20, onCompleteCallback);
    }

    private KeyHold(KeyBinding key, int durationTicks, Consumer<Void> onComplete) {
        this.key = key;
        this.durationTicks = durationTicks;
        this.onComplete = onComplete;
        this.ticksHeld = 0;

        MeteorClient.EVENT_BUS.subscribe(this);
        KeyBinding.setKeyPressed(key.getDefaultKey(), true);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            release();
            return;
        }

        ticksHeld++;

        if (ticksHeld >= durationTicks) {
            release();
        }
    }

    private void release() {
        KeyBinding.setKeyPressed(key.getDefaultKey(), false);
        MeteorClient.EVENT_BUS.unsubscribe(this);
        if (onComplete != null) {
            onComplete.accept(null);
        }
    }
}
