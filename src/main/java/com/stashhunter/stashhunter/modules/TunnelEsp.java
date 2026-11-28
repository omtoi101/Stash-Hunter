package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.DirtyChunks;
import com.stashhunter.stashhunter.utils.TunnelShapes;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Highlights 1x1 shafts, 2-3 high tunnels and staircases, the shapes players dig. No signal is published: caves
 * produce too many false hits. Chunks are scanned only when they load or change, a few per tick.
 * Idea from Trouser-Streak (etianl) hole/tunnel ESP, no code used.
 */
public class TunnelEsp extends Module {
    private static final int RESCAN_INTERVAL_TICKS = 20;
    // Bounds the work and the boxes drawn for one chunk (a big cave can match thousands of times).
    private static final int MAX_HITS_PER_KIND = 1024;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minHoleDepth = sgGeneral.add(new IntSetting.Builder()
        .name("min-hole-depth")
        .description("Minimum depth of a 1x1 shaft.")
        .defaultValue(4)
        .range(2, 32)
        .sliderRange(2, 16)
        .build()
    );

    private final Setting<Integer> minTunnelLength = sgGeneral.add(new IntSetting.Builder()
        .name("min-tunnel-length")
        .description("Minimum length of a 2-3 high tunnel.")
        .defaultValue(3)
        .range(2, 32)
        .sliderRange(2, 16)
        .build()
    );

    private final Setting<Integer> minStairSteps = sgGeneral.add(new IntSetting.Builder()
        .name("min-stair-steps")
        .description("Minimum number of steps of a staircase.")
        .defaultValue(3)
        .range(2, 32)
        .sliderRange(2, 16)
        .build()
    );

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Radius in chunks around you that is scanned and drawn.")
        .defaultValue(4)
        .range(1, 16)
        .sliderRange(1, 12)
        .build()
    );

    private final Setting<Integer> chunksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("chunks-per-tick")
        .description("Maximum number of chunks scanned per tick. Lower is smoother.")
        .defaultValue(1)
        .range(1, 8)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Boolean> holes = sgGeneral.add(new BoolSetting.Builder()
        .name("holes")
        .description("Show 1x1 shafts.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tunnels = sgGeneral.add(new BoolSetting.Builder()
        .name("tunnels")
        .description("Show 2-3 high tunnels.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stairs = sgGeneral.add(new BoolSetting.Builder()
        .name("stairs")
        .description("Show staircases.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> holeColor = sgRender.add(new ColorSetting.Builder()
        .name("hole-color")
        .description("Color of shafts.")
        .defaultValue(new SettingColor(255, 60, 60, 255))
        .build()
    );

    private final Setting<SettingColor> tunnelColor = sgRender.add(new ColorSetting.Builder()
        .name("tunnel-color")
        .description("Color of tunnels.")
        .defaultValue(new SettingColor(60, 160, 255, 255))
        .build()
    );

    private final Setting<SettingColor> stairColor = sgRender.add(new ColorSetting.Builder()
        .name("stair-color")
        .description("Color of staircases.")
        .defaultValue(new SettingColor(60, 255, 120, 255))
        .build()
    );

    private final Setting<Integer> sideOpacity = sgRender.add(new IntSetting.Builder()
        .name("side-opacity")
        .description("Opacity of the box sides.")
        .defaultValue(40)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to shafts, tunnels and staircases.")
        .defaultValue(false)
        .build()
    );

    /** A hit: the lowest block of the box and its height in blocks (box spans pos to pos + height). */
    private record Hit(int x, int y, int z, int height) {
    }

    private record ChunkResult(List<Hit> holes, List<Hit> tunnels, List<Hit> stairs) {
    }

    // Client thread only. Results per chunk (key from DirtyChunks.key); absent = not scanned yet.
    private final Map<Long, ChunkResult> results = new HashMap<>();
    private final DirtyChunks dirty = new DirtyChunks();
    private ClientLevel lastLevel;
    private int ticksUntilRescan;

    public TunnelEsp() {
        super(StashHunter.CATEGORY, "tunnel-esp", "Highlights 1x1 shafts, tunnels and staircases dug by players.");
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
        results.clear();
        dirty.clear();
        lastLevel = null;
        ticksUntilRescan = 0;
    }

    // Chunk data arrived (again): rescan it and its neighbours, whose shapes reach across the border.
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.level == null) return;
        markAround(event.chunk().getPos().x(), event.chunk().getPos().z());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.level == null || event.oldState == null || event.newState == null) return;
        // Only a change in passability can alter a shape.
        if (isPassable(event.oldState, event.pos) == isPassable(event.newState, event.pos)) return;
        int cx = event.pos.getX() >> 4, cz = event.pos.getZ() >> 4;
        int lx = event.pos.getX() & 15, lz = event.pos.getZ() & 15;
        // Shapes reach at most a few blocks across a chunk border.
        boolean nearBorder = lx < 4 || lx > 11 || lz < 4 || lz > 11;
        if (nearBorder) markAround(cx, cz);
        else dirty.mark(cx, cz);
    }

    private void markAround(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) dirty.mark(cx + dx, cz + dz);
        }
    }

    // Passable = the block has no full-cube collision (air, plants, slabs, stairs, ... count as open).
    private boolean isPassable(BlockState state, BlockPos pos) {
        if (state.isAir()) return true;
        return !state.isCollisionShapeFullBlock(mc.level, pos);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            clearData();
            lastLevel = mc.level;
        }
        int pcx = mc.player.chunkPosition().x();
        int pcz = mc.player.chunkPosition().z();
        int radius = scanRadius.get();

        if (ticksUntilRescan-- <= 0) {
            ticksUntilRescan = RESCAN_INTERVAL_TICKS;
            housekeeping(pcx, pcz, radius);
        }

        for (long key : dirty.drain(chunksPerTick.get(), k -> readyToScan(k, pcx, pcz, radius))) {
            results.put(key, scan(DirtyChunks.x(key), DirtyChunks.z(key)));
        }
    }

    // Drops what is out of range or unloaded and queues loaded in-range chunks that were never scanned.
    private void housekeeping(int pcx, int pcz, int radius) {
        results.keySet().removeIf(k -> !inRange(k, pcx, pcz, radius + 1) || !mc.level.hasChunk(DirtyChunks.x(k), DirtyChunks.z(k)));
        dirty.retain(k -> inRange(k, pcx, pcz, radius) && mc.level.hasChunk(DirtyChunks.x(k), DirtyChunks.z(k)));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = pcx + dx, cz = pcz + dz;
                if (mc.level.hasChunk(cx, cz) && !results.containsKey(DirtyChunks.key(cx, cz))) dirty.mark(cx, cz);
            }
        }
    }

    private static boolean inRange(long key, int pcx, int pcz, int radius) {
        return Math.abs(DirtyChunks.x(key) - pcx) <= radius && Math.abs(DirtyChunks.z(key) - pcz) <= radius;
    }

    // All eight neighbours must be loaded: a shape is decided by blocks across the border.
    private boolean readyToScan(long key, int pcx, int pcz, int radius) {
        if (!inRange(key, pcx, pcz, radius)) return false;
        int cx = DirtyChunks.x(key), cz = DirtyChunks.z(key);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!mc.level.hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return true;
    }

    private ChunkResult scan(int cx, int cz) {
        List<Hit> holeHits = new ArrayList<>();
        List<Hit> tunnelHits = new ArrayList<>();
        List<Hit> stairHits = new ArrayList<>();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        TunnelShapes.Passable passable = (x, y, z) -> {
            cursor.set(x, y, z);
            return isPassable(mc.level.getBlockState(cursor), cursor);
        };

        int depth = minHoleDepth.get(), length = minTunnelLength.get(), steps = minStairSteps.get();
        boolean doHoles = holes.get(), doTunnels = tunnels.get(), doStairs = stairs.get();
        // Keep the lookups of every shape inside the world (floor y-1, ceiling y+3, stair top y+steps+1).
        int minY = mc.level.getMinY() + 1;
        int maxY = mc.level.getMinY() + mc.level.getHeight() - 5 - steps;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                for (int y = minY; y <= maxY; y++) {
                    if (!passable.at(x, y, z)) continue;
                    if (doHoles && holeHits.size() < MAX_HITS_PER_KIND
                        && TunnelShapes.isHole(passable, x, y, z, depth)
                        // Report the shaft once, from its top.
                        && !TunnelShapes.isHole(passable, x, y + 1, z, depth)) {
                        holeHits.add(new Hit(x, y - depth + 1, z, depth + 1));
                    }
                    if (doTunnels && tunnelHits.size() < MAX_HITS_PER_KIND && TunnelShapes.isTunnel(passable, x, y, z, length)) {
                        tunnelHits.add(new Hit(x, y, z, 2));
                    }
                    if (doStairs && stairHits.size() < MAX_HITS_PER_KIND && TunnelShapes.isStair(passable, x, y, z, steps)) {
                        stairHits.add(new Hit(x, y, z, 2));
                    }
                }
            }
        }
        return new ChunkResult(holeHits, tunnelHits, stairHits);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (event.renderer == null) return;
        for (ChunkResult result : results.values()) {
            if (holes.get()) draw(event, result.holes(), holeColor.get());
            if (tunnels.get()) draw(event, result.tunnels(), tunnelColor.get());
            if (stairs.get()) draw(event, result.stairs(), stairColor.get());
        }
    }

    private void draw(Render3DEvent event, List<Hit> hits, SettingColor line) {
        SettingColor side = new SettingColor(line.r, line.g, line.b, sideOpacity.get());
        for (Hit h : hits) {
            event.renderer.box(h.x(), h.y(), h.z(), h.x() + 1, h.y() + h.height(), h.z() + 1, side, line, shapeMode.get(), 0);
            if (tracers.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    h.x() + 0.5, h.y() + 0.5, h.z() + 0.5, line);
            }
        }
    }
}
