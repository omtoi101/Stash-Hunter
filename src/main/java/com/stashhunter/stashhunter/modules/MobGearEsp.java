package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.signals.SignalHub;
import com.stashhunter.stashhunter.signals.SignalKind;
import com.stashhunter.stashhunter.utils.MobGear;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights mobs wearing or holding gear that looks like a player's (see {@link MobGear}), and also reports them as
 * weak trace signals. Idea from the PathSeeker-listed mob gear ESP modules (windoid and others), no code used.
 */
public class MobGearEsp extends Module {
    private static final int SCAN_INTERVAL_TICKS = 10;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance")
        .description("Only mobs within this many blocks are highlighted.")
        .defaultValue(256)
        .range(16, 1024)
        .sliderRange(16, 512)
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
        .defaultValue(new SettingColor(255, 80, 200, 60))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line and tracer color.")
        .defaultValue(new SettingColor(255, 80, 200, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to the mobs.")
        .defaultValue(false)
        .build()
    );

    // Client thread only.
    private List<Mob> found = List.of();
    private ClientLevel lastLevel;
    private int ticksUntilScan;

    public MobGearEsp() {
        super(StashHunter.CATEGORY, "mob-gear-esp", "Highlights mobs carrying gear that looks like a player's.");
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
        SignalHub.clear(name);
        found = List.of();
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

        double maxSq = (double) renderDistance.get() * renderDistance.get();
        String dimension = PlayerUtils.getDimension().name();
        List<Mob> next = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob) || mob.isRemoved() || !hasPlayerGear(mob)) continue;
            SignalHub.put(name, dimension, mob.blockPosition(), SignalKind.MOB_GEAR);
            if (mob.distanceToSqr(mc.player) <= maxSq) next.add(mob);
        }
        found = next;
    }

    private static boolean hasPlayerGear(Mob mob) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (MobGear.isPlayerGear(id, stack.isEnchanted())) return true;
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (event.renderer == null) return;
        for (Mob mob : found) {
            if (mob.isRemoved()) continue;
            Vec3 shift = mob.getPosition(event.tickDelta).subtract(mob.position());
            AABB box = mob.getBoundingBox().move(shift);
            event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            if (tracers.get()) {
                Vec3 center = box.getCenter();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    center.x, center.y, center.z, lineColor.get());
            }
        }
    }
}
