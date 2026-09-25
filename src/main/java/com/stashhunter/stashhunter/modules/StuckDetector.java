package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.KeyHold;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

public class StuckDetector extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<String> discordWebhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("discord-webhook-url")
        .description("The Discord webhook URL to send notifications to.")
        .defaultValue(Config.stuckDetectorWebhookUrl)
        .onChanged(v -> {
            Config.stuckDetectorWebhookUrl = v;
            Config.save();
        })
        .build()
    );

    private final Setting<Integer> detectionThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("detection-threshold")
        .description("The time in seconds a player needs to be motionless before being considered stuck.")
        .defaultValue(Config.stuckDetectorThreshold)
        .min(1)
        .sliderMax(10)
        .onChanged(v -> {
            Config.stuckDetectorThreshold = v;
            Config.save();
        })
        .build()
    );

    private final Setting<Boolean> autoFix = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-fix")
        .description("Automatically tries to fix the rubber-banding by toggling ElytraFly.")
        .defaultValue(Config.stuckDetectorAutoFix)
        .onChanged(v -> {
            Config.stuckDetectorAutoFix = v;
            Config.save();
        })
        .build()
    );

    private Vec3 lastPosition;
    private int stationaryTicks = 0;
    private boolean fixInProgress = false;
    private int fixCooldown = 0;

    // Auto-fix runs as a small tick-driven state machine so all game state is touched on the client thread.
    private FixStage fixStage = FixStage.NONE;
    private int fixTicks = 0;
    private Vec3 positionWhenStuck;

    private enum FixStage {
        NONE,
        REENABLE_ELYTRA_FLY,
        CHECK_RECOVERY,
        HOLDING_JUMP
    }

    public StuckDetector() {
        super(StashHunter.CATEGORY, "stuck-detector", "Detects when you are stuck in an elytra rubber-band loop and tries to fix it.");
    }

    @Override
    public void onActivate() {
        lastPosition = null;
        stationaryTicks = 0;
        fixInProgress = false;
        fixCooldown = 0;
        fixStage = FixStage.NONE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (fixInProgress) {
            tickFix();
            return;
        }

        if (fixCooldown > 0) {
            fixCooldown--;
            return;
        }

        // Check if player is gliding
        if (!mc.player.isFallFlying()) {
            stationaryTicks = 0; // Reset if not gliding
            return;
        }

        if (lastPosition != null) {
            double distance = mc.player.position().distanceTo(lastPosition);
            if (distance < 0.1) {
                stationaryTicks++;
            } else {
                stationaryTicks = 0;
            }
        }
        lastPosition = mc.player.position();

        // 20 ticks per second
        if (stationaryTicks > detectionThreshold.get() * 20) {
            handleStuck();
            stationaryTicks = 0;
        }
    }

    private void handleStuck() {
        info("Detected elytra rubber-banding at " + mc.player.blockPosition().toShortString());

        // Send Discord notification
        if (!discordWebhookUrl.get().isEmpty()) {
            DiscordEmbed embed = new DiscordEmbed(
                "Elytra Stuck Detected!",
                "Player " + mc.player.getName().getString() + " is stuck in an elytra rubber-band loop at " +
                mc.player.blockPosition().toShortString() + ".\n" +
                (autoFix.get() ? "Attempting to fix automatically." : "Manual intervention may be required."),
                0xFF0000
            );
            DiscordWebhook.sendMessage(discordWebhookUrl.get(), "", embed);
        }

        if (!autoFix.get()) return;

        fixInProgress = true;
        positionWhenStuck = mc.player.position();

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null && elytraFly.isActive()) {
            info("Attempting Fix 1: Toggling ElytraFly module...");
            elytraFly.toggle();
            fixStage = FixStage.REENABLE_ELYTRA_FLY;
            fixTicks = 20; // 1 second
        } else {
            // ElytraFly not active, go straight to stopping flight
            info("ElytraFly not active. Attempting to get unstuck by stopping flight...");
            mc.player.stopFallFlying();
            finishFix();
        }
    }

    private void tickFix() {
        if (fixStage == FixStage.HOLDING_JUMP || --fixTicks > 0) return;

        switch (fixStage) {
            case REENABLE_ELYTRA_FLY -> {
                ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
                if (elytraFly != null && !elytraFly.isActive()) elytraFly.toggle();
                info("ElytraFly re-enabled. Monitoring for recovery...");
                fixStage = FixStage.CHECK_RECOVERY;
                fixTicks = 40; // Wait 2 seconds to see if we start moving
            }
            case CHECK_RECOVERY -> {
                if (mc.player.position().distanceTo(positionWhenStuck) < 1.0) {
                    info("Fix 1 seems to have failed. Attempting Fix 2: Stopping vanilla flight...");
                    mc.player.stopFallFlying();

                    info("Attempting Fix 3: Holding jump...");
                    fixStage = FixStage.HOLDING_JUMP;
                    KeyHold.hold(mc.options.keyJump, 5, () -> {
                        info("Jump complete.");
                        finishFix();
                    });
                } else {
                    info("Fix 1 appears successful. No further action needed.");
                    finishFix();
                }
            }
            default -> finishFix();
        }
    }

    private void finishFix() {
        fixInProgress = false;
        fixStage = FixStage.NONE;
        fixCooldown = 200;
    }
}
