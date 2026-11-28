package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.signals.Candidate;
import com.stashhunter.stashhunter.signals.ContainerFilter;
import com.stashhunter.stashhunter.signals.DiagnosticsLog;
import com.stashhunter.stashhunter.signals.EntitySignalIndex;
import com.stashhunter.stashhunter.signals.Rejection;
import com.stashhunter.stashhunter.signals.ScanDiagnostics;
import com.stashhunter.stashhunter.signals.ScanRadiusAdvice;
import com.stashhunter.stashhunter.signals.Sensitivity;
import com.stashhunter.stashhunter.signals.Signal;
import com.stashhunter.stashhunter.signals.SignalBlocks;
import com.stashhunter.stashhunter.signals.SignalCategory;
import com.stashhunter.stashhunter.signals.SignalClusterer;
import com.stashhunter.stashhunter.signals.SignalEntities;
import com.stashhunter.stashhunter.signals.SignalHub;
import com.stashhunter.stashhunter.signals.SignalKind;
import com.stashhunter.stashhunter.signals.SignalScoring;
import com.stashhunter.stashhunter.signals.Tier;
import com.stashhunter.stashhunter.signals.TraceGroup;
import com.stashhunter.stashhunter.signals.Verdict;
import com.stashhunter.stashhunter.utils.ChunkScanSchedule;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.DimensionIds;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.DungeonFilter;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.KeyHold;
import com.stashhunter.stashhunter.utils.MissedChunks;
import com.stashhunter.stashhunter.utils.ReportedStashes;
import com.stashhunter.stashhunter.utils.ScanTrigger;
import com.stashhunter.stashhunter.utils.ServerKey;
import com.stashhunter.stashhunter.utils.StashBlockIndex;
import com.stashhunter.stashhunter.utils.StashClustering;
import com.stashhunter.stashhunter.utils.StashGrowth;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StashHunterModule extends Module {
    // Settings
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    @SuppressWarnings("unused") // value is read via Config.discordWebhookUrl
    private final Setting<String> discordWebhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("discord-webhook-url")
        .description("The Discord webhook URL to send notifications to.")
        .defaultValue(Config.discordWebhookUrl)
        .onChanged(v -> {
            Config.discordWebhookUrl = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> blockDetectionThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("block-detection-threshold")
        .description("Containers for a stash. In normal at most 6, in sensitive at most 4 are needed.")
        .defaultValue(Config.blockDetectionThreshold)
        .min(1)
        .sliderMax(50)
        .onChanged(v -> {
            Config.blockDetectionThreshold = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Sensitivity> sensitivity = sgGeneral.add(new EnumSetting.Builder<Sensitivity>()
        .name("sensitivity")
        .description("classic: only containers and block-detection-threshold, like 26.2.2. normal: also traces (signs, beds, portals, entities, other modules) and small finds such as one shulker box, reported as hints. sensitive: lower hint and stash scores.")
        .defaultValue(Sensitivity.NORMAL)
        .build()
    );

    private final Setting<Boolean> discordHints = sgGeneral.add(new BoolSetting.Builder()
        .name("discord-hints")
        .description("Also send hints (small finds and player traces) to Discord. Stashes are always sent.")
        .defaultValue(false)
        .visible(() -> sensitivity.get() != Sensitivity.CLASSIC)
        .build()
    );

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("The radius to scan for valuable blocks.")
        .defaultValue(Config.scanRadius)
        .min(16)
        .max(256)
        .sliderMax(256)
        .onChanged(v -> {
            Config.scanRadius = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> storageOnlyMode = sgGeneral.add(new BoolSetting.Builder()
        .name("storage-only-mode")
        .description("Only search for storage containers (chests, shulkers). Disable to search for all stash blocks. Only in classic sensitivity.")
        .defaultValue(Config.storageOnlyMode)
        .onChanged(v -> {
            Config.storageOnlyMode = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> maxVolumeThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("max-volume-threshold")
        .description("Maximum volume to prevent natural structure detection.")
        .defaultValue(Config.maxVolumeThreshold)
        .min(100)
        .sliderMax(50000)
        .onChanged(v -> {
            Config.maxVolumeThreshold = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> filterNaturalStructures = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-natural-structures")
        .description("Filter out likely natural structures (dungeons, etc.).")
        .defaultValue(Config.filterNaturalStructures)
        .onChanged(v -> {
            Config.filterNaturalStructures = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> structureFilterRadius = sgGeneral.add(new IntSetting.Builder()
        .name("structure-filter-radius")
        .description("With activated-spawner-detector on: a stash whose center is this close to a spawner of a natural structure (dungeon, mineshaft, trial chamber, ...) counts as that structure's loot and is not reported. Spawners with blocks placed or removed around them (farms) are not counted. 0 = off (default); 6-8 covers dungeon chests. Note: also hides real stashes hidden in such structures.")
        .defaultValue(Config.structureFilterRadius)
        .range(0, 64)
        .sliderRange(0, 64)
        .visible(filterNaturalStructures::get)
        .onChanged(v -> {
            Config.structureFilterRadius = v;
            Config.saveLater();
        })
        .build()
    );

    // Only the mob spawner: trial spawners can't be obtained or moved in survival and only appear
    // in trial chambers, which also hold natural chests and vaults, so they'd only add false alarms.
    private final Setting<Boolean> includeSpawners = sgGeneral.add(new BoolSetting.Builder()
        .name("include-spawners")
        .description("Also count mob spawners as stash blocks (spawner farms are built around them). A dungeon with only its spawner and chests is still ignored while filter-natural-structures is on.")
        .defaultValue(Config.includeSpawners)
        .onChanged(v -> {
            Config.includeSpawners = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Double> minDensityThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-density-threshold")
        .description("Minimum density required for stash detection.")
        .defaultValue(Config.minDensityThreshold)
        .min(0.0001)
        .sliderMax(0.1)
        .onChanged(v -> {
            Config.minDensityThreshold = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Double> notificationDensityThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("notification-density-threshold")
        .description("Minimum density required to send Discord notifications. Set to 0 to notify for all detected stashes.")
        .defaultValue(Config.notificationDensityThreshold)
        .min(0.0)
        .sliderMax(0.1)
        .onChanged(v -> {
            Config.notificationDensityThreshold = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> maxClusterDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-cluster-distance")
        .description("Maximum distance between blocks in a cluster.")
        .defaultValue(Config.maxClusterDistance)
        .min(10)
        .sliderMax(200)
        .onChanged(v -> {
            Config.maxClusterDistance = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> maxClusterSize = sgGeneral.add(new IntSetting.Builder()
        .name("max-cluster-size")
        .description("Maximum diameter of one cluster. Keeps neighbouring stashes from being chained into one huge, low-density cluster.")
        .defaultValue(Config.maxClusterSize)
        .min(16)
        .sliderMax(256)
        .onChanged(v -> {
            Config.maxClusterSize = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> reportDedupeRadius = sgGeneral.add(new IntSetting.Builder()
        .name("report-dedupe-radius")
        .description("A stash closer than this to an already reported one (or to an existing stash waypoint) is not reported again.")
        .defaultValue(Config.reportDedupeRadius)
        .min(8)
        .sliderMax(200)
        .onChanged(v -> {
            Config.reportDedupeRadius = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> saveToXaero = sgGeneral.add(new BoolSetting.Builder()
        .name("save-to-xaero")
        .description("Also add every stash waypoint to Xaero's Minimap (current waypoint set). Needs Xaero's Minimap installed.")
        .defaultValue(Config.saveToXaero)
        .onChanged(v -> {
            Config.saveToXaero = v;
            Config.saveLater();
        })
        .build()
    );

    // Value is read via Config.flightAltitude; setFlightAltitude goes through the setting so the GUI stays in sync.
    private final Setting<Integer> flightAltitude = sgGeneral.add(new IntSetting.Builder()
        .name("flight-altitude")
        .description("The altitude to fly at.")
        .defaultValue(Config.flightAltitude)
        .min(100)
        .max(400)
        .sliderMax(400)
        .onChanged(v -> {
            Config.flightAltitude = v;
            Config.saveLater();
            ElytraController.applyFlightAltitude();
        })
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Maximum ticks between scans. With scan-by-distance on, fast flight scans earlier.")
        .defaultValue(Config.scanInterval)
        .min(1)
        .max(1200)
        .sliderMax(100)
        .onChanged(v -> {
            Config.scanInterval = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> scanByDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("scan-by-distance")
        .description("Also scan after flying half the scan radius since the last scan, so fast flight leaves no gaps.")
        .defaultValue(Config.scanByDistance)
        .onChanged(v -> {
            Config.scanByDistance = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> scanOnlyWhileFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("scan-only-while-flying")
        .description("Only scan while a flight (.stashhunter start/resume) is running. Off: the module also works as a standing scanner while you walk or stand.")
        .defaultValue(Config.scanOnlyWhileFlying)
        .onChanged(v -> {
            Config.scanOnlyWhileFlying = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> playerDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("player-detection")
        .description("Whether to notify when a player is detected.")
        .defaultValue(Config.playerDetection)
        .onChanged(v -> {
            Config.playerDetection = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> playerDetectionRadius = sgGeneral.add(new IntSetting.Builder()
        .name("player-detection-radius")
        .description("Players closer than this many blocks are reported.")
        .defaultValue(Config.playerDetectionRadius)
        .min(8)
        .max(512)
        .sliderMax(256)
        .visible(playerDetection::get)
        .onChanged(v -> {
            Config.playerDetectionRadius = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> playerReportCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("player-report-cooldown")
        .description("Minutes before the same player is reported again.")
        .defaultValue(Config.playerReportCooldown)
        .min(1)
        .max(120)
        .sliderMax(30)
        .visible(playerDetection::get)
        .onChanged(v -> {
            Config.playerReportCooldown = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> notifyOnDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-on-death")
        .description("Whether to notify when you die.")
        .defaultValue(Config.notifyOnDeath)
        .onChanged(v -> {
            Config.notifyOnDeath = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> notifyOnCompletion = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-on-completion")
        .description("Whether to notify when scanning is completed.")
        .defaultValue(Config.notifyOnCompletion)
        .onChanged(v -> {
            Config.notifyOnCompletion = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> notifyOnDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-on-disconnect")
        .description("Whether to notify when you are disconnected from the server.")
        .defaultValue(Config.notifyOnDisconnect)
        .onChanged(v -> {
            Config.notifyOnDisconnect = v;
            Config.saveLater();
        })
        .build()
    );

    @SuppressWarnings("unused") // value is read via Config.mentionEveryone
    private final Setting<Boolean> mentionEveryone = sgGeneral.add(new BoolSetting.Builder()
        .name("mention-everyone")
        .description("Ping @everyone for important Discord notifications (stash found, death, disconnect).")
        .defaultValue(Config.mentionEveryone)
        .onChanged(v -> {
            Config.mentionEveryone = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Boolean> useBaritonePathing = sgGeneral.add(new BoolSetting.Builder()
        .name("use-baritone-pathing")
        .description("Use Baritone (if installed) for precise flight/ground path execution instead of the built-in flight controller.")
        .defaultValue(Config.useBaritonePathing)
        .onChanged(v -> {
            Config.useBaritonePathing = v;
            Config.saveLater();
        })
        .build()
    );

    @SuppressWarnings("unused") // value is read via Config.landAfterCompletion
    private final Setting<Boolean> landAfterCompletion = sgGeneral.add(new BoolSetting.Builder()
        .name("land-after-completion")
        .description("After the area is scanned, let Baritone's elytra pathing land on a safe spot near the last waypoint. Needs Baritone; otherwise you keep gliding.")
        .defaultValue(Config.landAfterCompletion)
        .onChanged(v -> {
            Config.landAfterCompletion = v;
            Config.saveLater();
        })
        .build()
    );

    // Value is read via Config.landOnLowFireworks; the field only drives lowFireworkThreshold's visibility.
    private final Setting<Boolean> landOnLowFireworks = sgGeneral.add(new BoolSetting.Builder()
        .name("land-on-low-fireworks")
        .description("Pause the scan and let Baritone land on a safe spot nearby when the rockets in your inventory drop to the threshold. Resume with .stashhunter resume.")
        .defaultValue(Config.landOnLowFireworks)
        .onChanged(v -> {
            Config.landOnLowFireworks = v;
            Config.saveLater();
        })
        .build()
    );

    @SuppressWarnings("unused") // value is read via Config.lowFireworkThreshold
    private final Setting<Integer> lowFireworkThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("low-firework-threshold")
        .description("Rocket count (whole inventory) at which the scan pauses and lands.")
        .defaultValue(Config.lowFireworkThreshold)
        .min(1)
        .max(64)
        .sliderMax(64)
        .visible(landOnLowFireworks::get)
        .onChanged(v -> {
            Config.lowFireworkThreshold = v;
            Config.saveLater();
        })
        .build()
    );

    // Signals: one switch per trace group (containers and shulker boxes/ender chests always count).
    private final SettingGroup sgSignals = settings.createGroup("Signals");
    private final Map<TraceGroup, Setting<Boolean>> traceGroups = traceGroupSettings();

    private Map<TraceGroup, Setting<Boolean>> traceGroupSettings() {
        Map<TraceGroup, Setting<Boolean>> result = new EnumMap<>(TraceGroup.class);
        for (TraceGroup group : TraceGroup.values()) {
            if (group == TraceGroup.NONE) continue;
            result.put(group, sgSignals.add(new BoolSetting.Builder()
                .name(group.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .description(traceGroupDescription(group))
                .defaultValue(true)
                .visible(() -> sensitivity.get() != Sensitivity.CLASSIC)
                .build()));
        }
        return result;
    }

    private static String traceGroupDescription(TraceGroup group) {
        return switch (group) {
            case SIGNS -> "Signs with text.";
            case UNNATURAL_BLOCKS -> "Blocks that never generate naturally (concrete, some heads, ore blocks, beacons, conduits) and decorated pots holding an item.";
            case PORTALS -> "Open nether/end portals and obsidian portal frames.";
            case HIGH_BUILDS -> "Blocks above Y 260 in the Overworld and on the Nether roof.";
            case BEDROCK -> "Bedrock outside the natural layers.";
            case ENTITIES -> "Item frames with items, named entities, traded villagers, animals with chests, boats, chest minecarts and mobs with player gear.";
            case OTHER_MODULES -> "Findings of activated-spawner-detector, stacked-minecarts and cave-disturbance-detector while they are on.";
            case BASE_BLOCKS -> "Beds, crafting tables, anvils, enchanting tables, brewing stands (weak traces), and spawners with include-spawners.";
            case NONE -> "";
        };
    }

    // Stash ESP
    private final SettingGroup sgRender = settings.createGroup("Stash ESP");

    private final Setting<Boolean> renderStashes = sgRender.add(new BoolSetting.Builder()
        .name("render-stashes")
        .description("Outlines the containers of stashes and hints found this session (hints without containers: their signals).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> stashRenderDistance = sgRender.add(new IntSetting.Builder()
        .name("stash-render-distance")
        .description("Maximum distance in blocks at which found stashes are rendered.")
        .defaultValue(128)
        .min(16)
        .sliderMax(512)
        .visible(renderStashes::get)
        .build()
    );

    private final Setting<ShapeMode> stashShapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("stash-shape-mode")
        .description("How the stash containers are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(renderStashes::get)
        .build()
    );

    private final Setting<SettingColor> stashSideColor = sgRender.add(new ColorSetting.Builder()
        .name("stash-side-color")
        .description("Side color of stash containers.")
        .defaultValue(new SettingColor(0, 255, 0, 50))
        .visible(renderStashes::get)
        .build()
    );

    private final Setting<SettingColor> stashLineColor = sgRender.add(new ColorSetting.Builder()
        .name("stash-line-color")
        .description("Line color of stash containers, their bounding box and tracer.")
        .defaultValue(new SettingColor(0, 255, 0, 235))
        .visible(renderStashes::get)
        .build()
    );

    private final Setting<SettingColor> hintSideColor = sgRender.add(new ColorSetting.Builder()
        .name("hint-side-color")
        .description("Side color of hints (small finds and player traces).")
        .defaultValue(new SettingColor(255, 255, 0, 50))
        .visible(renderStashes::get)
        .build()
    );

    private final Setting<SettingColor> hintLineColor = sgRender.add(new ColorSetting.Builder()
        .name("hint-line-color")
        .description("Line color of hints, their bounding box and tracer.")
        .defaultValue(new SettingColor(255, 255, 0, 235))
        .visible(renderStashes::get)
        .build()
    );

    private final Setting<Boolean> stashBoundingBox = sgRender.add(new BoolSetting.Builder()
        .name("stash-bounding-box")
        .description("Also draws one box around all containers of a stash.")
        .defaultValue(false)
        .visible(renderStashes::get)
        .build()
    );

    private final Setting<Boolean> stashTracers = sgRender.add(new BoolSetting.Builder()
        .name("stash-tracers")
        .description("Draws a tracer to the nearest found stash or hint.")
        .defaultValue(false)
        .visible(renderStashes::get)
        .build()
    );

    // State
    private final Map<UUID, Long> reportedPlayers = new ConcurrentHashMap<>();
    // Hints and stashes reported this session (report corrections, Stash ESP), and their messages.
    private final StashReporter reporter = new StashReporter(this::info);
    // One file per server under the Meteor folder; loaded lazily for the server we're on.
    private static final Path STASH_HUNTER_DIR = MeteorClient.FOLDER.toPath().resolve("StashHunter");
    private static final Path REPORTED_STASHES_DIR = STASH_HUNTER_DIR.resolve("reported-stashes");
    private static final Path DIAGNOSTICS_DIR = STASH_HUNTER_DIR.resolve("diagnostics");
    private static final long DIAGNOSTICS_LOG_MAX_BYTES = 5L * 1024 * 1024;
    private ReportedStashes reportedStashes;
    private DiagnosticsLog diagnosticsLog;
    // Signals of every scanned chunk, so clusters can span several scans/flight strips.
    private final StashBlockIndex<SignalKind> signalIndex = new StashBlockIndex<>(8192);
    // Entity signals per entity (one signal per entity, also when it walks on), updated on every scan.
    private final EntitySignalIndex entityIndex = new EntitySignalIndex(16_384);
    // Every spawner seen (also when spawners are no signal), for the dungeon filter.
    private final StashBlockIndex<Boolean> spawnerIndex = new StashBlockIndex<>(8192);
    // Entities are only sent within the server's entity tracking range, which can be smaller than
    // the scan radius. An entity not seen this scan is forgotten only if its last chunk is this close
    // to the player, where its entities are surely loaded; farther out it stays where it was seen.
    private static final int ENTITY_REPLACE_RADIUS = 48;
    // A chunk is only read again when it changed or after this long (catches missed updates).
    private static final int RESCAN_TICKS = 1200;
    private final ChunkScanSchedule scanSchedule = new ChunkScanSchedule(RESCAN_TICKS);
    // Chunks that were inside the scan radius before the server sent them: scanned once they arrive.
    private final MissedChunks missedChunks = new MissedChunks(RESCAN_TICKS, 4096);
    // Chunks that never arrived leave holes in the scanned area; the player hears about it now and then.
    private static final int MISSED_WARNING_MIN_CHUNKS = 16;
    private static final int MISSED_WARNING_INTERVAL_TICKS = 12_000; // 10 minutes
    private int neverLoadedChunks = 0;
    private int lastMissedWarningTick = -MISSED_WARNING_INTERVAL_TICKS;
    // Scan settings of the last scan: a change makes every cached chunk result stale.
    private ScanConfig lastScanConfig = null;
    // Blocks whose change invalidates a chunk's cached scan (depends on the sensitivity).
    private Set<Block> watchedBlocks = Set.of();
    private boolean lastScanClassic = false;
    private int lastSchedulePruneTick = 0;
    // Counters per flight (or since enabling without a flight), summed per session.
    private final ScanDiagnostics diagnostics = new ScanDiagnostics();
    // Candidates already counted this period: one stays in range for many scans.
    private final Set<String> diagnosedCandidates = new HashSet<>();
    private static final int MAX_DIAGNOSED_CANDIDATES = 20_000;
    private static final DateTimeFormatter PERIOD_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private boolean wasFlying = false;
    private int tickCounter = 0;
    private int ticksSinceScan = Integer.MAX_VALUE / 2; // large = scan on the first tick
    private double lastScanX, lastScanZ;
    private float lastHealth = -1; // Track health for death detection, -1 = not sampled yet
    private int elytraBrokenTicks = 0;
    private int reEngagingElytraTicks = 0;
    private boolean wasElytraBroken = false;
    private boolean holdingJump = false;
    private ResourceKey<Level> lastDimension = null; // Track dimension for world-change teardown

    /** Everything a chunk scan depends on; when it changes, cached chunk results are stale. */
    private record ScanConfig(boolean classic, Set<TraceGroup> enabled, boolean includeSpawners, Set<Block> classicBlocks) {
    }

    public StashHunterModule() {
        super(StashHunter.CATEGORY, "stash-hunter", "Automatically finds stashes by flying around and scanning for valuable blocks. Also scans while standing unless scan-only-while-flying is on.");
    }

    @Override
    public void onActivate() {
        reportedPlayers.clear();
        reportedStashes = null; // reloaded from disk for the current server
        diagnosticsLog = null;
        clearIndexes();
        neverLoadedChunks = 0;
        lastMissedWarningTick = -MISSED_WARNING_INTERVAL_TICKS;
        lastHealth = -1; // Reset health tracking
        lastDimension = null;

        // Reset elytra/tick state so a re-enable can't instantly trip the "out of elytras"
        // path (Discord blast + disconnect) from a stale broken-elytra counter, nor resume a
        // leftover jump hold or completion/notification timer from the previous run.
        elytraBrokenTicks = 0;
        wasElytraBroken = false;
        reEngagingElytraTicks = 0;
        tickCounter = 0;
        lastSchedulePruneTick = 0;
        ticksSinceScan = Integer.MAX_VALUE / 2;
        setHoldingJump(false);

        // Without a flight the period runs from now until the module is turned off.
        wasFlying = ElytraController.isActive();
        startDiagnosticsPeriod(wasFlying ? "flight from " : "standing since ");

        if (useBaritonePathing.get() && !BaritoneBridge.isModLoaded()) {
            info("Baritone is not installed - falling back to the built-in flight controller. " +
                "Install the Baritone Fabric mod (26.2) for precise pathfinding.");
        }
    }

    @Override
    public void onDeactivate() {
        ElytraController.stop();
        wasFlying = false;
        setHoldingJump(false);
        reporter.clear();
        printDiagnosticsPeriod();
    }

    private void clearIndexes() {
        signalIndex.clear();
        entityIndex.clear();
        spawnerIndex.clear();
        scanSchedule.clear();
        missedChunks.clear();
    }

    /** Sets the cruise altitude (clamped to the setting's 100-400 range); a running flight follows at once. */
    public int setFlightAltitude(int altitude) {
        flightAltitude.set(Math.max(100, Math.min(400, altitude)));
        return flightAltitude.get();
    }

    /** Chat lines for .stashhunter diag: the running period and the session total. */
    public List<String> diagnosticsLines() {
        return diagnostics.current();
    }

    // Looked up lazily: this module is registered before AutoElytraRepair, so it isn't available at construction time.
    private AutoElytraRepair autoElytraRepair() {
        return Modules.get().get(AutoElytraRepair.class);
    }

    // Only release the jump key if we pressed it, so the player (and other modules) can still use it.
    private void setHoldingJump(boolean hold) {
        if (hold == holdingJump) return;
        holdingJump = hold;
        if (hold) KeyHold.press(mc.options.keyJump);
        else KeyHold.release(mc.options.keyJump);
    }

    // Also forgets them on disk, so they can be reported again.
    public void clearReportedStashes() {
        if (mc.level != null) reportedStashes().clear();
        reportedStashes = null;
        reporter.clear(); // they get reported (and highlighted) again, so no duplicate boxes
    }

    private ReportedStashes reportedStashes() {
        if (reportedStashes == null) reportedStashes = ReportedStashes.load(REPORTED_STASHES_DIR.resolve(ServerKey.current() + ".txt"));
        return reportedStashes;
    }

    private DiagnosticsLog diagnosticsLog() {
        if (diagnosticsLog == null) {
            diagnosticsLog = new DiagnosticsLog(DIAGNOSTICS_DIR.resolve(ServerKey.current() + ".log"), DIAGNOSTICS_LOG_MAX_BYTES);
        }
        return diagnosticsLog;
    }

    public void clearReportedPlayers() {
        reportedPlayers.clear();
    }

    // Meteor posts GameLeftEvent whenever a world is left (kick, lost connection, own disconnect),
    // so no Fabric API connection event is needed for this.
    private void notifyDisconnect() {
        if (notifyOnDisconnect.get()) {
            DiscordEmbed embed = new DiscordEmbed(
                "Disconnected!",
                "The client has been disconnected from the server.",
                0xFF0000
            );
            DiscordWebhook.sendMessage("@everyone", embed);
            info("Disconnected from server and notified to Discord");
        }
    }

    // ElytraController's waypoints/target are static and would survive into the next session.
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        notifyDisconnect();
        if (ElytraController.isActive()) ElytraController.pause();
        reportedStashes = null; // the next server has its own file
        diagnosticsLog = null;
        clearIndexes();
        reporter.clear();
        resetFlightState();
        lastHealth = -1;
        lastDimension = null;
    }

    // Saves the trip (resumable via .stashhunter resume) and drops all flight/elytra state.
    private void haltFlight(String reason) {
        if (ElytraController.isActive()) {
            ElytraController.pause();
            info(reason + " - flight paused. Use .stashhunter resume to continue.");
        }
        resetFlightState();
    }

    private void resetFlightState() {
        elytraBrokenTicks = 0;
        wasElytraBroken = false;
        reEngagingElytraTicks = 0;
        setHoldingJump(false);
    }

    // ----- diagnostics periods -----

    private void startDiagnosticsPeriod(String label) {
        diagnostics.startPeriod(label + LocalTime.now().format(PERIOD_TIME));
        diagnosedCandidates.clear();
    }

    private void printDiagnosticsPeriod() {
        for (String line : diagnostics.endPeriod()) info(line);
    }

    // Covers every way a flight starts (.stashhunter start/spiral/resume, resume after a repair) and
    // ends (stop, pause, completion, landing, death, dimension change).
    private void trackFlight() {
        boolean flying = ElytraController.isActive();
        if (flying && !wasFlying) {
            // A standing period before the flight goes into the session total without a chat summary.
            diagnostics.endPeriod();
            startDiagnosticsPeriod("flight from ");
            ScanRadiusAdvice.advise(scanRadius.get(), mc.options.getEffectiveRenderDistance()).ifPresent(this::info);
        } else if (!flying && wasFlying) {
            printDiagnosticsPeriod();
            startDiagnosticsPeriod("standing since ");
        }
        wasFlying = flying;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        ElytraController.onTick();
        trackFlight();

        AutoElytraRepair autoElytraRepair = autoElytraRepair();
        if (autoElytraRepair != null && autoElytraRepair.justFinishedRepair()) {
            ElytraController.climbToAltitude();
        }

        if (mc.player == null || mc.level == null) {
            return;
        }

        // Dimension change: the waypoints belong to the old dimension's coordinates.
        ResourceKey<Level> dimension = mc.level.dimension();
        if (lastDimension != null && !dimension.equals(lastDimension)) {
            haltFlight("Dimension changed");
            missedChunks.clear(); // the old dimension's chunks won't arrive anymore
        }
        lastDimension = dimension;

        // Enhanced Elytra check with repair integration
        if (ElytraController.isActive()) {
            ItemStack chestSlot = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            boolean elytraMissing = chestSlot.isEmpty() ||
                (chestSlot.getItem() == Items.ELYTRA && chestSlot.isDamaged() && chestSlot.getDamageValue() >= chestSlot.getMaxDamage() - 1);

            // Check if auto repair is handling the situation
            if (autoElytraRepair != null && autoElytraRepair.isActive() && autoElytraRepair.isRepairing()) {
                // Auto repair is active, let it handle elytra management
                return; // Skip normal elytra checks
            }

            if (elytraMissing) {
                wasElytraBroken = true;
                elytraBrokenTicks++;

                // Give auto repair system time to activate before panicking
                int timeoutTicks = autoElytraRepair != null && autoElytraRepair.isActive() ? 200 : 40;

                if (elytraBrokenTicks > timeoutTicks) {
                    // Send Discord notification
                    DiscordEmbed embed = new DiscordEmbed(
                        "Out of Elytras!",
                        "The bot has run out of elytras or all elytras are broken beyond repair, and will now disconnect.",
                        0xFF0000
                    );
                    DiscordWebhook.sendMessage("@everyone", embed);

                    // Disconnect from server
                    if (mc.getConnection() != null) {
                        mc.getConnection().getConnection().disconnect(Component.literal("Ran out of elytras or all elytras broken."));
                    }

                    // Stop elytra controller
                    ElytraController.stop();

                    // Deactivate the module
                    toggle();
                    return;
                }
            } else {
                elytraBrokenTicks = 0;
                if (wasElytraBroken) {
                    wasElytraBroken = false;
                    reEngagingElytraTicks = 100; // 5 seconds
                }
            }
        }

        // Handle re-engagement
        if (reEngagingElytraTicks > 0) {
            reEngagingElytraTicks--;
            setHoldingJump(true);
        } else {
            setHoldingJump(false);
        }

        // Death detection - check if player health dropped to 0 or respawned
        float currentHealth = mc.player.getHealth();

        // If health went from positive to 0, player died
        if (lastHealth > 0 && currentHealth <= 0) {
            if (notifyOnDeath.get()) {
                DiscordEmbed embed = new DiscordEmbed(
                    "Bot Died!",
                    "Death location: " + mc.player.blockPosition().toShortString() +
                    "\nLast health: " + String.format("%.1f", lastHealth) + " → 0",
                    0xFF0000
                );
                DiscordWebhook.sendMessage("@everyone", embed);
                info("Death detected and notified to Discord");
            }
            haltFlight("Death detected");
        }

        // If we were dead (health 0, not the unsampled -1) and now have health, we respawned
        if (lastHealth == 0 && currentHealth > 0 && notifyOnDeath.get()) {
            DiscordEmbed embed = new DiscordEmbed(
                "Bot Respawned",
                "Respawn location: " + mc.player.blockPosition().toShortString() +
                "\nHealth restored: " + currentHealth,
                0x00FF00
            );
            DiscordWebhook.sendMessage("", embed);
            info("Respawn detected and notified to Discord");
        }

        lastHealth = currentHealth;

        // Scan every scanInterval ticks at most, sooner once the player moved half the scan radius.
        // Without scan-only-while-flying this also runs with no flight active (standing scanner).
        ticksSinceScan++;
        if ((!scanOnlyWhileFlying.get() || ElytraController.isActive()) && ScanTrigger.shouldScan(ticksSinceScan, scanInterval.get(),
            mc.player.getX() - lastScanX, mc.player.getZ() - lastScanZ, scanRadius.get(), scanByDistance.get())) {
            ticksSinceScan = 0;
            lastScanX = mc.player.getX();
            lastScanZ = mc.player.getZ();
            scanForBlocks();
        }

        // Player detection logic
        long playerCooldownMs = playerReportCooldown.get() * 60_000L;
        if (playerDetection.get()) {
            double radiusSq = (double) playerDetectionRadius.get() * playerDetectionRadius.get();
            for (Player player : mc.level.players()) {
                if (player == null || player.equals(mc.player)) {
                    continue;
                }

                if (mc.player.distanceToSqr(player) < radiusSq) {
                    // Keyed by UUID: the entity object is recreated whenever a player re-enters render distance
                    UUID playerId = player.getUUID();
                    if (!reportedPlayers.containsKey(playerId) ||
                        System.currentTimeMillis() - reportedPlayers.get(playerId) > playerCooldownMs) {

                        DiscordEmbed embed = new DiscordEmbed(
                            "Player Detected!",
                            "Player: " + player.getName().getString() +
                            "\nCoordinates: " + player.blockPosition().toShortString() +
                            "\nDistance: " + String.format("%.1f", mc.player.distanceTo(player)) + " blocks",
                            0xFFFF00
                        );
                        DiscordWebhook.sendMessage("", embed);
                        reportedPlayers.put(playerId, System.currentTimeMillis());
                    }
                }
            }
        }

        // Clean up old reported players every minute
        if (tickCounter % 1200 == 0) {
            reportedPlayers.entrySet().removeIf(entry ->
                System.currentTimeMillis() - entry.getValue() > playerCooldownMs);
        }

        // Check if ElytraController completed and send notification
        if (ElytraController.justCompleted() && notifyOnCompletion.get()) { // always consume the flag
            DiscordEmbed embed = new DiscordEmbed(
                "Scanning Completed!",
                "Stash hunter has finished scanning the designated area.\n" +
                "Total waypoints completed: " + ElytraController.getTotalWaypoints() +
                "\nFinal location: " + (mc.player != null ? mc.player.blockPosition().toShortString() : "Unknown"),
                0x0099FF
            );
            DiscordWebhook.sendMessage("@everyone", embed);
            info("Scanning completion notified to Discord");
        }
    }

    // ----- scan -----

    private Set<TraceGroup> enabledGroups() {
        Set<TraceGroup> enabled = EnumSet.noneOf(TraceGroup.class);
        traceGroups.forEach((group, setting) -> {
            if (setting.get()) enabled.add(group);
        });
        return enabled;
    }

    // The classic block list (Config.getActiveBlockList), plus spawners: their positions feed the
    // dungeon filter, and with include-spawners they are stash blocks.
    private static Set<Block> classicBlocks() {
        Set<Block> blocks = new HashSet<>(Config.getActiveBlockList());
        blocks.add(Blocks.SPAWNER);
        return Set.copyOf(blocks);
    }

    private void updateScanConfig(boolean classic, Set<TraceGroup> enabled) {
        ScanConfig config = new ScanConfig(classic, Set.copyOf(enabled), includeSpawners.get(), classic ? classicBlocks() : Set.of());
        if (!config.equals(lastScanConfig)) {
            scanSchedule.clear();
            lastScanConfig = config;
            lastScanClassic = classic;
            watchedBlocks = classic ? config.classicBlocks() : SignalBlocks.watchedBlocks();
        }
    }

    private void scanForBlocks() {
        if (mc.player == null || mc.level == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        Dimension dim = PlayerUtils.getDimension();
        String dimension = dim.name();
        boolean classic = sensitivity.get() == Sensitivity.CLASSIC;
        Set<TraceGroup> enabled = enabledGroups();
        updateScanConfig(classic, enabled);
        if (tickCounter - lastSchedulePruneTick >= RESCAN_TICKS) {
            scanSchedule.prune(tickCounter);
            lastSchedulePruneTick = tickCounter;
        }
        diagnostics.viewDistance(mc.options.getEffectiveRenderDistance());

        // Every loaded chunk touching the scan circle is scanned whole, so its index entry is complete.
        // Horizontal (cylindrical) distance on purpose: the bot flies far above the ground, so a
        // spherical radius around the player would never reach the stashes below it.
        int radius = scanRadius.get();
        long radiusSq = (long) radius * radius;
        int px = playerPos.getX(), pz = playerPos.getZ();
        int chunkRadius = (radius >> 4) + 1;
        int playerChunkX = px >> 4, playerChunkZ = pz >> 4;
        // Classic is the 26.2.2 scan: no entities.
        List<EntitySignalIndex.Seen> entities = classic ? List.of()
            : SignalEntities.collect(mc.level, playerPos, radius, enabled).seen();
        Set<Long> entityClearedChunks = new HashSet<>();
        Set<Long> scannedChunks = new HashSet<>();

        boolean scannedAny = false;
        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                long dx = Math.clamp(px, cx << 4, (cx << 4) + 15) - px;
                long dz = Math.clamp(pz, cz << 4, (cz << 4) + 15) - pz;
                if (dx * dx + dz * dz > radiusSq) continue;
                // Unloaded chunks read as an empty chunk and would wipe what we know about them.
                // Scanned once the server sends them, even if the player has flown on by then.
                if (!mc.level.hasChunk(cx, cz)) {
                    missedChunks.add(dimension, cx, cz, tickCounter);
                    continue;
                }
                if (scanChunkIfDue(dim, cx, cz, classic, enabled)) {
                    scannedAny = true;
                    scannedChunks.add(ChunkPos.pack(cx, cz));
                }
                long ex = (cx << 4) + 8 - px, ez = (cz << 4) + 8 - pz;
                if (ex * ex + ez * ez <= (long) ENTITY_REPLACE_RADIUS * ENTITY_REPLACE_RADIUS) {
                    entityClearedChunks.add(ChunkPos.pack(cx, cz));
                }
            }
        }
        if (!classic) updateEntitySignals(dimension, entities, entityClearedChunks, scannedChunks);

        // Chunks that arrived after they were in range. Those far behind the player get their own
        // cluster check below: the one around the player doesn't reach them anymore.
        List<BlockPos> lateCenters = new ArrayList<>();
        for (long key : missedChunks.takeLoaded(dimension, key -> mc.level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)))) {
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            if (!scanChunkIfDue(dim, cx, cz, classic, enabled)) continue;
            BlockPos center = new BlockPos((cx << 4) + 8, 0, (cz << 4) + 8);
            if (horizontalDistSq(center, px, pz) <= radiusSq) {
                scannedAny = true;
            } else if (lateCenters.stream().noneMatch(c -> horizontalDistSq(c, center.getX(), center.getZ()) <= 32 * 32)) {
                lateCenters.add(center);
            }
        }
        warnAboutMissedChunks(radius);

        // Nothing new around here, so clustering would reach the same result as last time.
        if (scannedAny) findStashes(dim, px, pz, radius);
        for (BlockPos center : lateCenters) findStashes(dim, center.getX(), center.getZ(), 32);
    }

    // Unchanged since its last scan: the index still holds its result. Returns whether it was scanned.
    // The whole chunk, not just the part inside the scan circle: the result replaces the chunk's
    // index entry, so a partial scan would forget blocks found there on an earlier pass.
    private boolean scanChunkIfDue(Dimension dim, int cx, int cz, boolean classic, Set<TraceGroup> enabled) {
        String dimension = dim.name();
        if (!scanSchedule.isDue(dimension, cx, cz, tickCounter)) return false;
        SignalBlocks.ChunkScan scan = SignalBlocks.scan(mc.level.getChunk(cx, cz), dim, enabled, includeSpawners.get(), classic);
        signalIndex.update(dimension, cx, cz, scan.signals());
        Map<BlockPos, Boolean> spawners = new HashMap<>();
        for (BlockPos pos : scan.spawners()) spawners.put(pos, Boolean.TRUE);
        spawnerIndex.update(dimension, cx, cz, spawners);
        scanSchedule.markScanned(dimension, cx, cz, tickCounter);
        diagnostics.chunkScanned();
        countSignals(scan.signals().values());
        return true;
    }

    private void updateEntitySignals(String dimension, List<EntitySignalIndex.Seen> entities,
                                     Set<Long> clearedChunks, Set<Long> scannedChunks) {
        entityIndex.update(dimension, entities, clearedChunks::contains);
        // Counted with their chunk's block scan, so a standing player doesn't count the same entities every scan.
        List<SignalKind> counted = new ArrayList<>();
        for (EntitySignalIndex.Seen seen : entities) {
            if (scannedChunks.contains(ChunkPos.pack(seen.pos()))) counted.add(seen.kind());
        }
        countSignals(counted);
    }

    private void countSignals(Collection<SignalKind> kinds) {
        if (kinds.isEmpty()) return;
        int[] counts = new int[SignalCategory.values().length];
        for (SignalKind kind : kinds) counts[kind.category().ordinal()]++;
        for (SignalCategory category : SignalCategory.values()) {
            if (counts[category.ordinal()] > 0) diagnostics.signals(category, counts[category.ordinal()]);
        }
    }

    private static long horizontalDistSq(BlockPos pos, int x, int z) {
        long dx = pos.getX() - x, dz = pos.getZ() - z;
        return dx * dx + dz * dz;
    }

    // Chunks the server never sent while they were in range are holes in the scanned area.
    private void warnAboutMissedChunks(int radius) {
        missedChunks.expire(tickCounter);
        int dropped = missedChunks.takeDropped();
        if (dropped > 0) diagnostics.chunksNeverLoaded(dropped);
        neverLoadedChunks += dropped;
        if (neverLoadedChunks < MISSED_WARNING_MIN_CHUNKS || tickCounter - lastMissedWarningTick < MISSED_WARNING_INTERVAL_TICKS) return;

        int viewBlocks = mc.options.getEffectiveRenderDistance() * 16;
        warning(neverLoadedChunks + " chunks inside scan-radius never loaded and were not scanned. Either the server sends chunks"
            + " slower than you fly, or its view distance (about " + viewBlocks + " blocks) is below scan-radius (" + radius + ").");
        neverLoadedChunks = 0;
        lastMissedWarningTick = tickCounter;
    }

    // ----- evaluation -----

    /**
     * Clusters everything known within {@code range} of x/z, including chunks scanned on earlier
     * passes, so a stash split across two flight strips is seen whole. The margin lets a cluster that
     * reaches into the range pull in its members from outside it.
     */
    private void findStashes(Dimension dim, int x, int z, int range) {
        Sensitivity sens = sensitivity.get();
        if (sens == Sensitivity.CLASSIC) {
            findStashesClassic(dim, x, z, range);
            return;
        }
        String dimension = dim.name();
        int reach = range + maxClusterSize.get() + 16;
        Set<TraceGroup> enabled = enabledGroups();
        boolean spawners = includeSpawners.get();

        // Settings may have changed since a chunk was scanned: drop kinds that are switched off now.
        List<Signal> signals = new ArrayList<>();
        signalIndex.near(dimension, x, z, reach).forEach((pos, kind) -> {
            if (isSignalEnabled(kind, enabled, spawners)) signals.add(new Signal(pos, kind));
        });
        for (Signal s : entityIndex.near(dimension, x, z, reach)) {
            if (isSignalEnabled(s.kind(), enabled, spawners)) signals.add(s);
        }
        for (Signal s : SignalHub.near(dimension, x, z, reach)) {
            if (isSignalEnabled(s.kind(), enabled, spawners)) signals.add(s);
        }

        // Without a strong kind or a trace only the container count can make a stash: skip the
        // clustering while there are too few containers for that, like 26.2.2 did.
        int threshold = blockDetectionThreshold.get();
        int minContainers = sens.containerThreshold(threshold);
        int containers = 0;
        boolean strongOrTrace = false;
        for (Signal s : signals) {
            switch (s.kind().category()) {
                case CONTAINER -> containers++;
                case STRONG, TRACE -> strongOrTrace = true;
                default -> {
                }
            }
        }
        if (!strongOrTrace && containers < minContainers) return;

        Set<BlockPos> spawnerPositions = spawnerIndex.near(dimension, x, z, reach).keySet();
        ContainerFilter filter = new ContainerFilter() {
            @Override
            public boolean filterNaturalStructures() {
                return filterNaturalStructures.get();
            }

            @Override
            public boolean hasStashShape(List<BlockPos> blocks) {
                return StashHunterModule.this.hasStashShape(blocks);
            }

            @Override
            public boolean nearStructureSpawner(BlockPos center) {
                return isNearDetectedStructure(center);
            }
        };
        for (Candidate candidate : SignalClusterer.candidates(signals, spawnerPositions, maxClusterDistance.get(),
            maxClusterSize.get() / 2, minContainers, filter)) {
            processCandidate(dim, candidate, sens, threshold);
        }
    }

    private static boolean isSignalEnabled(SignalKind kind, Set<TraceGroup> enabled, boolean includeSpawners) {
        if (kind == SignalKind.SPAWNER) return includeSpawners;
        return kind.group() == TraceGroup.NONE || enabled.contains(kind.group());
    }

    private void processCandidate(Dimension dim, Candidate candidate, Sensitivity sens, int threshold) {
        for (Rejection reason : candidate.filtered().keySet()) diagnose(dim, candidate.center(), candidate.signals(), reason);

        Verdict verdict = SignalScoring.evaluate(candidate.signals(), candidate.keptContainers(),
            candidate.containerShapeOk(), sens, threshold);
        if (verdict.tier() == Tier.NONE) {
            if (!candidate.filtered().isEmpty() || verdict.score() > 0) {
                diagnose(dim, candidate.center(), candidate.signals(), Rejection.LOW_SCORE);
            }
            return;
        }

        String dimension = dim.name();
        BlockPos center = candidate.center();
        Set<BlockPos> positions = new HashSet<>();
        for (Signal s : candidate.signals()) positions.add(s.pos());
        int dedupeRadius = reportDedupeRadius.get();
        List<StashReporter.FoundStash> earlier = reporter.earlierReportsOf(dim, positions);
        Tier known = StashReporter.max(reportedStashes().highestTierNear(dimension, center, dedupeRadius),
            StashReporter.knownTier(dim, center, dedupeRadius, earlier));

        // A higher tier than any report nearby is new (a hint growing into a stash is an update);
        // a stash reported this session is reported again once it grew well past its first report.
        boolean higher = verdict.tier().compareTo(known) > 0;
        boolean grew = !higher && verdict.tier() == Tier.STASH && known == Tier.STASH
            && grewEnough(earlier, positions.size());
        if (!higher && !grew) {
            diagnose(dim, center, candidate.signals(), Rejection.ALREADY_REPORTED);
            return;
        }

        StashReporter.Options options = reportOptions();
        if (verdict.tier() == Tier.HINT) {
            reporter.reportHint(dim, center, candidate.signals(), verdict.score(), options);
        } else {
            reporter.reportStash(dim, center, candidate.signals(), verdict, earlier, known != Tier.NONE,
                sens.toString(), sens.stashScore(), options);
        }
        reportedStashes().add(dimension, center, verdict.tier());
        diagnostics.reported(verdict.tier());
    }

    // Growth only counts against this session's stash reports (hints are upgraded, not grown).
    private static boolean grewEnough(List<StashReporter.FoundStash> earlier, int current) {
        int reported = -1;
        for (StashReporter.FoundStash stash : earlier) {
            if (stash.tier() == Tier.STASH) reported = Math.max(reported, stash.reportedBlocks());
        }
        return reported >= 0 && StashGrowth.grewEnough(reported, current);
    }

    private StashReporter.Options reportOptions() {
        return new StashReporter.Options(saveToXaero.get(), notificationDensityThreshold.get(), discordHints.get(),
            reportDedupeRadius.get());
    }

    /** Counts a rejected candidate once per period and logs it to the diagnostics file. */
    private void diagnose(Dimension dim, BlockPos center, List<Signal> signals, Rejection reason) {
        String key = dim.name() + ' ' + (center.getX() >> 3) + ' ' + (center.getZ() >> 3) + ' ' + reason;
        if (diagnosedCandidates.size() >= MAX_DIAGNOSED_CANDIDATES) diagnosedCandidates.clear();
        if (!diagnosedCandidates.add(key)) return;
        diagnostics.rejected(reason);
        diagnosticsLog().candidate(DimensionIds.idOf(dim), center, signals, reason);
    }

    // ----- classic: the 26.2.2 evaluation -----

    // Kinds of the classic block list, so entries scanned under other settings are left out.
    private Set<SignalKind> classicKinds() {
        Set<SignalKind> kinds = EnumSet.noneOf(SignalKind.class);
        for (Block block : Config.getActiveBlockList()) {
            SignalKind kind = SignalBlocks.kindOf(block);
            if (kind != null) kinds.add(kind);
        }
        if (includeSpawners.get()) kinds.add(SignalKind.SPAWNER);
        return kinds;
    }

    private void findStashesClassic(Dimension dim, int x, int z, int range) {
        Map<BlockPos, SignalKind> found = signalIndex.near(dim.name(), x, z, range + maxClusterSize.get() + 16);
        Set<SignalKind> active = classicKinds();
        found.values().removeIf(kind -> !active.contains(kind)); // settings may have changed since
        int threshold = blockDetectionThreshold.get();
        if (found.size() < threshold) return;

        // Group blocks into clusters to separate actual stashes from natural structures
        int linkDistance = maxClusterDistance.get(), maxRadius = maxClusterSize.get() / 2;
        for (List<BlockPos> cluster : StashClustering.cluster(new ArrayList<>(found.keySet()), linkDistance, maxRadius)) {
            if (cluster.size() < threshold) continue;
            // Too big or too sparse for a stash as a whole: a dense core inside it is still reported.
            List<List<BlockPos>> parts = filterNaturalStructures.get()
                ? StashClustering.tighten(cluster, linkDistance, maxRadius, threshold, this::hasStashShape)
                : List.of(cluster);
            for (List<BlockPos> part : parts) processClusterClassic(dim, part, found);
        }
    }

    // The volume and density limits of filter-natural-structures: natural structures spread a few
    // chests over a large space.
    private boolean hasStashShape(List<BlockPos> cluster) {
        double volume = StashClustering.boundingBoxVolume(cluster);
        return volume <= maxVolumeThreshold.get() && cluster.size() / volume >= minDensityThreshold.get();
    }

    // Block kinds come from the scan, not from the level: members of a cluster may lie in chunks
    // that are no longer loaded and would read as air.
    private void processClusterClassic(Dimension dim, List<BlockPos> cluster, Map<BlockPos, SignalKind> types) {
        BlockPos stashPos = StashClustering.center(cluster).orElse(null);
        if (stashPos == null) return;
        String dimension = dim.name();

        // Stashes reported this session that are part of this cluster. If it has grown well past
        // what was reported (the first report only saw part of it), the report is corrected.
        List<StashReporter.FoundStash> earlier = reporter.earlierReportsOf(dim, cluster);
        boolean update;
        if (!earlier.isEmpty() && earlier.stream().anyMatch(s -> s.tier() == Tier.STASH)) {
            if (!grewEnough(earlier, cluster.size())) return;
            update = true;
        } else {
            // Already reported near this location (earlier session, or cleared)? Only a hint from
            // another sensitivity is replaced, never a stash.
            int dedupeRadius = reportDedupeRadius.get();
            Tier known = StashReporter.max(reportedStashes().highestTierNear(dimension, stashPos, dedupeRadius),
                StashReporter.knownTier(dim, stashPos, dedupeRadius, earlier));
            if (known == Tier.STASH) {
                diagnose(dim, stashPos, signalsOf(cluster, types), Rejection.ALREADY_REPORTED);
                return;
            }
            update = known == Tier.HINT;
        }

        // Enhanced filtering for natural structures. A grown cluster that now looks natural keeps
        // its first report.
        Rejection natural = naturalStructureReason(stashPos, cluster, types);
        if (natural != null) {
            diagnose(dim, stashPos, signalsOf(cluster, types), natural);
            return;
        }

        reportedStashes().add(dimension, stashPos, Tier.STASH);
        reporter.reportStashClassic(dim, stashPos, cluster, types, earlier, update, storageOnlyMode.get(), reportOptions());
        diagnostics.reported(Tier.STASH);
    }

    private static List<Signal> signalsOf(List<BlockPos> cluster, Map<BlockPos, SignalKind> types) {
        List<Signal> signals = new ArrayList<>();
        for (BlockPos pos : cluster) {
            SignalKind kind = types.get(pos);
            if (kind != null) signals.add(new Signal(pos, kind));
        }
        return signals;
    }

    /**
     * Enhanced natural structure detection (classic). Returns why the cluster looks natural, or null.
     */
    private Rejection naturalStructureReason(BlockPos center, List<BlockPos> blocks, Map<BlockPos, SignalKind> types) {
        if (!filterNaturalStructures.get()) {
            return null;
        }

        // Volume and density: natural structures are large and sparse. findStashes has already
        // looked for a dense core, so this only rejects what is still left.
        if (!hasStashShape(blocks)) {
            return Rejection.STRUCTURE_SHAPE;
        }

        // A vanilla dungeon (spawner + up to two chests) is no base, however low the threshold is.
        if (includeSpawners.get() && isDungeonOnly(blocks, types)) {
            return Rejection.STRUCTURE_DUNGEON;
        }

        // Exact positions from the spawners activated-spawner-detector has seen (PLAN 67).
        if (isNearDetectedStructure(center)) {
            return Rejection.STRUCTURE_SPAWNER;
        }

        // No block-type checks here: the cluster only holds scanned stash blocks, so ratios of
        // structure materials (the old Woodland Mansion check) were always 0 and never matched.
        // Mineshaft loot sits in chest minecarts (entities) and never reaches the block scan.
        // Structures without spawners need the world-seed structure locator (PLAN 44).
        return null;
    }

    private boolean isNearDetectedStructure(BlockPos center) {
        int radius = structureFilterRadius.get();
        if (radius <= 0) return false;
        ActivatedSpawnerDetector detector = Modules.get().get(ActivatedSpawnerDetector.class);
        return detector != null && detector.isActive()
            && detector.isNearNaturalStructure(PlayerUtils.getDimension(), center, radius);
    }

    private static boolean isDungeonOnly(List<BlockPos> blocks, Map<BlockPos, SignalKind> types) {
        Set<BlockPos> spawners = new HashSet<>();
        Set<BlockPos> chests = new HashSet<>();
        for (BlockPos pos : blocks) {
            SignalKind kind = types.get(pos);
            if (kind == SignalKind.SPAWNER) spawners.add(pos);
            else if (kind == SignalKind.CHEST) chests.add(pos);
        }
        return DungeonFilter.isDungeonOnly(blocks, spawners, chests);
    }

    // ----- render and cache invalidation -----

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderStashes.get()) return;
        if (mc.level == null || mc.player == null || event.renderer == null) return;
        reporter.render(event, PlayerUtils.getDimension(), new StashReporter.RenderOptions(stashRenderDistance.get(),
            stashShapeMode.get(), stashSideColor.get(), stashLineColor.get(), hintSideColor.get(), hintLineColor.get(),
            stashBoundingBox.get(), stashTracers.get()));
    }

    // Chunk data arrived (again): its cached scan result may be outdated.
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.level == null) return;
        scanSchedule.invalidate(PlayerUtils.getDimension().name(), event.chunk().getPos().x(), event.chunk().getPos().z());
    }

    /**
     * Invalidates a chunk's cached scan when a block the scan evaluates changes. In normal/sensitive
     * also on any change above Y 260 (Overworld) or Y 127 (Nether): sky and nether roof builds depend
     * on every block there.
     */
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        // oldState is null when nothing changed (LevelChunk.setBlockState returns null then, e.g. for
        // a block update the server sends with the state the client already has). An exception here
        // would end the packet handling and disconnect the client.
        if (mc.level == null || event.oldState == null || event.newState == null) return;
        boolean relevant = watchedBlocks.contains(event.oldState.getBlock()) || watchedBlocks.contains(event.newState.getBlock());
        Dimension dim = PlayerUtils.getDimension();
        if (!relevant && !lastScanClassic) {
            int y = event.pos.getY();
            relevant = (dim == Dimension.Overworld && y > 260) || (dim == Dimension.Nether && y > 127);
        }
        if (!relevant) return;
        scanSchedule.invalidate(dim.name(), SectionPos.blockToSectionCoord(event.pos.getX()),
            SectionPos.blockToSectionCoord(event.pos.getZ()));
    }

    /**
     * Sign text and decorated pot contents change through block-entity data packets, not block
     * updates. Invalidating the chunk here is cheap; without it the 60 s rescan would catch them.
     * The packet arrives on the network thread, so the schedule is touched on the client thread.
     */
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundBlockEntityDataPacket packet)) return;
        BlockEntityType<?> type = packet.getType();
        if (type != BlockEntityTypes.SIGN && type != BlockEntityTypes.HANGING_SIGN && type != BlockEntityTypes.DECORATED_POT) return;
        BlockPos pos = packet.getPos();
        mc.execute(() -> {
            if (mc.level == null || !isActive() || lastScanClassic) return;
            scanSchedule.invalidate(PlayerUtils.getDimension().name(), SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        });
    }
}
