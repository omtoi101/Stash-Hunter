package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.MinecartStacks;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;

import java.util.ArrayList;
import java.util.List;

/*
    Ported from PathSeeker (Apache-2.0), adapted:
    https://github.com/FaxHack/PathSeeker/blob/main/src/main/java/dev/journey/PathSeeker/modules/utility/StackedMinecartsDetector.java
    Original work by the PathSeeker authors (FaxHack / dev.journey), licensed under the Apache License 2.0.

    Changes from the original:
    - Mojang mappings on MC 26.2 instead of Yarn on 1.21.1.
    - detection-radius is actually used (the original grouped by block position and ignored it);
      grouping lives in the tested MinecartStacks.
    - Scans every few ticks instead of every tick, and forgets everything on toggle, server leave and
      level change.
    - Optional: only storage minecarts (chest/hopper), chat and Discord notice once per new stack.
    - Box height is capped, tracers are optional per stack.
*/
public class StackedMinecartDetector extends Module {
    private static final int SCAN_INTERVAL_TICKS = 10;
    // Bounds the "already announced" memory on long flights.
    private static final int MAX_ANNOUNCED = 4096;
    private static final double MAX_BOX_HEIGHT = 2.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> detectionRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("detection-radius")
        .description("Maximum distance in blocks between minecarts to count them as one stack.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(2)
        .build()
    );

    private final Setting<Integer> minStackSize = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-stack-size")
        .description("Minimum number of minecarts in one spot to count as a stack.")
        .defaultValue(2)
        .min(2)
        .sliderRange(2, 10)
        .build()
    );

    private final Setting<Boolean> storageOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("storage-only")
        .description("Only count minecarts with storage (chest and hopper minecarts).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Report each new stack in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> discord = sgGeneral.add(new BoolSetting.Builder()
        .name("discord")
        .description("Send each new stack to the Discord webhook configured in the Stash-Hunter module.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw a box on stacked minecarts; it grows with the stack size.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color of the boxes.")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line and tracer color.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to stacked minecarts.")
        .defaultValue(true)
        .build()
    );

    // Client thread only.
    private List<MinecartStacks.Stack> stacks = List.of();
    // Not keyed by block: a stack on a block border can be anchored on either side.
    private final List<MinecartStacks.Stack> announced = new ArrayList<>();
    private ClientLevel lastLevel;
    private int ticksUntilScan;

    public StackedMinecartDetector() {
        super(StashHunter.CATEGORY, "stacked-minecarts",
            "Detects minecarts stacked in one spot, a common way to hide storage (chest/hopper minecarts).");
    }

    @Override
    public void onActivate() {
        clearData();
    }

    @Override
    public void onDeactivate() {
        clearData();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearData();
    }

    private void clearData() {
        stacks = List.of();
        announced.clear();
        lastLevel = null;
        ticksUntilScan = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            // Positions of another dimension or server mean nothing here.
            clearData();
            lastLevel = mc.level;
        }
        if (ticksUntilScan-- > 0) return;
        ticksUntilScan = SCAN_INTERVAL_TICKS;

        double radius = detectionRadius.get();
        MinecartStacks grouping = new MinecartStacks(radius);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractMinecart)) continue;
            if (storageOnly.get() && !(entity instanceof AbstractMinecartContainer)) continue;
            grouping.add(entity.getX(), entity.getY(), entity.getZ());
        }
        stacks = grouping.stacks(minStackSize.get());

        for (MinecartStacks.Stack stack : stacks) {
            if (isAnnounced(stack, radius)) continue;
            if (announced.size() >= MAX_ANNOUNCED) announced.clear();
            announced.add(stack);
            announce(BlockPos.containing(stack.x(), stack.y(), stack.z()), stack.count());
        }
    }

    private boolean isAnnounced(MinecartStacks.Stack stack, double radius) {
        for (MinecartStacks.Stack known : announced) {
            if (known.isNear(stack, radius)) return true;
        }
        return false;
    }

    private void announce(BlockPos pos, int count) {
        StashHunter.LOG.info("Stacked minecarts: {} at {}", count, pos.toShortString());
        if (chatFeedback.get()) info("%d stacked minecarts at %s.", count, pos.toShortString());
        if (discord.get()) {
            String description = count + " minecarts at " + pos.toShortString() + " (" + PlayerUtils.getDimension().name() + ")";
            DiscordWebhook.sendMessage("", new DiscordEmbed("Stacked Minecarts", description, 0xFF5555));
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || event.renderer == null) return;
        for (MinecartStacks.Stack stack : stacks) {
            int x = (int) Math.floor(stack.x());
            double y = Math.floor(stack.y());
            int z = (int) Math.floor(stack.z());
            double height = Math.min(MAX_BOX_HEIGHT, 0.2 * stack.count());
            event.renderer.box(x + 0.1, y, z + 0.1, x + 0.9, y + height, z + 0.9,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            if (tracers.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    x + 0.5, y + 0.1, z + 0.5, lineColor.get());
            }
        }
    }
}
