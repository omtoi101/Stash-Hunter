package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.KeyHold;
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
        super(StashHunter.CATEGORY, "altitude-loss-detector", "Detects when you are rapidly losing altitude and tries to fix it.");
    }

    @Override
    public void onActivate() {
        lastYPosition = mc.player != null ? mc.player.getY() : 0;
        lastCheckTime = System.currentTimeMillis();
        fixInProgress = false;
        fixCooldown = 0;
    }

    @Override
    public void onDeactivate() {
        // The hold's callback would otherwise be the only thing that clears fixInProgress.
        KeyHold.cancel(this);
        fixInProgress = false;
        fixCooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Only elytra flight counts: walking off a ledge or a normal fall is no altitude loss.
        // While the bot flies, a closed elytra falling is exactly the case to recover from.
        boolean flying = mc.player.isFallFlying() || ElytraController.isActive();

        // Keep the reference fresh while not checking, so the first check afterwards doesn't
        // compare against a Y from before the cooldown/fix and fire on that stale drop.
        if (fixCooldown > 0 || fixInProgress || !flying) {
            if (fixCooldown > 0) fixCooldown--;
            lastYPosition = mc.player.getY();
            lastCheckTime = currentTime;
            return;
        }

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
        info("Detected rapid altitude loss at " + mc.player.blockPosition().toShortString());

        if (autoFix.get()) {
            fixInProgress = true;

            // If Baritone is mid-path for our flight, cancel it first so its own movement inputs
            // don't fight the jump-hold recovery below. Leave paths the user started alone.
            if (ElytraController.isActive()) BaritoneBridge.cancel();

            info("Attempting to fix by holding jump...");
            KeyHold.hold(this, mc.options.keyJump, 100, () -> {
                info("Jump complete.");
                fixInProgress = false;
                fixCooldown = 200;
            });
        }
    }
}
