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
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

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

    private Vec3d lastPosition;
    private int stationaryTicks = 0;
    private boolean fixInProgress = false;
    private int fixCooldown = 0;

    public StuckDetector() {
        super(StashHunter.CATEGORY, "stuck-detector", "Detects when you are stuck in an elytra rubber-band loop and tries to fix it.");
    }

    @Override
    public void onActivate() {
        lastPosition = null;
        stationaryTicks = 0;
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

        // Check if player is gliding
        if (!mc.player.isGliding()) {
            stationaryTicks = 0; // Reset if not gliding
            return;
        }

        if (lastPosition != null) {
            double distance = mc.player.getPos().distanceTo(lastPosition);
            if (distance < 0.1) {
                stationaryTicks++;
            } else {
                stationaryTicks = 0;
            }
        }
        lastPosition = mc.player.getPos();

        // 20 ticks per second
        if (stationaryTicks > detectionThreshold.get() * 20) {
            handleStuck();
            stationaryTicks = 0;
        }
    }

    private void handleStuck() {
        info("Detected elytra rubber-banding at " + mc.player.getBlockPos().toShortString());

        // Send Discord notification
        if (!discordWebhookUrl.get().isEmpty()) {
            DiscordEmbed embed = new DiscordEmbed(
                "Elytra Stuck Detected!",
                "Player " + mc.player.getName().getString() + " is stuck in an elytra rubber-band loop at " +
                mc.player.getBlockPos().toShortString() + ".\n" +
                (autoFix.get() ? "Attempting to fix automatically." : "Manual intervention may be required."),
                0xFF0000
            );
            new Thread(() -> DiscordWebhook.sendMessage("", embed)).start();
        }

        if (autoFix.get()) {
            fixInProgress = true;
            final Vec3d positionWhenStuck = mc.player.getPos(); // Capture position for later check

            new Thread(() -> {
                try {
                    ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
                    boolean wasActive = elytraFly.isActive();

                    if (wasActive) {
                        info("Attempting Fix 1: Toggling ElytraFly module...");
                        elytraFly.toggle();
                        Thread.sleep(1000);
                        elytraFly.toggle();
                        info("ElytraFly re-enabled. Monitoring for recovery...");
                        Thread.sleep(2000); // Wait 2 seconds to see if we start moving

                        if (mc.player.getPos().distanceTo(positionWhenStuck) < 1.0) {
                            info("Fix 1 seems to have failed. Attempting Fix 2: Stopping vanilla flight...");
                            mc.player.stopGliding();

                            info("Attempting Fix 3: Holding jump...");
                            KeyHold.hold(mc.options.jumpKey, 5, (v) -> {
                                info("Jump complete.");
                                fixInProgress = false;
                                fixCooldown = 200;
                            });

                        } else {
                            info("Fix 1 appears successful. No further action needed.");
                            fixInProgress = false;
                            fixCooldown = 200;
                        }
                    } else {
                        // ElytraFly not active, go straight to stopping flight
                        info("ElytraFly not active. Attempting to get unstuck by stopping flight...");
                        mc.player.stopGliding();
                        fixInProgress = false;
                        fixCooldown = 200;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
