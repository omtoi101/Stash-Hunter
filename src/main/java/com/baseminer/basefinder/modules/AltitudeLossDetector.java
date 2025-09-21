package com.baseminer.basefinder.modules;

import com.baseminer.basefinder.BaseFinder;
import com.baseminer.basefinder.utils.KeyHold;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;


public class AltitudeLossDetector extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> altitudeDropThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("altitude-drop-threshold")
        .description("The number of blocks the player has to fall in one second to trigger the detector.")
        .defaultValue(10)
        .min(1)
        .sliderMax(50)
        .build()
    );

    private final Setting<Boolean> autoFix = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-fix")
        .description("Automatically tries to fix the issue by holding spacebar.")
        .defaultValue(true)
        .build()
    );

    private double lastYPosition;
    private long lastCheckTime;
    private boolean fixInProgress = false;
    private int fixCooldown = 0;

    public AltitudeLossDetector() {
        super(BaseFinder.CATEGORY, "altitude-loss-detector", "Detects when you are rapidly losing altitude and tries to fix it.");
    }

    @Override
    public void onActivate() {
        lastYPosition = mc.player != null ? mc.player.getY() : 0;
        lastCheckTime = System.currentTimeMillis();
        fixInProgress = false;
        fixCooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (fixCooldown > 0) {
            fixCooldown--;
            return;
        }

        if (fixInProgress) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime >= 1000) {
            double currentYPosition = mc.player.getY();
            double altitudeDrop = lastYPosition - currentYPosition;

            if (altitudeDrop > altitudeDropThreshold.get()) {
                handleStuck();
            }

            lastYPosition = currentYPosition;
            lastCheckTime = currentTime;
        }
    }

    private void handleStuck() {
        info("Detected rapid altitude loss at " + mc.player.getBlockPos().toShortString());

        if (autoFix.get()) {
            fixInProgress = true;
            info("Attempting to fix by holding jump...");
            KeyHold.hold(mc.options.jumpKey, 100, (v) -> {
                info("Jump complete.");
                fixInProgress = false;
                fixCooldown = 200;
            });
        }
    }
}
