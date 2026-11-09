package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.CaveAirPockets;
import com.stashhunter.stashhunter.utils.ChunkPacketDecoder;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
    Clean-room implementation of PLAN item 57, written against the Minecraft and Meteor APIs only.
    The idea (plain air among cave air marks player activity) is known from Trouser-Streak, which
    has no license; none of its code was looked at or used.

    Generation fills some spaces with cave air (checked in the 26.2 bytecode): NetherWorldCarver
    above Y 31, mineshafts, strongholds, dungeons and lakes. Overworld carvers and noise caves get
    their block from the aquifer, which returns plain air, so overworld caves generated since 1.18
    show nothing; chunks generated in 1.13-1.17 have cave air in all carver caves. A block broken
    there turns into plain air, which CaveAirPockets finds.

    The chunk copy is searched on the Netty thread (decoded once by ChunkPacketDecoder). Pockets on
    the chunk edge may continue into a neighbour; the client thread checks them again in the real
    level once the neighbours are loaded.
*/
public class CaveDisturbanceDetector extends Module {
    // Pockets cut off by an unloaded neighbour chunk are checked again this often, and given up
    // after the max age (the neighbour is outside the server's view distance).
    private static final int RECHECK_INTERVAL_TICKS = 20;
    private static final int MAX_RECHECK_AGE_TICKS = 400;
    private static final int MAX_PENDING = 2048;
    // Bounds render cost and memory for a chunk full of mining.
    private static final int MAX_BLOCKS_PER_CHUNK = 128;
    // A tunnel through several chunks gives one chat message, not one per chunk.
    private static final int CHAT_DEDUPE_RADIUS = 48;
    private static final int MAX_ANNOUNCED = 8192;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("min-air-blocks")
        .description("Plain-air blocks in cave air a chunk needs before it is reported. Higher: fewer false alarms, but single broken blocks are missed.")
        .defaultValue(2)
        .range(1, 64)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> maxPocketSize = sgGeneral.add(new IntSetting.Builder()
        .name("max-pocket-size")
        .description("Largest group of connected plain-air blocks that still counts. Bigger groups are natural caves crossing the cave air and are ignored.")
        .defaultValue(48)
        .range(1, 512)
        .sliderRange(4, 256)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Only search below this height. 128 covers the Nether's carver caves (cave air from Y 32 up) and mineshafts in badlands.")
        .defaultValue(128)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Report disturbed chunks in chat (one message per area).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("render-distance")
        .description("How many chunks from you detections are kept and rendered.")
        .defaultValue(32)
        .min(4)
        .sliderRange(4, 128)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the air blocks are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color of the disturbed air blocks.")
        .defaultValue(new SettingColor(255, 150, 0, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline and tracer color of the disturbed air blocks.")
        .defaultValue(new SettingColor(255, 150, 0, 220))
        .build()
    );

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw a line to each disturbed chunk.")
        .defaultValue(false)
        .build()
    );

    // Found on the Netty thread; handed over through mc.execute.
    private record Scan(List<List<BlockPos>> resolved, List<List<BlockPos>> unresolved) {}

    private record PendingPocket(List<BlockPos> cells, long receivedTick) {}

    private static final class Detection {
        final Set<BlockPos> blocks = new LinkedHashSet<>();
        boolean reported;
    }

    // Client thread only.
    private final Map<Long, Detection> detections = new LinkedHashMap<>();
    private final List<PendingPocket> pending = new ArrayList<>();
    private final Set<Long> announced = new LinkedHashSet<>(); // chunks already reported this level
    private ClientLevel lastLevel;
    private long ticks;

    public CaveDisturbanceDetector() {
        super(StashHunter.CATEGORY, "cave-disturbance-detector",
            "Marks plain air among cave air: blocks broken by players in Nether caves, mineshafts, strongholds and caves of 1.13-1.17 chunks.");
    }

    @Override
    public void onActivate() {
        clearData();
        lastLevel = mc.level;
    }

    @Override
    public void onDeactivate() {
        clearData();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearData();
        lastLevel = null;
    }

    private void clearData() {
        detections.clear();
        pending.clear();
        announced.clear();
    }

    // Netty thread: only the packet and the private chunk copy are touched here.
    @EventHandler
    private void onReadPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundLevelChunkWithLightPacket packet)) return;
        ClientLevel level = mc.level;
        if (level == null) return;
        Scan scan = scan(level, packet);
        if (scan == null) return;
        mc.execute(() -> {
            if (mc.level != level || !isActive()) return;
            for (List<BlockPos> pocket : scan.resolved()) add(pocket);
            for (List<BlockPos> pocket : scan.unresolved()) {
                if (pending.size() < MAX_PENDING) pending.add(new PendingPocket(pocket, ticks));
            }
            reportNew();
        });
    }

    private Scan scan(ClientLevel level, ClientboundLevelChunkWithLightPacket packet) {
        LevelChunk chunk = ChunkPacketDecoder.decode(level, packet);
        if (chunk == null) return null;
        LevelChunkSection[] sections = chunk.getSections();
        int top = maxY.get();

        // Seeds lie in or next to a section that may hold cave air; most chunks have none.
        int first = -1, last = -1;
        for (int i = 0; i < sections.length; i++) {
            if (chunk.getMinY() + i * 16 > top) break;
            if (sections[i].getStates().maybeHas(state -> state.is(Blocks.CAVE_AIR))) {
                if (first < 0) first = i;
                last = i;
            }
        }
        if (first < 0) return null;
        int minSeedY = chunk.getMinY() + Math.max(0, first - 1) * 16;
        int maxSeedY = Math.min(top, chunk.getMinY() + Math.min(sections.length, last + 2) * 16 - 1);

        int maxSize = maxPocketSize.get();
        CaveAirPockets.Blocks blocks = (x, y, z) -> {
            if (x < 0 || x > 15 || z < 0 || z > 15) return CaveAirPockets.UNKNOWN; // neighbour chunk
            int index = chunk.getSectionIndex(y);
            if (index < 0 || index >= sections.length) return CaveAirPockets.OTHER; // below/above the world
            return kind(sections[index].getBlockState(x, y & 15, z));
        };
        List<CaveAirPockets.Pocket> pockets;
        try {
            pockets = CaveAirPockets.find(blocks, 0, minSeedY, 0, 15, maxSeedY, 15, maxSize);
        } catch (RuntimeException e) {
            StashHunter.LOG.debug("Could not search chunk {}, {} for cave disturbances", packet.getX(), packet.getZ(), e);
            return null;
        }
        if (pockets.isEmpty()) return null;

        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        List<List<BlockPos>> resolved = new ArrayList<>();
        List<List<BlockPos>> unresolved = new ArrayList<>();
        for (CaveAirPockets.Pocket pocket : pockets) {
            List<BlockPos> cells = pocket.cells().stream().map(c -> new BlockPos(baseX + c.x(), c.y(), baseZ + c.z())).toList();
            (pocket.resolved() ? resolved : unresolved).add(cells);
        }
        return new Scan(resolved, unresolved);
    }

    private static int kind(BlockState state) {
        if (state.is(Blocks.CAVE_AIR)) return CaveAirPockets.CAVE_AIR;
        if (state.is(Blocks.AIR)) return CaveAirPockets.AIR;
        return CaveAirPockets.OTHER;
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        ticks++;
        if (mc.level != lastLevel) {
            // Dimension change or new world: positions from the old level are meaningless here.
            clearData();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.player == null) return;

        if (!pending.isEmpty()) recheckPending(mc.level);
        if (ticks % 20 == 0) {
            double maxDistance = renderDistance.get() * 16.0;
            detections.entrySet().removeIf(e -> horizontalDistanceSq(e.getValue().blocks.iterator().next()) > maxDistance * maxDistance);
        }
    }

    // Pockets that touched an unloaded neighbour chunk, searched again in the real level.
    private void recheckPending(Level level) {
        int maxSize = maxPocketSize.get();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        CaveAirPockets.Blocks blocks = (x, y, z) -> {
            if (y < level.getMinY() || y > level.getMaxY()) return CaveAirPockets.OTHER;
            if (!level.hasChunk(x >> 4, z >> 4)) return CaveAirPockets.UNKNOWN;
            return kind(level.getBlockState(m.set(x, y, z)));
        };
        boolean added = false;
        Iterator<PendingPocket> it = pending.iterator();
        while (it.hasNext()) {
            PendingPocket p = it.next();
            long age = ticks - p.receivedTick();
            if (age == 0 || age % RECHECK_INTERVAL_TICKS != 0) continue;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxYc = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : p.cells()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxYc = Math.max(maxYc, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            boolean stillOpen = false;
            for (CaveAirPockets.Pocket pocket : CaveAirPockets.find(blocks, minX, minY, minZ, maxX, maxYc, maxZ, maxSize)) {
                if (!pocket.resolved()) {
                    stillOpen = true;
                    continue;
                }
                add(pocket.cells().stream().map(c -> new BlockPos(c.x(), c.y(), c.z())).toList());
                added = true;
            }
            if (!stillOpen || age >= MAX_RECHECK_AGE_TICKS) it.remove();
        }
        if (added) reportNew();
    }

    private void add(List<BlockPos> pocket) {
        BlockPos first = pocket.get(0);
        long key = ChunkPos.pack(first.getX() >> 4, first.getZ() >> 4);
        Detection detection = detections.computeIfAbsent(key, k -> new Detection());
        for (BlockPos pos : pocket) {
            if (detection.blocks.size() >= MAX_BLOCKS_PER_CHUNK) break;
            detection.blocks.add(pos);
        }
    }

    private void reportNew() {
        int threshold = minBlocks.get();
        for (Map.Entry<Long, Detection> entry : detections.entrySet()) {
            Detection detection = entry.getValue();
            if (detection.reported || detection.blocks.size() < threshold) continue;
            detection.reported = true;
            if (announced.size() >= MAX_ANNOUNCED) announced.clear();
            if (!announced.add(entry.getKey())) continue; // seen before, chunk came back into range
            BlockPos pos = detection.blocks.iterator().next();
            StashHunter.LOG.info("Cave disturbance: {} plain-air blocks in cave air near {}", detection.blocks.size(), pos.toShortString());
            if (chatFeedback.get() && !nearReported(entry.getKey(), pos)) {
                info("Possible player activity: %d plain-air blocks in cave air near %s.", detection.blocks.size(), pos.toShortString());
            }
        }
    }

    // True if another reported chunk is close: the same tunnel or mining area.
    private boolean nearReported(long key, BlockPos pos) {
        for (Map.Entry<Long, Detection> entry : detections.entrySet()) {
            if (entry.getKey() == key || !entry.getValue().reported) continue;
            BlockPos other = entry.getValue().blocks.iterator().next();
            int dx = other.getX() - pos.getX(), dz = other.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= CHAT_DEDUPE_RADIUS * CHAT_DEDUPE_RADIUS) return true;
        }
        return false;
    }

    private double horizontalDistanceSq(BlockPos pos) {
        double dx = pos.getX() - mc.player.getBlockX();
        double dz = pos.getZ() - mc.player.getBlockZ();
        return dx * dx + dz * dz;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || event.renderer == null) return;
        int threshold = minBlocks.get();
        ShapeMode mode = shapeMode.get();
        for (Detection detection : detections.values()) {
            if (detection.blocks.size() < threshold) continue;
            for (BlockPos p : detection.blocks) {
                event.renderer.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, sideColor.get(), lineColor.get(), mode, 0);
            }
            if (tracers.get()) {
                BlockPos p = detection.blocks.iterator().next();
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, lineColor.get());
            }
        }
    }
}
