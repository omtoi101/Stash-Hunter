package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.SlimeChunks;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.OptionalLong;

/**
 * Marks slime chunks around the player. Java Edition slime chunks depend on the world seed, which
 * the server doesn't send: in singleplayer it is read from the integrated server, in multiplayer it
 * has to be entered in world-seed.
 */
public class SlimeChunkOverlay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<String> worldSeed = sgGeneral.add(new StringSetting.Builder()
        .name("world-seed")
        .description("The world seed (number or text, like the Create World screen). Ignored in singleplayer, where the real seed is used.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("How many chunks around you to mark.")
        .defaultValue(16)
        .range(1, 64)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Integer> renderHeight = sgRender.add(new IntSetting.Builder()
        .name("render-height")
        .description("The height at which slime chunks are drawn. Slimes spawn below y=40 in them.")
        .defaultValue(40)
        .sliderRange(-64, 319)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of slime chunks.")
        .defaultValue(new SettingColor(80, 220, 80, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of slime chunks.")
        .defaultValue(new SettingColor(80, 220, 80, 160))
        .build()
    );

    // Slime chunks in range as {x0, z0, x1, z1, ...}; recomputed only when the inputs change.
    private int[] cached = new int[0];
    private int cachedCount;
    private long cachedSeed;
    private int cachedCenterX, cachedCenterZ, cachedRadius = -1;
    private boolean warnedNoSeed;

    public SlimeChunkOverlay() {
        super(StashHunter.CATEGORY, "slime-chunks", "Marks slime chunks around you (needs the world seed in multiplayer).");
    }

    @Override
    public void onActivate() {
        cachedRadius = -1;
        warnedNoSeed = false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || event.renderer == null) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        OptionalLong seed = currentSeed();
        if (seed.isEmpty()) {
            // In singleplayer an empty seed only means the integrated server is starting/stopping.
            if (!warnedNoSeed && mc.getSingleplayerServer() == null) {
                warning("No world seed set - enter it in the world-seed setting.");
                warnedNoSeed = true;
            }
            return;
        }
        warnedNoSeed = false;

        int cx = mc.player.chunkPosition().x();
        int cz = mc.player.chunkPosition().z();
        int r = radius.get();
        if (cachedRadius != r || cachedSeed != seed.getAsLong() || cachedCenterX != cx || cachedCenterZ != cz) {
            recompute(seed.getAsLong(), cx, cz, r);
        }

        double y = renderHeight.get();
        for (int i = 0; i < cachedCount; i += 2) {
            double minX = cached[i] * 16.0;
            double minZ = cached[i + 1] * 16.0;
            event.renderer.box(minX, y, minZ, minX + 16, y, minZ + 16, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    private OptionalLong currentSeed() {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            // overworld() is a plain map lookup and is null while the server starts or stops.
            ServerLevel overworld = server.overworld();
            return overworld != null ? OptionalLong.of(overworld.getSeed()) : OptionalLong.empty();
        }
        return SlimeChunks.parseSeed(worldSeed.get());
    }

    private void recompute(long seed, int cx, int cz, int r) {
        int side = 2 * r + 1;
        if (cached.length < side * side * 2) cached = new int[side * side * 2];
        int count = 0;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (SlimeChunks.isSlimeChunk(seed, x, z)) {
                    cached[count++] = x;
                    cached[count++] = z;
                }
            }
        }
        cachedCount = count;
        cachedSeed = seed;
        cachedCenterX = cx;
        cachedCenterZ = cz;
        cachedRadius = r;
    }
}
