package com.stashhunter.stashhunter.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

public class KeyHold {
    private static final Minecraft mc = Minecraft.getInstance();
    private KeyMapping key;
    private int durationTicks;
    private Runnable onComplete;
    private int ticksHeld;

    public static void hold(KeyMapping keyToHold, int durationTicks, Runnable onCompleteCallback) {
        new KeyHold(keyToHold, durationTicks, onCompleteCallback);
    }

    private KeyHold(KeyMapping key, int durationTicks, Runnable onComplete) {
        this.key = key;
        this.durationTicks = durationTicks;
        this.onComplete = onComplete;
        this.ticksHeld = 0;

        MeteorClient.EVENT_BUS.subscribe(this);
        key.setDown(true);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            release();
            return;
        }

        ticksHeld++;

        if (ticksHeld >= durationTicks) {
            release();
        }
    }

    private void release() {
        key.setDown(false);
        MeteorClient.EVENT_BUS.unsubscribe(this);
        if (onComplete != null) {
            onComplete.run();
        }
    }
}
