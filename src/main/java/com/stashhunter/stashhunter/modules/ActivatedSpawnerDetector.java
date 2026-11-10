package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.ChunkPacketDecoder;
import com.stashhunter.stashhunter.utils.NaturalStructures;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.*;

/*
    Ported from PathSeeker (Apache-2.0), adapted:
    https://github.com/FaxHack/PathSeeker/blob/main/src/main/java/dev/journey/PathSeeker/modules/utility/ActivatedSpawnerDetector.java
    Original work by the PathSeeker authors (FaxHack / dev.journey), licensed under the Apache License 2.0.

    Changes from the original:
    - Mojang mappings on MC 26.2 instead of Yarn on 1.21.1.
    - Spawners are read from the chunk packet's block-entity tags instead of scanning every loaded
      chunk's block entities each tick. The packet's "Delay" is the server's value, so the client's
      own spawner ticking near the player can no longer fake an activation.
    - Packet decoding runs on the Netty thread on a private LevelChunk copy only; candidates are handed
      to the client thread via mc.execute(...) and checked there once the surrounding chunks have loaded.
    - Trial spawners count as activated only in a state a player causes (active, reward, cooldown);
      INACTIVE is also the state of spawners the server never ticked.
    - Optional amethyst-geode markers (budding amethyst) next to the geode filter of the air check.
*/
public class ActivatedSpawnerDetector extends Module {
    // waypointRadius: spawners of one kind closer than this belong to the same structure (one waypoint).
    // Rough structure extents; two dungeons can sit close together, a mineshaft spans 150+ blocks.
    public enum Kind {
        DUNGEON("Dungeon", 16),
        MINESHAFT("Mineshaft", 96),
        STRONGHOLD("Stronghold", 96),
        FORTRESS("Fortress", 96),
        BASTION("Bastion", 48),
        WOODLAND_MANSION("Woodland Mansion", 64),
        TRIAL_CHAMBER("Trial Chamber", 64),
        OTHER("Spawner", 0),
        GEODE("Amethyst Geode", 0);

        private final String title;
        private final int waypointRadius;

        Kind(String title, int waypointRadius) {
            this.title = title;
            this.waypointRadius = waypointRadius;
        }

        // OTHER is any other mob, usually a placed spawner, i.e. a base signal rather than a structure.
        boolean isNaturalStructure() {
            return this != OTHER && this != GEODE;
        }
    }

    private static final Set<Block> GEODE_BLOCKS = Set.of(
            Blocks.AMETHYST_BLOCK,
            Blocks.BUDDING_AMETHYST,
            Blocks.CALCITE,
            Blocks.SMOOTH_BASALT,
            Blocks.AMETHYST_CLUSTER,
            Blocks.LARGE_AMETHYST_BUD,
            Blocks.MEDIUM_AMETHYST_BUD,
            Blocks.SMALL_AMETHYST_BUD
    );
    private static final Set<Block> LIGHT_BLOCKS = Set.of(
            Blocks.TORCH, Blocks.WALL_TORCH,
            Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
            Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
            Blocks.JACK_O_LANTERN,
            Blocks.GLOWSTONE,
            Blocks.SHROOMLIGHT,
            Blocks.OCHRE_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT,
            Blocks.SEA_LANTERN,
            Blocks.LANTERN, Blocks.SOUL_LANTERN,
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE
    );
    // A vanilla spawner starts with Delay 20 and only changes it once a player has been in range.
    private static final int UNTOUCHED_DELAY = 20;
    // Entities (chest minecarts) arrive in their own packets after the chunk; give them a second.
    private static final int MIN_CANDIDATE_AGE = 20;
    // Checks run once the neighbouring chunks are loaded, or after this many ticks regardless.
    private static final int MAX_CANDIDATE_AGE = 200;
    // Bounds memory on long flights; StashHunter only asks about positions near the player.
    private static final int MAX_STRUCTURES_PER_DIMENSION = 4096;
    private static final String STRUCTURE_WAYPOINT_MARKER = " structure @ ";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLocations = settings.createGroup("Location Toggles");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
            .name("chat-feedback")
            .description("Display info about detected spawners in chat.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> displayCoords = sgGeneral.add(new BoolSetting.Builder()
            .name("display-coords")
            .description("Include the coordinates in the chat message.")
            .defaultValue(true)
            .visible(chatFeedback::get)
            .build()
    );
    private final Setting<Boolean> stashMessage = sgGeneral.add(new BoolSetting.Builder()
            .name("stash-message")
            .description("Remind you that there may be stashed items in storage near an activated spawner.")
            .defaultValue(true)
            .visible(chatFeedback::get)
            .build()
    );
    private final Setting<Boolean> lessStashSpam = sgGeneral.add(new BoolSetting.Builder()
            .name("less-stash-spam")
            .description("Only show the stash reminder if there is storage near the spawner.")
            .defaultValue(true)
            .visible(() -> chatFeedback.get() && stashMessage.get())
            .build()
    );
    private final Setting<Boolean> airChecker = sgGeneral.add(new BoolSetting.Builder()
            .name("check-air-disturbances")
            .description("Also count untouched spawners as activated if both air and cave air are next to them, e.g. after a torch was placed and removed.")
            .defaultValue(false)
            .build()
    );
    private final Setting<Boolean> ignoreGeodes = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-geodes")
            .description("Skip the air check for spawners near geode blocks to reduce false positives.")
            .defaultValue(true)
            .visible(airChecker::get)
            .build()
    );
    private final Setting<List<Block>> storageBlocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("storage-blocks")
            .description("Storage blocks that count as a possible stash near a spawner.")
            .defaultValue(Blocks.CHEST, Blocks.BARREL, Blocks.HOPPER, Blocks.DISPENSER)
            .build()
    );
    private final Setting<Boolean> deactivatedSpawner = sgGeneral.add(new BoolSetting.Builder()
            .name("deactivated-spawner-detector")
            .description("Mark activated spawners with torches or other light blocks next to them separately.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Integer> deactivatedDistance = sgGeneral.add(new IntSetting.Builder()
            .name("torch-scan-distance")
            .description("How many blocks around the spawner to look for light blocks.")
            .defaultValue(1)
            .range(1, 10)
            .sliderRange(1, 10)
            .visible(deactivatedSpawner::get)
            .build()
    );
    private final Setting<Boolean> structureWaypoints = sgGeneral.add(new BoolSetting.Builder()
            .name("structure-waypoints")
            .description("Create a Meteor waypoint for every structure with a detected activated spawner (dungeon, mineshaft, trial chamber, ...). One per structure, not per spawner.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> enableDungeon = sgLocations.add(new BoolSetting.Builder()
            .name("dungeon")
            .description("Detect activated dungeon spawners (zombie, skeleton, spider).")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableMineshaft = sgLocations.add(new BoolSetting.Builder()
            .name("mineshaft")
            .description("Detect activated mineshaft spawners (cave spider).")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableStronghold = sgLocations.add(new BoolSetting.Builder()
            .name("stronghold")
            .description("Detect activated stronghold spawners (silverfish).")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableFortress = sgLocations.add(new BoolSetting.Builder()
            .name("fortress")
            .description("Detect activated fortress spawners (blaze).")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableBastion = sgLocations.add(new BoolSetting.Builder()
            .name("bastion")
            .description("Detect activated bastion spawners (magma cube).")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableWoodlandMansion = sgLocations.add(new BoolSetting.Builder()
            .name("woodland-mansion")
            .description("Detect activated woodland mansion spawners (spider on birch planks).")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableTrialChamber = sgLocations.add(new BoolSetting.Builder()
            .name("trial-chamber")
            .description("Detect trial spawners that a player has triggered.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableOther = sgLocations.add(new BoolSetting.Builder()
            .name("other-spawners")
            .description("Detect activated spawners of any other mob, e.g. placed ones.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableGeode = sgLocations.add(new BoolSetting.Builder()
            .name("amethyst-geode")
            .description("Mark amethyst geodes (budding amethyst). They are common, so this is off by default.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
            .name("render-distance")
            .description("How many chunks from you detections are checked and rendered.")
            .defaultValue(32)
            .min(6)
            .sliderRange(6, 1024)
            .build()
    );
    private final Setting<Boolean> removeOutsideRenderDistance = sgRender.add(new BoolSetting.Builder()
            .name("remove-outside-render-distance")
            .description("Forget detections once they are farther away than the render distance.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> lessRenderSpam = sgRender.add(new BoolSetting.Builder()
            .name("less-render-spam")
            .description("Don't render the range box if there is no storage near the spawner.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
            .name("tracers")
            .description("Show tracers to the detected spawners.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> nearestTracer = sgRender.add(new BoolSetting.Builder()
            .name("nearest-tracer-only")
            .description("Only show a tracer to the nearest detected spawner.")
            .defaultValue(false)
            .visible(tracers::get)
            .build()
    );
    private final Setting<Boolean> rangeRendering = sgRender.add(new BoolSetting.Builder()
            .name("spawner-range-rendering")
            .description("Render the rough active range of a spawner.")
            .defaultValue(true)
            .build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );
    private final Setting<SettingColor> spawnerSideColor = sgRender.add(new ColorSetting.Builder()
            .name("spawner-side-color")
            .description("Color of activated spawners.")
            .defaultValue(new SettingColor(251, 5, 5, 70))
            .build()
    );
    private final Setting<SettingColor> spawnerLineColor = sgRender.add(new ColorSetting.Builder()
            .name("spawner-line-color")
            .description("Color of activated spawners and their tracers.")
            .defaultValue(new SettingColor(251, 5, 5, 235))
            .build()
    );
    private final Setting<SettingColor> trialSideColor = sgRender.add(new ColorSetting.Builder()
            .name("trial-side-color")
            .description("Color of activated trial spawners.")
            .defaultValue(new SettingColor(255, 100, 0, 70))
            .visible(enableTrialChamber::get)
            .build()
    );
    private final Setting<SettingColor> trialLineColor = sgRender.add(new ColorSetting.Builder()
            .name("trial-line-color")
            .description("Color of activated trial spawners and their tracers.")
            .defaultValue(new SettingColor(255, 100, 0, 235))
            .visible(enableTrialChamber::get)
            .build()
    );
    private final Setting<SettingColor> deactivatedSideColor = sgRender.add(new ColorSetting.Builder()
            .name("deactivated-spawner-side-color")
            .description("Color of activated spawners with light blocks next to them.")
            .defaultValue(new SettingColor(251, 5, 251, 70))
            .visible(deactivatedSpawner::get)
            .build()
    );
    private final Setting<SettingColor> deactivatedLineColor = sgRender.add(new ColorSetting.Builder()
            .name("deactivated-spawner-line-color")
            .description("Color of activated spawners with light blocks next to them.")
            .defaultValue(new SettingColor(251, 5, 251, 235))
            .visible(deactivatedSpawner::get)
            .build()
    );
    private final Setting<SettingColor> geodeSideColor = sgRender.add(new ColorSetting.Builder()
            .name("geode-side-color")
            .description("Color of amethyst geodes.")
            .defaultValue(new SettingColor(170, 90, 255, 50))
            .visible(enableGeode::get)
            .build()
    );
    private final Setting<SettingColor> geodeLineColor = sgRender.add(new ColorSetting.Builder()
            .name("geode-line-color")
            .description("Color of amethyst geodes.")
            .defaultValue(new SettingColor(170, 90, 255, 200))
            .visible(enableGeode::get)
            .build()
    );
    private final Setting<SettingColor> rangeSideColor = sgRender.add(new ColorSetting.Builder()
            .name("range-side-color")
            .description("Color of the active spawner range.")
            .defaultValue(new SettingColor(5, 178, 251, 30))
            .visible(rangeRendering::get)
            .build()
    );
    private final Setting<SettingColor> rangeLineColor = sgRender.add(new ColorSetting.Builder()
            .name("range-line-color")
            .description("Color of the active spawner range.")
            .defaultValue(new SettingColor(5, 178, 251, 155))
            .visible(rangeRendering::get)
            .build()
    );

    private record Detection(BlockPos pos, Kind kind, boolean deactivated, boolean storageNearby) {}

    // Collected on the Netty thread from the packet alone; checked on the client thread.
    private record Candidate(BlockPos pos, BlockEntityType<?> type, String mobId, int delay, TrialSpawnerState trialState) {}

    private record PendingCandidate(Candidate candidate, long receivedTick) {}

    private record StructureSpawner(Kind kind, BlockPos pos) {}

    // Client thread only: the packet handler passes every access through mc.execute(...).
    private final Map<BlockPos, Detection> detections = new LinkedHashMap<>();
    private final Set<BlockPos> checkedPositions = new HashSet<>();
    private final List<PendingCandidate> pending = new ArrayList<>();
    // Spawners already handled by addStructureWaypoint in this level. A big structure is chained
    // spawner to spawner, so its far end doesn't get a second waypoint.
    private final List<StructureSpawner> structureSpawners = new ArrayList<>();
    // Every unmodified natural spawner seen, activated or not and regardless of the location toggles; kept
    // across dimension changes for StashHunter's natural-structure filter.
    private final NaturalStructures structures = new NaturalStructures(MAX_STRUCTURES_PER_DIMENSION);
    private ClientLevel lastLevel;
    private long ticks;

    public ActivatedSpawnerDetector() {
        super(StashHunter.CATEGORY, "activated-spawner-detector", "Detects spawners a player has been near, to locate dungeons, mineshafts, bastions, mansions and trial chambers that may hide stashes.");
    }

    @Override
    public void onActivate() {
        clearData();
        structures.clear();
        lastLevel = mc.level;
    }

    @Override
    public void onDeactivate() {
        clearData();
        structures.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearData();
        structures.clear();
        lastLevel = null;
    }

    /** Client thread only. True if a natural-structure spawner was seen within radius (3D) of pos. */
    public boolean isNearNaturalStructure(Dimension dimension, BlockPos pos, int radius) {
        return structures.isNear(dimension.name(), pos, radius);
    }

    private void clearData() {
        detections.clear();
        checkedPositions.clear();
        pending.clear();
        structureSpawners.clear();
    }

    // PacketEvent.Receive is posted on the Netty connection thread. Only the packet and our own private
    // LevelChunk copy are touched here; the result is handed to the client thread via mc.execute(...).
    @EventHandler
    private void onReadPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundLevelChunkWithLightPacket packet)) return;
        ClientLevel level = mc.level;
        if (level == null) return;

        List<Candidate> candidates = collectCandidates(level, packet);
        if (candidates.isEmpty()) return;
        mc.execute(() -> {
            if (mc.level != level || !isActive()) return;
            for (Candidate c : candidates) {
                if (!checkedPositions.contains(c.pos())) pending.add(new PendingCandidate(c, ticks));
            }
        });
    }

    private List<Candidate> collectCandidates(ClientLevel level, ClientboundLevelChunkWithLightPacket packet) {
        List<Candidate> candidates = new ArrayList<>();
        List<BlockPos> trialSpawners = new ArrayList<>();
        try {
            packet.getChunkData().getBlockEntitiesTagsConsumer(packet.getX(), packet.getZ()).accept((pos, type, tag) -> {
                if (type == BlockEntityTypes.MOB_SPAWNER) {
                    candidates.add(spawnerCandidate(pos.immutable(), tag));
                } else if (type == BlockEntityTypes.TRIAL_SPAWNER) {
                    trialSpawners.add(pos.immutable());
                }
            });
        } catch (RuntimeException e) {
            StashHunter.LOG.debug("Could not read block entities of chunk {}, {}", packet.getX(), packet.getZ(), e);
            return List.of();
        }

        boolean wantGeode = enableGeode.get();
        if (trialSpawners.isEmpty() && !wantGeode) return candidates;

        // Trial spawner state and budding amethyst live in the block states, so read the private copy
        // (decoded once and shared with NewerNewChunks, without block entities).
        LevelChunk chunk = ChunkPacketDecoder.decode(level, packet);
        if (chunk == null) return candidates; // a half-decoded chunk would only produce false positives

        for (BlockPos pos : trialSpawners) {
            BlockState state = chunk.getBlockState(pos);
            if (state.getBlock() instanceof TrialSpawnerBlock) {
                candidates.add(new Candidate(pos, BlockEntityTypes.TRIAL_SPAWNER, null, 0, state.getValue(TrialSpawnerBlock.STATE)));
            }
        }
        if (wantGeode) {
            BlockPos geode = findBuddingAmethyst(chunk);
            if (geode != null) candidates.add(new Candidate(geode, null, null, 0, null));
        }
        return candidates;
    }

    private static Candidate spawnerCandidate(BlockPos pos, CompoundTag tag) {
        String mobId = tag.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getStringOr("id", "");
        int delay = tag.getShortOr("Delay", (short) UNTOUCHED_DELAY);
        return new Candidate(pos, BlockEntityTypes.MOB_SPAWNER, mobId, delay, null);
    }

    private static BlockPos findBuddingAmethyst(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.getStates().maybeHas(s -> s.is(Blocks.BUDDING_AMETHYST))) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(Blocks.BUDDING_AMETHYST)) {
                            int blockY = chunk.getMinY() + i * 16 + y;
                            return new BlockPos(chunk.getPos().getMinBlockX() + x, blockY, chunk.getPos().getMinBlockZ() + z);
                        }
                    }
                }
            }
        }
        return null;
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

        double maxDistance = renderDistance.get() * 16.0;
        double maxDistanceSq = maxDistance * maxDistance;
        Iterator<PendingCandidate> it = pending.iterator();
        while (it.hasNext()) {
            PendingCandidate p = it.next();
            long age = ticks - p.receivedTick();
            if (age < MIN_CANDIDATE_AGE) continue;
            BlockPos pos = p.candidate().pos();
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                if (age > MAX_CANDIDATE_AGE) it.remove();
                continue;
            }
            if (age <= MAX_CANDIDATE_AGE && !neighboursLoaded(pos)) continue;
            it.remove();
            if (!checkedPositions.add(pos)) continue;
            if (horizontalDistanceSq(pos) >= maxDistanceSq) {
                checkedPositions.remove(pos); // may come back into range with the next chunk packet
                continue;
            }
            evaluate(mc.level, p.candidate());
        }

        if (removeOutsideRenderDistance.get()) {
            detections.keySet().removeIf(pos -> horizontalDistanceSq(pos) >= maxDistanceSq);
            checkedPositions.removeIf(pos -> horizontalDistanceSq(pos) >= maxDistanceSq);
        }
    }

    private boolean neighboursLoaded(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!mc.level.hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return true;
    }

    private double horizontalDistanceSq(BlockPos pos) {
        double dx = pos.getX() - mc.player.getBlockX();
        double dz = pos.getZ() - mc.player.getBlockZ();
        return dx * dx + dz * dz;
    }

    private void evaluate(Level level, Candidate c) {
        if (c.type() == null) {
            if (enableGeode.get()) detections.put(c.pos(), new Detection(c.pos(), Kind.GEODE, false, false));
            return;
        }
        if (c.type() == BlockEntityTypes.TRIAL_SPAWNER) {
            // Trial spawners only generate in trial chambers, triggered or not.
            structures.add(PlayerUtils.getDimension().name(), c.pos());
            if (!enableTrialChamber.get() || !isTriggered(c.trialState())) return;
            report(level, c.pos(), Kind.TRIAL_CHAMBER, 14);
            return;
        }

        String mob = c.mobId().substring(c.mobId().indexOf(':') + 1);
        Kind kind = classify(level, c.pos(), mob);
        // A spawner with blocks placed/removed around it is a farm, i.e. a base signal: keep it out
        // of the index so StashHunter's structure filter doesn't hide the storage next to it.
        boolean modified = kind.isNaturalStructure() && hasAirDisturbance(level, c.pos(), mob);
        if (kind.isNaturalStructure() && !modified) structures.add(PlayerUtils.getDimension().name(), c.pos());
        if (!isEnabled(kind)) return;

        boolean activated;
        if (airChecker.get() && (c.delay() == UNTOUCHED_DELAY || c.delay() == 0)) {
            activated = kind.isNaturalStructure() ? modified : hasAirDisturbance(level, c.pos(), mob);
        } else {
            // Nether spawners sit at Delay 0 without a player ever being near; only the air check can tell.
            activated = c.delay() != UNTOUCHED_DELAY && !(level.dimension() == Level.NETHER && c.delay() == 0);
        }
        if (activated) report(level, c.pos(), kind, 16);
    }

    private static boolean isTriggered(TrialSpawnerState state) {
        return state == TrialSpawnerState.ACTIVE
                || state == TrialSpawnerState.WAITING_FOR_REWARD_EJECTION
                || state == TrialSpawnerState.EJECTING_REWARD
                || state == TrialSpawnerState.COOLDOWN;
    }

    private static Kind classify(Level level, BlockPos pos, String mob) {
        return switch (mob) {
            case "spider" -> level.getBlockState(pos.below()).is(Blocks.BIRCH_PLANKS) ? Kind.WOODLAND_MANSION : Kind.DUNGEON;
            case "zombie", "skeleton" -> Kind.DUNGEON;
            case "cave_spider" -> Kind.MINESHAFT;
            case "silverfish" -> Kind.STRONGHOLD;
            case "blaze" -> Kind.FORTRESS;
            case "magma_cube" -> Kind.BASTION;
            default -> Kind.OTHER;
        };
    }

    private boolean isEnabled(Kind kind) {
        return switch (kind) {
            case DUNGEON -> enableDungeon.get();
            case MINESHAFT -> enableMineshaft.get();
            case STRONGHOLD -> enableStronghold.get();
            case FORTRESS -> enableFortress.get();
            case BASTION -> enableBastion.get();
            case WOODLAND_MANSION -> enableWoodlandMansion.get();
            case TRIAL_CHAMBER -> enableTrialChamber.get();
            case OTHER -> enableOther.get();
            case GEODE -> enableGeode.get();
        };
    }

    // Natural structures generate either air or cave air around a spawner, not both; a mix means
    // blocks were placed and removed. Only checked for mobs whose structure has a known shape.
    private boolean hasAirDisturbance(Level level, BlockPos pos, String mob) {
        int minX, maxX, minY, maxY, minZ, maxZ;
        switch (mob) {
            case "zombie", "skeleton", "spider" -> { minX = -2; maxX = 1; minY = -1; maxY = 2; minZ = -2; maxZ = 1; }
            case "cave_spider" -> { minX = -1; maxX = 1; minY = 0; maxY = 1; minZ = -1; maxZ = 1; }
            case "silverfish" -> { minX = -3; maxX = 3; minY = -2; maxY = 3; minZ = -3; maxZ = 3; }
            default -> { return false; }
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        if (ignoreGeodes.get() && containsAny(level, pos, m, -5, 5, -5, 5, -5, 5, GEODE_BLOCKS)) return false;
        boolean air = false;
        boolean caveAir = false;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = level.getBlockState(m.setWithOffset(pos, x, y, z)).getBlock();
                    if (block == Blocks.AIR) air = true;
                    else if (block == Blocks.CAVE_AIR) caveAir = true;
                    if (air && caveAir) return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAny(Level level, BlockPos pos, BlockPos.MutableBlockPos m, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Collection<Block> blocks) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (blocks.contains(level.getBlockState(m.setWithOffset(pos, x, y, z)).getBlock())) return true;
                }
            }
        }
        return false;
    }

    private void report(Level level, BlockPos pos, Kind kind, int storageRadius) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean deactivated = false;
        if (kind != Kind.TRIAL_CHAMBER && deactivatedSpawner.get()) {
            int d = deactivatedDistance.get();
            deactivated = containsAny(level, pos, m, -d, d, -d, d, -d, d, LIGHT_BLOCKS);
        }
        boolean storage = hasStorageNearby(level, pos, m, storageRadius);
        detections.put(pos, new Detection(pos, kind, deactivated, storage));
        StashHunter.LOG.info("Activated {} spawner at {} (storage nearby: {})", kind.title, pos.toShortString(), storage);
        if (structureWaypoints.get() && kind.isNaturalStructure()) addStructureWaypoint(pos, kind);

        if (!chatFeedback.get()) return;
        String where = displayCoords.get() ? " at " + pos.toShortString() : "";
        info("Detected activated %s spawner%s.", kind.title, where);
        if (deactivated) info("The spawner has torches or other light blocks next to it.");
        if (stashMessage.get() && (storage || !lessStashSpam.get())) {
            warning("There may be stashed items in storage near the spawner!");
        }
    }

    // Deduped against the structure's other spawners and Meteor's persisted waypoints, so toggles
    // and reconnects don't add duplicates either.
    private void addStructureWaypoint(BlockPos pos, Kind kind) {
        int radius = kind.waypointRadius;
        boolean sameStructure = false;
        for (StructureSpawner spawner : structureSpawners) {
            if (spawner.kind() == kind && spawner.pos().closerThan(pos, radius)) {
                sameStructure = true;
                break;
            }
        }
        structureSpawners.add(new StructureSpawner(kind, pos.immutable()));
        if (sameStructure) return;

        Dimension dimension = PlayerUtils.getDimension();
        String prefix = kind.title + STRUCTURE_WAYPOINT_MARKER;
        for (Waypoint waypoint : Waypoints.get()) {
            if (waypoint.dimension.get() == dimension
                    && waypoint.name.get().startsWith(prefix)
                    && waypoint.pos.get().closerThan(pos, radius)) {
                return;
            }
        }
        String name = prefix + pos.toShortString();
        Waypoints.get().add(new Waypoint.Builder()
                .name(name)
                .pos(pos)
                .dimension(dimension)
                .build());
        StashHunter.LOG.info("Created waypoint: {}", name);
    }

    // One pass over the cube instead of an entity query per block like the original.
    private boolean hasStorageNearby(Level level, BlockPos pos, BlockPos.MutableBlockPos m, int r) {
        List<Block> blocks = storageBlocks.get();
        if (!blocks.isEmpty() && containsAny(level, pos, m, -r, r, -r, r, -r, r, blocks)) return true;
        AABB box = new AABB(pos.getX() - r, pos.getY() - r, pos.getZ() - r, pos.getX() + r + 1, pos.getY() + r + 1, pos.getZ() + r + 1);
        return !level.getEntitiesOfClass(MinecartChest.class, box, e -> true).isEmpty();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || event.renderer == null) return;
        double maxDistance = renderDistance.get() * 16.0;
        double maxDistanceSq = maxDistance * maxDistance;
        ShapeMode mode = shapeMode.get();

        Detection nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Detection d : detections.values()) {
            double distSq = horizontalDistanceSq(d.pos());
            if (distSq >= maxDistanceSq) continue;
            BlockPos p = d.pos();
            Color sides = sideColor(d);
            Color lines = lineColor(d);

            if (d.kind() == Kind.GEODE) {
                event.renderer.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, sides, lines, mode, 0);
                continue;
            }
            if (rangeRendering.get() && (d.storageNearby() || !lessRenderSpam.get())) {
                int r = d.kind() == Kind.TRIAL_CHAMBER ? 14 : 16;
                event.renderer.box(p.getX() - r, p.getY() - r, p.getZ() - r, p.getX() + r + 1, p.getY() + r + 1, p.getZ() + r + 1, rangeSideColor.get(), rangeLineColor.get(), mode, 0);
            }
            event.renderer.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, sides, lines, mode, 0);

            if (tracers.get()) {
                if (nearestTracer.get()) {
                    if (distSq < nearestSq) {
                        nearestSq = distSq;
                        nearest = d;
                    }
                } else {
                    tracer(event, p, lines);
                }
            }
        }
        if (nearest != null) tracer(event, nearest.pos(), lineColor(nearest));
    }

    private static void tracer(Render3DEvent event, BlockPos p, Color color) {
        event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, color);
    }

    private Color sideColor(Detection d) {
        if (d.kind() == Kind.GEODE) return geodeSideColor.get();
        if (d.deactivated()) return deactivatedSideColor.get();
        return d.kind() == Kind.TRIAL_CHAMBER ? trialSideColor.get() : spawnerSideColor.get();
    }

    private Color lineColor(Detection d) {
        if (d.kind() == Kind.GEODE) return geodeLineColor.get();
        if (d.deactivated()) return deactivatedLineColor.get();
        return d.kind() == Kind.TRIAL_CHAMBER ? trialLineColor.get() : spawnerLineColor.get();
    }
}
