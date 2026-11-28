package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights chosen items lying on the ground. No signal is published: items stay around after deaths.
 * Idea from the PathSeeker-listed item ESP modules (windoid and others), no code used.
 */
public class DroppedItemEsp extends Module {
    private static final int SCAN_INTERVAL_TICKS = 5;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to highlight when they lie on the ground.")
        .defaultValue(
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL,
            Items.ELYTRA, Items.TOTEM_OF_UNDYING, Items.SHULKER_BOX, Items.END_CRYSTAL)
        .build()
    );

    private final Setting<Integer> renderDistance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance")
        .description("Only items within this many blocks are highlighted.")
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
        .defaultValue(new SettingColor(255, 200, 0, 60))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line and tracer color.")
        .defaultValue(new SettingColor(255, 200, 0, 255))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to the items.")
        .defaultValue(false)
        .build()
    );

    // Client thread only.
    private List<ItemEntity> found = List.of();
    private int ticksUntilScan;

    public DroppedItemEsp() {
        super(StashHunter.CATEGORY, "dropped-item-esp", "Highlights selected items lying on the ground.");
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
        found = List.of();
        ticksUntilScan = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;
        if (ticksUntilScan-- > 0) return;
        ticksUntilScan = SCAN_INTERVAL_TICKS;

        double maxSq = (double) renderDistance.get() * renderDistance.get();
        List<Item> wanted = items.get();
        List<ItemEntity> next = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity drop) || drop.isRemoved()) continue;
            if (drop.distanceToSqr(mc.player) > maxSq) continue;
            if (wanted.contains(drop.getItem().getItem())) next.add(drop);
        }
        found = next;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (event.renderer == null) return;
        for (ItemEntity drop : found) {
            if (drop.isRemoved()) continue;
            Vec3 shift = drop.getPosition(event.tickDelta).subtract(drop.position());
            AABB box = drop.getBoundingBox().move(shift);
            event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            if (tracers.get()) {
                Vec3 center = box.getCenter();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    center.x, center.y, center.z, lineColor.get());
            }
        }
    }
}
