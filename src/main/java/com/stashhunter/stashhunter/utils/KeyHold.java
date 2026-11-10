package com.stashhunter.stashhunter.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Presses keys on behalf of several modules at once. Presses are counted per key, so one module
 * releasing a key no longer cuts off another module's hold of the same key.
 */
public final class KeyHold {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Map<KeyMapping, Integer> pressCount = new HashMap<>();
    private static final List<KeyHold> timedHolds = new ArrayList<>();
    private static final Ticker TICKER = new Ticker();
    private static boolean subscribed = false;

    private final Object owner;
    private final KeyMapping key;
    private final int durationTicks;
    private final Runnable onComplete;
    private int ticksHeld = 0;

    private KeyHold(Object owner, KeyMapping key, int durationTicks, Runnable onComplete) {
        this.owner = owner;
        this.key = key;
        this.durationTicks = durationTicks;
        this.onComplete = onComplete;
    }

    /** Holds {@code key} for {@code durationTicks}, then runs {@code onCompleteCallback} (may be null). */
    public static void hold(Object owner, KeyMapping key, int durationTicks, Runnable onCompleteCallback) {
        if (!subscribed) {
            MeteorClient.EVENT_BUS.subscribe(TICKER);
            subscribed = true;
        }
        press(key);
        timedHolds.add(new KeyHold(owner, key, durationTicks, onCompleteCallback));
    }

    /** Ends all timed holds of {@code owner} without running their callbacks, e.g. when it is deactivated. */
    public static void cancel(Object owner) {
        timedHolds.removeIf(hold -> {
            if (hold.owner != owner) return false;
            release(hold.key);
            return true;
        });
    }

    /** Presses {@code key} until a matching {@link #release(KeyMapping)}. */
    public static void press(KeyMapping key) {
        pressCount.merge(key, 1, Integer::sum);
        key.setDown(true);
    }

    public static void release(KeyMapping key) {
        Integer count = pressCount.get(key);
        if (count == null) return;
        if (count > 1) {
            pressCount.put(key, count - 1);
        } else {
            pressCount.remove(key);
            key.setDown(false);
        }
    }

    private static final class Ticker {
        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (timedHolds.isEmpty()) return;
            boolean noWorld = mc.player == null || mc.level == null;

            // Callbacks may start new holds, so finish the expired ones outside the iteration.
            List<KeyHold> finished = new ArrayList<>();
            timedHolds.removeIf(hold -> {
                if (!noWorld && ++hold.ticksHeld < hold.durationTicks) return false;
                finished.add(hold);
                return true;
            });
            for (KeyHold hold : finished) {
                release(hold.key);
                if (hold.onComplete != null) hold.onComplete.run();
            }
        }
    }
}
