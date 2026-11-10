package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.AlarmSchedule;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.TreeSet;

public class PlayerAlarms extends Module {
    public enum AlarmSound {
        Bell,
        Pling,
        Anvil,
        LevelUp,
        Orb;

        SoundEvent event() {
            return switch (this) {
                case Bell -> SoundEvents.BELL_BLOCK;
                case Pling -> SoundEvents.NOTE_BLOCK_PLING.value();
                case Anvil -> SoundEvents.ANVIL_LAND;
                case LevelUp -> SoundEvents.PLAYER_LEVELUP;
                case Orb -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            };
        }
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Ring while another player is closer than this many blocks.")
        .defaultValue(100)
        .min(1)
        .sliderMax(256)
        .build()
    );

    private final Setting<AlarmSound> sound = sgGeneral.add(new EnumSetting.Builder<AlarmSound>()
        .name("sound")
        .description("Vanilla sound played as the alarm.")
        .defaultValue(AlarmSound.Bell)
        .build()
    );

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
        .name("volume")
        .description("Alarm volume, additionally scaled by the game's sound sliders.")
        .defaultValue(1.0)
        .range(0.1, 1.0)
        .sliderRange(0.1, 1.0)
        .build()
    );

    private final Setting<Integer> repeatInterval = sgGeneral.add(new IntSetting.Builder()
        .name("repeat-interval")
        .description("Seconds between two rings while a player stays in range.")
        .defaultValue(3)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not ring for players on your Meteor friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat")
        .description("Print the players in range to chat whenever that set changes.")
        .defaultValue(true)
        .build()
    );

    // Action bar instead of a title: setTimes/setTitle would overwrite a server's title and its timings.
    private final Setting<Boolean> actionBar = sgGeneral.add(new BoolSetting.Builder()
        .name("action-bar")
        .description("Show the players in range in the action bar with every ring.")
        .defaultValue(false)
        .build()
    );

    private final AlarmSchedule schedule = new AlarmSchedule();
    // Chat only on changes of this set, so the repeating ring doesn't flood it. Client thread only.
    private Set<String> announced = new TreeSet<>();

    public PlayerAlarms() {
        super(StashHunter.CATEGORY, "player-alarms",
            "Plays a repeating local alarm sound while another player is nearby, for unattended (AFK) use.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
    }

    private void reset() {
        schedule.reset();
        announced = new TreeSet<>();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        Set<String> inRange = new TreeSet<>();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            String name = player.getName().getString();
            if (AlarmSchedule.inRange(mc.player.distanceToSqr(player), radius.get(), announced.contains(name))) {
                inRange.add(name);
            }
        }

        if (chat.get() && !inRange.equals(announced)) {
            if (inRange.isEmpty()) info("No players in range anymore.");
            else warning("Players in range: " + String.join(", ", inRange));
        }
        announced = inRange;

        // Monotonic, so a system clock change can't stall or re-trigger the alarm.
        long nowMs = System.nanoTime() / 1_000_000L;
        if (!schedule.update(!inRange.isEmpty(), nowMs, repeatInterval.get() * 1000L)) return;

        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound.get().event(), 1.0f, volume.get().floatValue()));
        if (actionBar.get()) {
            mc.gui.hud.setOverlayMessage(Component.literal("Player nearby: " + String.join(", ", inRange)), false);
        }
    }
}
