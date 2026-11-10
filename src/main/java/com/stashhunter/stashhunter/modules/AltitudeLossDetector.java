package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.KeyHold;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
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

    // Opt-in: an unattended run gets no chat, but a falling bot shouldn't ping Discord by default.
    private final Setting<Boolean> discord = sgGeneral.add(new BoolSetting.Builder()
        .name("discord")
        .description("Send a notification to the Discord webhook configured in the Stash-Hunter module.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> discordCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("discord-cooldown")
        .description("Minimum seconds between two Discord notifications.")
        .defaultValue(60)
        .min(0)
        .sliderMax(600)
        .visible(discord::get)
        .build()
    );

    private double lastYPosition;
    private long lastCheckTime;
    private boolean fixInProgress = false;
    private int fixCooldown = 0;
    // Wall-clock and kept across toggles, so re-enabling the module can't bypass the cooldown.
    private long lastDiscordTime = 0;

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
        // A deliberate post-scan landing descends fast too; holding jump would fight Baritone.
        // The elytra repair's descent to the ground is just as deliberate.
        AutoElytraRepair repair = Modules.get().get(AutoElytraRepair.class);
        boolean repairing = repair != null && repair.isActive() && repair.isRepairing();
        boolean flying = (mc.player.isFallFlying() || ElytraController.isActive()) && !ElytraController.isLandingAfterCompletion() && !repairing;

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
        notifyDiscord();

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

    private void notifyDiscord() {
        if (!discord.get()) return;
        // Without auto-fix nothing else throttles detection, so a long fall would fire every second.
        long now = System.currentTimeMillis();
        if (lastDiscordTime != 0 && now - lastDiscordTime < discordCooldown.get() * 1000L) return;
        lastDiscordTime = now;

        String dimension = mc.level.dimension().identifier().getPath();
        DiscordEmbed embed = new DiscordEmbed(
            "Altitude Loss Detected",
            "Player " + mc.player.getName().getString() + " is rapidly losing altitude at "
                + mc.player.blockPosition().toShortString() + " in " + dimension + ".\n"
                + (autoFix.get() ? "Attempting to fix by holding jump." : "Manual intervention may be required."),
            0xFFAA00
        );
        DiscordWebhook.sendMessage("", embed);
    }
}
