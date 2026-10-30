package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.UnfocusedFps;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class UnfocusedFpsLimiter extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> fpsLimit = sgGeneral.add(new IntSetting.Builder()
        .name("fps-limit")
        .description("Maximum framerate while the game window is not focused. Your own limit is kept if it is lower. Low caps delay packet handling slightly.")
        .defaultValue(10)
        .range(UnfocusedFps.MIN_LIMIT, 120)
        .sliderRange(UnfocusedFps.MIN_LIMIT, 60)
        .build()
    );

    // Only the runtime FramerateLimitTracker is changed, never Options#framerateLimit, so options.txt
    // keeps the user's value even if the game exits while limited. Client thread only.
    private boolean limited = false;

    public UnfocusedFpsLimiter() {
        super(StashHunter.CATEGORY, "unfocused-fps-limiter",
            "Lowers the framerate while the game window is in the background to save CPU/GPU on long runs.");
    }

    @Override
    public void onActivate() {
        limited = false;
    }

    @Override
    public void onDeactivate() {
        if (limited) restore();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.isWindowActive()) {
            if (limited) restore();
            return;
        }
        // Re-applied every tick: changing the video option resets the tracker to the user's value.
        mc.getFramerateLimitTracker().setFramerateLimit(
            UnfocusedFps.effectiveLimit(false, mc.options.framerateLimit().get(), fpsLimit.get()));
        limited = true;
    }

    private void restore() {
        mc.getFramerateLimitTracker().setFramerateLimit(mc.options.framerateLimit().get());
        limited = false;
    }
}
