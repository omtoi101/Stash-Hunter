package com.baseminer.basefinder.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.function.Consumer;

public class KeyHold {
    private static final KeyHold INSTANCE = new KeyHold();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private KeyBinding key;
    private int durationTicks;
    private Consumer<Void> onComplete;
    private int ticksHeld;
    private boolean holding;

    private KeyHold() {
        // Private constructor for singleton
    }

    public static void register() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    public static void hold(KeyBinding keyToHold, int durationTicks, Consumer<Void> onCompleteCallback) {
        INSTANCE.startHold(keyToHold, durationTicks, onCompleteCallback);
    }

    private void startHold(KeyBinding keyToHold, int duration, Consumer<Void> onCompleteCallback) {
        if (holding) {
            // Already holding a key, release it first
            release();
        }

        this.key = keyToHold;
        this.durationTicks = duration;
        this.onComplete = onCompleteCallback;
        this.ticksHeld = 0;
        this.holding = true;

        KeyBinding.setKeyPressed(this.key.boundKey, true);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!holding || mc.player == null || mc.world == null) {
            if (holding) {
                release();
            }
            return;
        }

        ticksHeld++;

        if (ticksHeld >= durationTicks) {
            release();
        }
    }

    private void release() {
        if (key != null) {
            KeyBinding.setKeyPressed(key.boundKey, false);
        }
        if (onComplete != null) {
            onComplete.accept(null);
        }
        this.holding = false;
        this.key = null;
        this.onComplete = null;
        this.durationTicks = 0;
        this.ticksHeld = 0;
    }
}
