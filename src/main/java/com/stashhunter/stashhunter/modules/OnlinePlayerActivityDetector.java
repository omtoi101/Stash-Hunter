package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.OnlinePlayerTracker;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OnlinePlayerActivityDetector extends Module {
    private static final int POLL_INTERVAL_TICKS = 10;
    private static final int MAX_NAMES = 10;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> chat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat")
        .description("Report joins and leaves of other players in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> countOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("count-only")
        .description("Only report the changed player count, without names.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show the number of online players next to the module name in the module list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> joinGrace = sgGeneral.add(new IntSetting.Builder()
        .name("join-grace")
        .description("Seconds after joining a server or changing dimension in which tab-list changes are not reported, so the initial tab-list fill doesn't spam.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> discord = sgGeneral.add(new BoolSetting.Builder()
        .name("discord")
        .description("Send joins/leaves to the Discord webhook configured in the Stash-Hunter module. Changes during the cooldown are batched into the next message.")
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

    private final OnlinePlayerTracker tracker = new OnlinePlayerTracker();
    // Batched for Discord while the cooldown runs; client thread only.
    private final List<String> pendingJoined = new ArrayList<>();
    private final List<String> pendingLeft = new ArrayList<>();
    private int pendingPreviousCount = -1;
    // Wall-clock and kept across toggles, so re-enabling the module can't bypass the cooldown.
    private long lastDiscordTime = 0;
    private int graceTicks;
    private int pollTicks;
    private Level lastLevel;
    private int onlineCount = -1;

    public OnlinePlayerActivityDetector() {
        super(StashHunter.CATEGORY, "online-player-activity",
            "Tracks the server's tab list and reports when other players join or leave, anywhere on the server.");
    }

    @Override
    public void onActivate() {
        resetTracking();
    }

    @Override
    public void onDeactivate() {
        resetTracking();
        clearPending();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetTracking();
        clearPending();
    }

    private void resetTracking() {
        tracker.reset();
        graceTicks = joinGrace.get() * 20;
        pollTicks = 0;
        lastLevel = null;
        onlineCount = -1;
    }

    private void clearPending() {
        pendingJoined.clear();
        pendingLeft.clear();
        pendingPreviousCount = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null || mc.level == null) return;

        // A proxy server switch or dimension change can rewrite the whole tab list at once;
        // treat it like a fresh join instead of reporting everyone as joined/left.
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            graceTicks = joinGrace.get() * 20;
        }
        boolean silent = graceTicks > 0;
        if (graceTicks > 0) graceTicks--;

        if (pollTicks-- > 0) return;
        pollTicks = POLL_INTERVAL_TICKS;

        Map<UUID, String> snapshot = new HashMap<>();
        // Listed only: NPC plugins add unlisted entries that come and go with tracking range.
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            snapshot.put(info.getProfile().id(), info.getProfile().name());
        }
        onlineCount = snapshot.size();

        OnlinePlayerTracker.Change change = tracker.update(snapshot, mc.player.getUUID(), silent);
        if (!change.isEmpty()) report(change);

        flushDiscord();
    }

    private void report(OnlinePlayerTracker.Change change) {
        if (chat.get()) {
            if (countOnly.get()) {
                info("Other players online: %d -> %d.", change.previousCount(), change.count());
            } else {
                if (!change.joined().isEmpty()) info("Joined: %s (%d others online).",
                    OnlinePlayerTracker.formatNames(change.joined(), MAX_NAMES), change.count());
                if (!change.left().isEmpty()) info("Left: %s (%d others online).",
                    OnlinePlayerTracker.formatNames(change.left(), MAX_NAMES), change.count());
            }
        }

        if (discord.get()) {
            if (pendingPreviousCount < 0) pendingPreviousCount = change.previousCount();
            pendingJoined.addAll(change.joined());
            pendingLeft.addAll(change.left());
        }
    }

    private void flushDiscord() {
        if (!discord.get() || (pendingJoined.isEmpty() && pendingLeft.isEmpty())) return;
        long now = System.currentTimeMillis();
        if (lastDiscordTime != 0 && now - lastDiscordTime < discordCooldown.get() * 1000L) return;
        lastDiscordTime = now;

        int count = tracker.count();
        StringBuilder description = new StringBuilder();
        description.append("Other players online: ").append(pendingPreviousCount).append(" -> ").append(count).append('.');
        if (!countOnly.get()) {
            if (!pendingJoined.isEmpty()) description.append("\nJoined: ")
                .append(OnlinePlayerTracker.formatNames(pendingJoined, MAX_NAMES));
            if (!pendingLeft.isEmpty()) description.append("\nLeft: ")
                .append(OnlinePlayerTracker.formatNames(pendingLeft, MAX_NAMES));
        }
        // Someone showing up on an empty server is the case worth noticing; colour it as a warning.
        int color = pendingPreviousCount == 0 && count > 0 ? 0xFF5555 : 0x55AAFF;
        DiscordWebhook.sendMessage("", new DiscordEmbed("Online Player Activity", description.toString(), color));
        StashHunter.LOG.info("Online player activity sent to Discord: {}", description);
        clearPending();
    }

    @Override
    public String getInfoString() {
        if (!showCount.get() || onlineCount < 0) return null;
        return String.valueOf(onlineCount);
    }
}
