package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.DangerCheck;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.ElytraController;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class AutoLogout extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> playerTrigger = sgGeneral.add(new BoolSetting.Builder()
        .name("player-nearby")
        .description("Disconnect when another player comes within the radius.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Distance in blocks at which a nearby player triggers the disconnect.")
        .defaultValue(32)
        .min(1)
        .sliderMax(128)
        .visible(playerTrigger::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not disconnect for players on your Meteor friends list.")
        .defaultValue(true)
        .visible(playerTrigger::get)
        .build()
    );

    private final Setting<Boolean> healthTrigger = sgGeneral.add(new BoolSetting.Builder()
        .name("low-health")
        .description("Disconnect when your health drops below the threshold.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minHealth = sgGeneral.add(new IntSetting.Builder()
        .name("min-health")
        .description("Disconnect when health is below this many half-hearts (20 = full health).")
        .defaultValue(10)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(healthTrigger::get)
        .build()
    );

    private final Setting<Boolean> discord = sgGeneral.add(new BoolSetting.Builder()
        .name("discord")
        .description("Send a notification to the Discord webhook configured in the Stash-Hunter module before disconnecting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableAfter = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-after-logout")
        .description("Turn this module off after it disconnects, so reconnecting does not log out again immediately.")
        .defaultValue(true)
        .build()
    );

    // Set once per connection: the connection closes asynchronously, so later ticks of the same
    // session must not send a second Discord message or disconnect again. Client thread only.
    private boolean triggered = false;
    // Toggled off on the next tick instead of unsubscribing from inside the TickEvent being handled.
    private boolean pendingDeactivate = false;

    public AutoLogout() {
        super(StashHunter.CATEGORY, "auto-logout",
            "RISKY: disconnects from the server when a non-friend player comes near or health gets low. "
                + "Some servers treat this as combat logging.");
    }

    @Override
    public void onActivate() {
        triggered = false;
        pendingDeactivate = false;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // Re-arm for the next connection when the module stays on.
        triggered = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Before the null check: after the disconnect there is no player anymore.
        if (pendingDeactivate) {
            pendingDeactivate = false;
            if (isActive()) toggle();
            return;
        }
        if (triggered || mc.player == null || mc.level == null || mc.getConnection() == null) return;

        String reason = DangerCheck.reason(
            playerTrigger.get(), playerTrigger.get() ? nearestPlayerDistance() : DangerCheck.NO_PLAYER, radius.get(),
            healthTrigger.get(), mc.player.getHealth(), minHealth.get());
        if (reason != null) logout(reason);
    }

    private double nearestPlayerDistance() {
        double nearest = DangerCheck.NO_PLAYER;
        for (Player player : mc.level.players()) {
            // Blink and FakePlayer spawn client-side copies of us; they are not a threat.
            if (player == mc.player || player instanceof FakePlayerEntity) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            nearest = Math.min(nearest, mc.player.distanceTo(player));
        }
        return nearest;
    }

    private void logout(String reason) {
        triggered = true;
        String position = mc.player.blockPosition().toShortString();
        StashHunter.LOG.warn("AutoLogout: disconnecting at {} ({})", position, reason);
        error("Auto logout: " + reason + ".");

        if (discord.get()) {
            String dimension = mc.level.dimension().identifier().getPath();
            DiscordWebhook.sendMessage("@everyone", new DiscordEmbed(
                "Auto Logout",
                "Player " + mc.player.getName().getString() + " disconnected at " + position + " in " + dimension
                    + ": " + reason + ".",
                0xFF0000
            ));
        }

        // Saves the trip so the flight can be resumed after reconnecting.
        if (ElytraController.isActive()) ElytraController.pause();
        mc.getConnection().getConnection().disconnect(Component.literal("Auto logout: " + reason));

        if (disableAfter.get()) pendingDeactivate = true;
    }
}
