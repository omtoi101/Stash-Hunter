package com.stashhunter.stashhunter.signals;

import com.stashhunter.stashhunter.utils.Config;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Turns the blocks of one loaded chunk into stash signals. Called from the scan (every scan interval), never per
 * frame. Idee aus Trouser-Streak (etianl, BaseFinder), kein Code übernommen.
 */
public final class SignalBlocks {
    /** Every block above this Y in the Overworld is a sky build. */
    private static final int SKY_BUILD_MIN_Y = 261;
    /** Every block other than bedrock above this Y in the Nether is a nether roof build. */
    private static final int NETHER_ROOF_MIN_Y = 128;
    /**
     * Above this many obsidian blocks in one chunk the frame search is skipped: its cost grows steeply with solid
     * obsidian (End pillars, lava-lake obsidian, obsidian walls), and a portal frame needs only 10 to 14 blocks.
     */
    private static final int MAX_OBSIDIAN_FOR_FRAMES = 128;

    /** Blocks whose kind follows from the block alone. */
    private static final Map<Block, SignalKind> KINDS = new HashMap<>();
    /** Blocks the per-section pass looks for: the KINDS without a block entity, plus obsidian and bedrock. */
    private static final Set<Block> SECTION_BLOCKS = new HashSet<>();
    private static final Set<Block> WATCHED;

    static {
        KINDS.put(Blocks.CHEST, SignalKind.CHEST);
        Blocks.COPPER_CHEST.forEach(b -> KINDS.put(b, SignalKind.CHEST));
        KINDS.put(Blocks.TRAPPED_CHEST, SignalKind.TRAPPED_CHEST);
        KINDS.put(Blocks.BARREL, SignalKind.BARREL);
        KINDS.put(Blocks.FURNACE, SignalKind.FURNACE);
        KINDS.put(Blocks.BLAST_FURNACE, SignalKind.FURNACE);
        KINDS.put(Blocks.SMOKER, SignalKind.FURNACE);
        KINDS.put(Blocks.HOPPER, SignalKind.HOPPER);
        KINDS.put(Blocks.DISPENSER, SignalKind.DISPENSER);
        KINDS.put(Blocks.DROPPER, SignalKind.DROPPER);
        KINDS.put(Blocks.SHULKER_BOX, SignalKind.SHULKER_BOX);
        Blocks.DYED_SHULKER_BOX.forEach(b -> KINDS.put(b, SignalKind.SHULKER_BOX));
        KINDS.put(Blocks.ENDER_CHEST, SignalKind.ENDER_CHEST);
        KINDS.put(Blocks.BEACON, SignalKind.BEACON);
        KINDS.put(Blocks.CONDUIT, SignalKind.CONDUIT);

        Blocks.BED.forEach(b -> KINDS.put(b, SignalKind.BED));
        KINDS.put(Blocks.CRAFTING_TABLE, SignalKind.CRAFTING_TABLE);
        KINDS.put(Blocks.ANVIL, SignalKind.ANVIL);
        KINDS.put(Blocks.CHIPPED_ANVIL, SignalKind.ANVIL);
        KINDS.put(Blocks.DAMAGED_ANVIL, SignalKind.ANVIL);
        KINDS.put(Blocks.ENCHANTING_TABLE, SignalKind.ENCHANTING_TABLE);
        KINDS.put(Blocks.BREWING_STAND, SignalKind.BREWING_STAND);

        Blocks.CONCRETE.forEach(b -> KINDS.put(b, SignalKind.UNNATURAL_BLOCK));
        // Left out because they generate naturally: skeleton and wither skeleton skulls (ancient cities, nether
        // fortresses), dragon heads (End ships), waxed copper (trial chambers).
        for (Block b : List.of(Blocks.ZOMBIE_HEAD, Blocks.ZOMBIE_WALL_HEAD, Blocks.PLAYER_HEAD,
            Blocks.PLAYER_WALL_HEAD, Blocks.CREEPER_HEAD, Blocks.CREEPER_WALL_HEAD, Blocks.PIGLIN_HEAD,
            Blocks.PIGLIN_WALL_HEAD, Blocks.REDSTONE_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.NETHERITE_BLOCK,
            Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK)) {
            KINDS.put(b, SignalKind.UNNATURAL_BLOCK);
        }

        KINDS.put(Blocks.NETHER_PORTAL, SignalKind.OPEN_PORTAL);
        KINDS.put(Blocks.END_PORTAL, SignalKind.OPEN_PORTAL);

        for (Map.Entry<Block, SignalKind> e : KINDS.entrySet()) {
            if (!e.getKey().defaultBlockState().hasBlockEntity()) SECTION_BLOCKS.add(e.getKey());
        }
        SECTION_BLOCKS.add(Blocks.OBSIDIAN);
        SECTION_BLOCKS.add(Blocks.BEDROCK);

        Set<Block> watched = new HashSet<>(KINDS.keySet());
        watched.add(Blocks.OBSIDIAN);
        watched.add(Blocks.BEDROCK);
        watched.add(Blocks.SPAWNER);
        watched.add(Blocks.DECORATED_POT);
        BuiltInRegistries.BLOCK.stream().filter(b -> b instanceof SignBlock).forEach(watched::add);
        WATCHED = Collections.unmodifiableSet(watched);
    }

    // The section pass filter depends only on the enabled groups; the scan calls this once per chunk.
    private static Set<TraceGroup> cachedEnabled = EnumSet.noneOf(TraceGroup.class);
    private static Set<Block> cachedSectionBlocks = Set.of();

    private SignalBlocks() {}

    /** Signals of one chunk; {@code spawners} holds every spawner, also when they are not signals. */
    public record ChunkScan(Map<BlockPos, SignalKind> signals, Set<BlockPos> spawners) {}

    /**
     * Every block type the non-classic scan evaluates, for invalidating a cached scan on block updates. Sky and
     * nether roof builds depend on every block above their Y, and sign text or pot contents change without a block
     * update; neither is covered by this set.
     */
    public static Set<Block> watchedBlocks() {
        return WATCHED;
    }

    /** The kind a block maps to on its own, or null (signs, pots, obsidian, bedrock and spawners need context). */
    public static SignalKind kindOf(Block block) {
        return KINDS.get(block);
    }

    /**
     * @param classic exactly the 26.2.2 scan: only {@link Config#getActiveBlockList()} (plus spawners with
     *                {@code includeSpawners}), mapped to their kinds, no other traces; {@code enabled} is ignored
     */
    public static ChunkScan scan(LevelChunk chunk, Dimension dimension, Set<TraceGroup> enabled,
                                 boolean includeSpawners, boolean classic) {
        return classic ? scanClassic(chunk, includeSpawners) : scanSignals(chunk, dimension, enabled, includeSpawners);
    }

    private static ChunkScan scanClassic(LevelChunk chunk, boolean includeSpawners) {
        Map<BlockPos, SignalKind> signals = new HashMap<>();
        Set<BlockPos> spawners = new HashSet<>();
        Set<Block> active = new HashSet<>(Config.getActiveBlockList());

        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            Block block = chunk.getBlockState(pos).getBlock();
            if (block == Blocks.SPAWNER) {
                BlockPos p = pos.immutable();
                spawners.add(p);
                if (includeSpawners) signals.put(p, SignalKind.SPAWNER);
            } else if (active.contains(block)) {
                SignalKind kind = KINDS.get(block);
                if (kind != null) signals.put(pos.immutable(), kind);
            }
        }

        // Same as 26.2.2: the block-by-block pass only runs outside storage-only mode, for blocks without a block
        // entity (those are found exactly above).
        if (Config.storageOnlyMode) return new ChunkScan(signals, spawners);
        Set<Block> plain = new HashSet<>();
        for (Block block : active) {
            if (!block.defaultBlockState().hasBlockEntity()) plain.add(block);
        }
        if (plain.isEmpty()) return new ChunkScan(signals, spawners);

        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> plain.contains(s.getBlock()))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block block = section.getBlockState(x, y, z).getBlock();
                        SignalKind kind = plain.contains(block) ? KINDS.get(block) : null;
                        if (kind != null) signals.put(new BlockPos(minX + x, baseY + y, minZ + z), kind);
                    }
                }
            }
        }
        return new ChunkScan(signals, spawners);
    }

    private static ChunkScan scanSignals(LevelChunk chunk, Dimension dimension, Set<TraceGroup> enabled,
                                         boolean includeSpawners) {
        Map<BlockPos, SignalKind> signals = new HashMap<>();
        Set<BlockPos> spawners = new HashSet<>();

        // Block entities first: exact and cheap.
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            BlockState state = chunk.getBlockState(pos);
            Block block = state.getBlock();
            if (block == Blocks.SPAWNER) {
                BlockPos p = pos.immutable();
                spawners.add(p);
                if (includeSpawners) signals.put(p, SignalKind.SPAWNER);
                continue;
            }
            SignalKind kind = KINDS.get(block);
            if (kind == null && (block instanceof SignBlock || block == Blocks.DECORATED_POT)) {
                BlockEntity be = chunk.getBlockEntity(pos);
                if (be instanceof SignBlockEntity sign && SignText.hasText(sign)) {
                    kind = SignalKind.WRITTEN_SIGN;
                } else if (be instanceof DecoratedPotBlockEntity pot && !pot.getTheItem().isEmpty()) {
                    // Decorated pots generate empty, with sherds only, or with a loot table that the client never
                    // sees as an item; any stored item was put there by a player.
                    kind = SignalKind.UNNATURAL_POT;
                }
            }
            if (kind == SignalKind.OPEN_PORTAL && dimension == Dimension.End
                && NaturalBedrock.isEndExitPortal(pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }
            if (kind != null && isEnabled(kind, enabled)) signals.put(pos.immutable(), kind);
        }

        Set<Block> wanted = sectionBlocks(enabled);
        boolean skyBuilds = enabled.contains(TraceGroup.HIGH_BUILDS)
            && (dimension == Dimension.Overworld || dimension == Dimension.Nether);
        Set<BlockPos> obsidian = new HashSet<>();

        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));

            if (skyBuilds) highBuild(section, dimension, baseY, minX, minZ, signals);
            if (wanted.isEmpty() || !section.maybeHas(s -> wanted.contains(s.getBlock()))) continue;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block block = section.getBlockState(x, y, z).getBlock();
                        if (!wanted.contains(block)) continue;
                        int bx = minX + x, by = baseY + y, bz = minZ + z;
                        if (block == Blocks.OBSIDIAN) {
                            obsidian.add(new BlockPos(bx, by, bz));
                        } else if (block == Blocks.BEDROCK) {
                            if (isPlacedBedrock(chunk.getLevel(), dimension, bx, by, bz)) {
                                signals.put(new BlockPos(bx, by, bz), SignalKind.PLACED_BEDROCK);
                            }
                        } else {
                            signals.put(new BlockPos(bx, by, bz), KINDS.get(block));
                        }
                    }
                }
            }
        }

        if (!obsidian.isEmpty() && obsidian.size() <= MAX_OBSIDIAN_FOR_FRAMES) {
            // PortalFrames rejects rectangles with obsidian inside (walls, solid blocks).
            for (PortalFrames.Frame frame : PortalFrames.frames(obsidian)) {
                signals.put(frame.corner(), SignalKind.OBSIDIAN_FRAME);
            }
        }
        // One signal per trace kind and section: a portal or a concrete wall is one trace, not one per block.
        return new ChunkScan(SignalCollapse.perSection(signals), spawners);
    }

    /** At most one sky or nether roof build per section: the first block above the build Y. */
    private static void highBuild(LevelChunkSection section, Dimension dimension, int baseY, int minX, int minZ,
                                     Map<BlockPos, SignalKind> signals) {
        int minY = dimension == Dimension.Overworld ? SKY_BUILD_MIN_Y : NETHER_ROOF_MIN_Y;
        if (baseY + 15 < minY) return;
        SignalKind kind = dimension == Dimension.Overworld ? SignalKind.SKY_BUILD : SignalKind.NETHER_ROOF_BUILD;
        for (int y = Math.max(0, minY - baseY); y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (state.isAir() || (dimension == Dimension.Nether && state.is(Blocks.BEDROCK))) continue;
                    signals.put(new BlockPos(minX + x, baseY + y, minZ + z), kind);
                    return;
                }
            }
        }
    }

    private static boolean isPlacedBedrock(Level level, Dimension dimension, int x, int y, int z) {
        if (NaturalBedrock.isNatural(dimension, y)) return false;
        if (dimension != Dimension.End) return true;
        if (NaturalBedrock.isEndExitPortal(x, y, z)) return false;
        // End gateways are wrapped in bedrock (within one block sideways and two up or down).
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.getBlockState(p.set(x + dx, y + dy, z + dz)).is(Blocks.END_GATEWAY)) return false;
                }
            }
        }
        return true;
    }

    private static boolean isEnabled(SignalKind kind, Set<TraceGroup> enabled) {
        return kind.group() == TraceGroup.NONE || enabled.contains(kind.group());
    }

    private static synchronized Set<Block> sectionBlocks(Set<TraceGroup> enabled) {
        if (cachedEnabled.equals(enabled) && !cachedSectionBlocks.isEmpty()) return cachedSectionBlocks;
        Set<Block> blocks = new HashSet<>();
        for (Block block : SECTION_BLOCKS) {
            SignalKind kind = block == Blocks.OBSIDIAN ? SignalKind.OBSIDIAN_FRAME
                : block == Blocks.BEDROCK ? SignalKind.PLACED_BEDROCK : KINDS.get(block);
            if (isEnabled(kind, enabled)) blocks.add(block);
        }
        cachedEnabled = enabled.isEmpty() ? EnumSet.noneOf(TraceGroup.class) : EnumSet.copyOf(enabled);
        cachedSectionBlocks = Set.copyOf(blocks);
        return cachedSectionBlocks;
    }
}
