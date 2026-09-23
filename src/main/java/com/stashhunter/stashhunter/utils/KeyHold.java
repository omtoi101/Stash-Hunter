package com.stashhunter.stashhunter.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

import java.util.function.Consumer;

public class KeyHold {
    private static final Minecraft mc = Minecraft.getInstance();
    private KeyMapping key;
    private int durationTicks;
    private Consumer<Void> onComplete;
    private int ticksHeld;

    public static void hold(KeyMapping keyToHold, int durationTicks, Consumer<Void> onCompleteCallback) {
        new KeyHold(keyToHold, durationTicks, onCompleteCallback);
    }

    private KeyHold(KeyMapping key, int durationTicks, Consumer<Void> onComplete) {
        this.key = key;
        this.durationTicks = durationTicks;
        this.onComplete = onComplete;
        this.ticksHeld = 0;

        MeteorClient.EVENT_BUS.subscribe(this);
        KeyMapping.set(key.getDefaultKey(), true);
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
        KeyMapping.set(key.getDefaultKey(), false);
        MeteorClient.EVENT_BUS.unsubscribe(this);
        if (onComplete != null) {
            onComplete.accept(null);
        }
    }
}
