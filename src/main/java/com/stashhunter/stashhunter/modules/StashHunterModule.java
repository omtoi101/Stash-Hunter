package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.utils.ChunkScanSchedule;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.DungeonFilter;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.KeyHold;
import com.stashhunter.stashhunter.utils.ReportedStashes;
import com.stashhunter.stashhunter.utils.ScanTrigger;
import com.stashhunter.stashhunter.utils.ServerKey;
import com.stashhunter.stashhunter.utils.StashBlockIndex;
import com.stashhunter.stashhunter.utils.StashClustering;
import com.stashhunter.stashhunter.utils.StashGrowth;
import com.stashhunter.stashhunter.xaero.XaeroBridge;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        .description("The number of valuable blocks to find before a stash is detected.")
        .defaultValue(Config.blockDetectionThreshold)
        .min(1)
        .sliderMax(50)
        .onChanged(v -> {
            Config.blockDetectionThreshold = v;
            Config.saveLater();
        })
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
        .description("Only search for storage containers (chests, shulkers). Disable to search for all stash blocks.")
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

    @SuppressWarnings("unused") // value is read via Config.landOnLowFireworks
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

    // Stash ESP
    private final SettingGroup sgRender = settings.createGroup("Stash ESP");

    private final Setting<Boolean> renderStashes = sgRender.add(new BoolSetting.Builder()
        .name("render-stashes")
        .description("Outlines the containers of stashes found this session.")
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

    private final Setting<Boolean> stashBoundingBox = sgRender.add(new BoolSetting.Builder()
        .name("stash-bounding-box")
        .description("Also draws one box around all containers of a stash.")
        .defaultValue(false)
        .visible(renderStashes::get)
        .build()
    );

    private final Setting<Boolean> stashTracers = sgRender.add(new BoolSetting.Builder()
        .name("stash-tracers")
        .description("Draws a tracer to the nearest found stash.")
        .defaultValue(false)
        .visible(renderStashes::get)
        .build()
    );

    // State
    private final Map<UUID, Long> reportedPlayers = new ConcurrentHashMap<>();
    // Stashes reported this session, for report corrections and the Stash ESP. Not persisted.
    private final List<FoundStash> foundStashes = new ArrayList<>();
    // One file per server under the Meteor folder; loaded lazily for the server we're on.
    private static final Path REPORTED_STASHES_DIR = MeteorClient.FOLDER.toPath().resolve("StashHunter").resolve("reported-stashes");
    private ReportedStashes reportedStashes;
    // Stash blocks of every scanned chunk, so clusters can span several scans/flight strips.
    private final StashBlockIndex<Block> stashBlockIndex = new StashBlockIndex<>(8192);
    // A chunk is only read again when it changed or after this long (catches missed updates).
    private static final int RESCAN_TICKS = 1200;
    private final ChunkScanSchedule scanSchedule = new ChunkScanSchedule(RESCAN_TICKS);
    // Block set of the last scan: a settings change makes every cached chunk result stale.
    private Set<Block> lastScanBlocks = Set.of();
    private Set<Block> lastPlainBlocks = Set.of();
    private int lastSchedulePruneTick = 0;
    private static final String STASH_WAYPOINT_MARKER = " Blocks at "; // part of every stash waypoint name
    private int tickCounter = 0;
    private int ticksSinceScan = Integer.MAX_VALUE / 2; // large = scan on the first tick
    private double lastScanX, lastScanZ;
    private float lastHealth = -1; // Track health for death detection, -1 = not sampled yet
    private int elytraBrokenTicks = 0;
    private int reEngagingElytraTicks = 0;
    private boolean wasElytraBroken = false;
    private boolean holdingJump = false;
    private ResourceKey<Level> lastDimension = null; // Track dimension for world-change teardown

    public StashHunterModule() {
        super(StashHunter.CATEGORY, "stash-hunter", "Automatically finds stashes by flying around and scanning for valuable blocks. Also scans while standing unless scan-only-while-flying is on.");
    }

    @Override
    public void onActivate() {
        reportedPlayers.clear();
        reportedStashes = null; // reloaded from disk for the current server
        stashBlockIndex.clear();
        scanSchedule.clear();
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

        if (useBaritonePathing.get() && !BaritoneBridge.isModLoaded()) {
            info("Baritone is not installed - falling back to the built-in flight controller. " +
                "Install the Baritone Fabric mod (26.2) for precise pathfinding.");
        }
    }

    @Override
    public void onDeactivate() {
        ElytraController.stop();
        setHoldingJump(false);
        foundStashes.clear();
    }

    /** Sets the cruise altitude (clamped to the setting's 100-400 range); a running flight follows at once. */
    public int setFlightAltitude(int altitude) {
        flightAltitude.set(Math.max(100, Math.min(400, altitude)));
        return flightAltitude.get();
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
        foundStashes.clear(); // they get reported (and highlighted) again, so no duplicate boxes
    }

    private ReportedStashes reportedStashes() {
        if (reportedStashes == null) reportedStashes = ReportedStashes.load(REPORTED_STASHES_DIR.resolve(ServerKey.current() + ".txt"));
        return reportedStashes;
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
        stashBlockIndex.clear();
        scanSchedule.clear();
        foundStashes.clear();
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

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        ElytraController.onTick();

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

    private void scanForBlocks() {
        if (mc.player == null || mc.level == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        String dimension = PlayerUtils.getDimension().name();
        Set<Block> activeBlocks = new HashSet<>(Config.getActiveBlockList());
        // Spawners have a block entity, so they come through the block-entity pass in either mode.
        if (includeSpawners.get()) activeBlocks.add(Blocks.SPAWNER);

        // Containers are found exactly through the block-entity list, so the block-by-block scan
        // only needs the active blocks without a block entity. That also keeps a container from
        // being counted by both passes.
        Set<Block> plainBlocks = new HashSet<>();
        if (!storageOnlyMode.get()) {
            for (Block block : activeBlocks) {
                if (!block.defaultBlockState().hasBlockEntity()) plainBlocks.add(block);
            }
        }
        if (!activeBlocks.equals(lastScanBlocks) || !plainBlocks.equals(lastPlainBlocks)) {
            scanSchedule.clear();
            lastScanBlocks = Set.copyOf(activeBlocks);
            lastPlainBlocks = Set.copyOf(plainBlocks);
        }
        if (tickCounter - lastSchedulePruneTick >= RESCAN_TICKS) {
            scanSchedule.prune(tickCounter);
            lastSchedulePruneTick = tickCounter;
        }

        // Every loaded chunk touching the scan circle is scanned whole, so its index entry is complete.
        // Horizontal (cylindrical) distance on purpose: the bot flies far above the ground, so a
        // spherical radius around the player would never reach the stashes below it.
        int radius = scanRadius.get();
        long radiusSq = (long) radius * radius;
        int px = playerPos.getX(), pz = playerPos.getZ();
        int chunkRadius = (radius >> 4) + 1;
        int playerChunkX = px >> 4, playerChunkZ = pz >> 4;

        boolean scannedAny = false;
        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                // Unloaded chunks read as an empty chunk and would wipe what we know about them.
                if (!mc.level.hasChunk(cx, cz)) continue;
                long dx = Math.clamp(px, cx << 4, (cx << 4) + 15) - px;
                long dz = Math.clamp(pz, cz << 4, (cz << 4) + 15) - pz;
                if (dx * dx + dz * dz > radiusSq) continue;
                // Unchanged since its last scan: the index still holds its result.
                if (!scanSchedule.isDue(dimension, cx, cz, tickCounter)) continue;

                stashBlockIndex.update(dimension, cx, cz, scanChunk(mc.level.getChunk(cx, cz), activeBlocks, plainBlocks));
                scanSchedule.markScanned(dimension, cx, cz, tickCounter);
                scannedAny = true;
            }
        }
        // Nothing new around here, so clustering would reach the same result as last time.
        if (!scannedAny) return;

        // Cluster over everything found around here, including chunks scanned on earlier passes,
        // so a stash split across two flight strips is seen whole. The margin lets a cluster that
        // reaches into the scan circle pull in its members from outside it.
        Map<BlockPos, Block> found = stashBlockIndex.near(dimension, px, pz, radius + maxClusterSize.get() + 16);
        found.values().removeIf(block -> !activeBlocks.contains(block)); // settings may have changed since
        if (found.size() < blockDetectionThreshold.get()) return;

        // Group blocks into clusters to separate actual stashes from natural structures
        List<List<BlockPos>> clusters = StashClustering.cluster(new ArrayList<>(found.keySet()), maxClusterDistance.get(), maxClusterSize.get() / 2);
        for (List<BlockPos> cluster : clusters) {
            if (cluster.size() >= blockDetectionThreshold.get()) {
                processCluster(cluster, found);
            }
        }
    }

    // Block types come from the scan, not from the level: members of a cluster may lie in chunks
    // that are no longer loaded and would read as air.
    private void processCluster(List<BlockPos> cluster, Map<BlockPos, Block> types) {
        BlockPos stashPos = StashClustering.center(cluster).orElse(null);
        if (stashPos == null) return;

        Dimension dim = PlayerUtils.getDimension();
        String dimension = dim.name();

        // Stashes reported this session that are part of this cluster. If it has grown well past
        // what was reported (the first report only saw part of it), the report is corrected.
        List<FoundStash> earlier = earlierReportsOf(dim, cluster);
        if (!earlier.isEmpty()) {
            int reported = earlier.stream().mapToInt(FoundStash::reportedBlocks).max().orElse(0);
            if (!StashGrowth.grewEnough(reported, cluster.size())) return;
        } else {
            // Check if we've already reported a base near this location (earlier session, or cleared)
            int dedupeRadius = reportDedupeRadius.get();
            if (reportedStashes().isNear(dimension, stashPos, dedupeRadius) || hasStashWaypointNear(stashPos, dedupeRadius)) {
                return;
            }
        }

        double volume = StashClustering.boundingBoxVolume(cluster);
        double density = cluster.size() / Math.max(volume, 1.0);

        // Enhanced filtering for natural structures. A grown cluster that now looks natural keeps
        // its first report.
        if (isNaturalStructure(stashPos, cluster, types, volume, density)) {
            return;
        }

        for (FoundStash old : earlier) {
            foundStashes.remove(old);
            if (old.waypoint() != null) Waypoints.get().remove(old.waypoint());
            XaeroBridge.removeWaypoint(old.xaeroWaypoint());
        }
        reportedStashes().add(dimension, stashPos);
        Waypoint waypoint = reportStash(stashPos, cluster, types, volume, density, earlier);
        Object xaeroWaypoint = waypoint != null && saveToXaero.get() ? XaeroBridge.addWaypoint(stashPos, waypoint.name.get()) : null;
        foundStashes.add(new FoundStash(dim, stashPos, Set.copyOf(cluster), cluster.size(), waypoint, xaeroWaypoint,
            highlightOf(dim, cluster, types)));
    }

    private List<FoundStash> earlierReportsOf(Dimension dimension, List<BlockPos> cluster) {
        List<Set<BlockPos>> known = new ArrayList<>();
        for (FoundStash stash : foundStashes) known.add(stash.dimension() == dimension ? stash.blocks() : Set.of());
        List<FoundStash> result = new ArrayList<>();
        for (int i : StashGrowth.overlapping(known, cluster)) result.add(foundStashes.get(i));
        return result;
    }

    // In full mode a cluster also holds ores/spawners etc., so only the real containers are kept.
    private StashHighlight highlightOf(Dimension dimension, List<BlockPos> cluster, Map<BlockPos, Block> types) {
        List<BlockPos> containers = new ArrayList<>();
        for (BlockPos pos : cluster) {
            if (isContainer(types.get(pos))) containers.add(pos.immutable());
        }
        return containers.isEmpty() ? null : new StashHighlight(dimension, containers);
    }

    /**
     * A stash reported this session: the reported position, all blocks of its cluster (to recognise
     * it when more of it is found), the block count it was reported with, its waypoint (null if none
     * was created), its XaeroBridge waypoint handle (null if none) and its Stash ESP highlight (null
     * without containers). Not persisted.
     */
    private record FoundStash(Dimension dimension, BlockPos position, Set<BlockPos> blocks, int reportedBlocks,
                              Waypoint waypoint, Object xaeroWaypoint, StashHighlight highlight) {
    }

    // A set, not Config.storageBlocks (a list): onRender asks for every highlighted container each frame.
    private static final Set<Block> CONTAINERS = containers();

    private static Set<Block> containers() {
        Set<Block> containers = new HashSet<>(Config.storageBlocks);
        containers.addAll(List.of(
            Blocks.HOPPER, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
            Blocks.DISPENSER, Blocks.DROPPER, Blocks.ENDER_CHEST
        ));
        return Set.copyOf(containers);
    }

    private boolean isContainer(Block block) {
        return CONTAINERS.contains(block);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderStashes.get() || foundStashes.isEmpty()) return;
        if (mc.level == null || mc.player == null || event.renderer == null) return;

        Dimension dimension = PlayerUtils.getDimension();
        double maxDistanceSq = (double) stashRenderDistance.get() * stashRenderDistance.get();
        ShapeMode mode = stashShapeMode.get();
        SettingColor sides = stashSideColor.get();
        SettingColor lines = stashLineColor.get();

        StashHighlight nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (FoundStash found : foundStashes) {
            StashHighlight stash = found.highlight();
            if (stash == null || stash.dimension() != dimension) continue;
            double distSq = mc.player.distanceToSqr(stash.centerX(), stash.centerY(), stash.centerZ());
            if (distSq > maxDistanceSq) continue;

            for (BlockPos p : stash.containers()) {
                // Skip containers that were broken since; unloaded chunks read as air, so keep those.
                if (mc.level.isLoaded(p) && !isContainer(mc.level.getBlockState(p).getBlock())) continue;
                event.renderer.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, sides, lines, mode, 0);
            }
            if (stashBoundingBox.get()) {
                event.renderer.box(stash.min().getX(), stash.min().getY(), stash.min().getZ(),
                    stash.max().getX() + 1, stash.max().getY() + 1, stash.max().getZ() + 1, sides, lines, ShapeMode.Lines, 0);
            }
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = stash;
            }
        }

        if (stashTracers.get() && nearest != null) {
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                nearest.centerX(), nearest.centerY(), nearest.centerZ(), lines);
        }
    }

    private record StashHighlight(Dimension dimension, List<BlockPos> containers, BlockPos min, BlockPos max) {
        StashHighlight(Dimension dimension, List<BlockPos> containers) {
            this(dimension, List.copyOf(containers), corner(containers, true), corner(containers, false));
        }

        private static BlockPos corner(List<BlockPos> blocks, boolean min) {
            int x = blocks.get(0).getX(), y = blocks.get(0).getY(), z = blocks.get(0).getZ();
            for (BlockPos p : blocks) {
                x = min ? Math.min(x, p.getX()) : Math.max(x, p.getX());
                y = min ? Math.min(y, p.getY()) : Math.max(y, p.getY());
                z = min ? Math.min(z, p.getZ()) : Math.max(z, p.getZ());
            }
            return new BlockPos(x, y, z);
        }

        double centerX() { return (min.getX() + max.getX() + 1) / 2.0; }
        double centerY() { return (min.getY() + max.getY() + 1) / 2.0; }
        double centerZ() { return (min.getZ() + max.getZ() + 1) / 2.0; }
    }

    // Survives module toggles and reconnects (Meteor persists waypoints per server). Also catches
    // stashes reported before reportedStashes was persisted, or after it was cleared.
    private boolean hasStashWaypointNear(BlockPos pos, int radius) {
        var dimension = PlayerUtils.getDimension();
        for (Waypoint waypoint : Waypoints.get()) {
            if (waypoint.dimension.get() == dimension
                && waypoint.name.get().contains(STASH_WAYPOINT_MARKER)
                && waypoint.pos.get().closerThan(pos, radius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enhanced natural structure detection
     */
    private boolean isNaturalStructure(BlockPos center, List<BlockPos> blocks, Map<BlockPos, Block> types, double volume, double density) {
        if (!filterNaturalStructures.get()) {
            return false;
        }

        // Volume check - natural structures tend to be very large
        if (volume > maxVolumeThreshold.get()) {
            return true;
        }

        // Density check - natural structures have very low density
        if (density < minDensityThreshold.get()) {
            return true;
        }

        // A vanilla dungeon (spawner + up to two chests) is no base, however low the threshold is.
        if (includeSpawners.get() && isDungeonOnly(blocks, types)) {
            return true;
        }

        // Exact positions from the spawners activated-spawner-detector has seen (PLAN 67).
        if (isNearDetectedStructure(center)) {
            return true;
        }

        // No block-type checks here: the cluster only holds scanned stash blocks, so ratios of
        // structure materials (the old Woodland Mansion check) were always 0 and never matched.
        // Mineshaft loot sits in chest minecarts (entities) and never reaches the block scan.
        // Structures without spawners need the world-seed structure locator (PLAN 44).
        return false;
    }

    private boolean isNearDetectedStructure(BlockPos center) {
        int radius = structureFilterRadius.get();
        if (radius <= 0) return false;
        ActivatedSpawnerDetector detector = Modules.get().get(ActivatedSpawnerDetector.class);
        return detector != null && detector.isActive()
            && detector.isNearNaturalStructure(PlayerUtils.getDimension(), center, radius);
    }

    private boolean isDungeonOnly(List<BlockPos> blocks, Map<BlockPos, Block> types) {
        Set<BlockPos> spawners = new HashSet<>();
        Set<BlockPos> chests = new HashSet<>();
        for (BlockPos pos : blocks) {
            Block block = types.get(pos);
            if (block == Blocks.SPAWNER) spawners.add(pos);
            else if (block == Blocks.CHEST) chests.add(pos);
        }
        return DungeonFilter.isDungeonOnly(blocks, spawners, chests);
    }

    private Map<Block, Integer> countBlockTypes(List<BlockPos> blocks, Map<BlockPos, Block> types) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockPos pos : blocks) {
            Block block = types.get(pos);
            if (block != null) counts.merge(block, 1, Integer::sum);
        }
        return counts;
    }

    // The whole chunk, not just the part inside the scan circle: the result replaces the chunk's
    // index entry, so a partial scan would forget blocks found there on an earlier pass.
    // Chunk data arrived (again): its cached scan result may be outdated.
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.level == null) return;
        scanSchedule.invalidate(PlayerUtils.getDimension().name(), event.chunk().getPos().x(), event.chunk().getPos().z());
    }

    // Only changes involving a scanned block type matter (a chest placed or broken, ...).
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.level == null) return;
        if (!lastScanBlocks.contains(event.oldState.getBlock()) && !lastScanBlocks.contains(event.newState.getBlock())) return;
        scanSchedule.invalidate(PlayerUtils.getDimension().name(), SectionPos.blockToSectionCoord(event.pos.getX()),
            SectionPos.blockToSectionCoord(event.pos.getZ()));
    }

    private Map<BlockPos, Block> scanChunk(LevelChunk chunk, Set<Block> activeBlocks, Set<Block> plainBlocks) {
        Map<BlockPos, Block> found = new HashMap<>();

        // Scan through all block entities in the chunk first (most efficient)
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            Block block = chunk.getBlockState(pos).getBlock();
            if (activeBlocks.contains(block)) found.put(pos.immutable(), block);
        }

        if (plainBlocks.isEmpty()) return found;

        // Block-by-block scan, limited to sections whose palette can contain one of the plain blocks.
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()
                || !section.maybeHas(state -> plainBlocks.contains(state.getBlock()))) continue;

            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            // y, z, x: the order the section stores its blocks in.
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block block = section.getBlockState(x, y, z).getBlock();
                        if (plainBlocks.contains(block)) found.put(new BlockPos(minX + x, baseY + y, minZ + z), block);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Reports a new stash, or corrects the reports in {@code earlier} (same stash, first seen only in
     * part). Returns the created waypoint, or null if none was created.
     */
    private Waypoint reportStash(BlockPos stashPos, List<BlockPos> valuableBlocks, Map<BlockPos, Block> types,
                                 double volume, double density, List<FoundStash> earlier) {
        boolean update = !earlier.isEmpty();
        String coords = stashPos.toShortString();
        String rating = getRatingFromDensity(density);
        Dimension dimension = PlayerUtils.getDimension();

        Map<Block, Integer> counts = countBlockTypes(valuableBlocks, types);
        StringBuilder containerList = new StringBuilder();
        counts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .forEach(entry -> containerList
                .append(entry.getValue())
                .append("x ")
                .append(entry.getKey().getName().getString())
                .append("\n"));

        String modeInfo = storageOnlyMode.get() ? " (Storage Only)" : " (Full Stash)";
        StringBuilder previously = new StringBuilder();
        for (FoundStash old : earlier) {
            if (!previously.isEmpty()) previously.append(", ");
            previously.append(old.reportedBlocks()).append(" blocks at ").append(old.position().toShortString());
        }

        String logMessage = String.format("Stash %s at: %s (%s) with %d valuable blocks (density: %.6f)%s",
            update ? "Updated" : "Found", coords, dimension, valuableBlocks.size(), density,
            update ? " - previously reported: " + previously : "");

        // Always log to console/chat
        info(logMessage);

        // Only send Discord notification if density meets threshold. A stash already announced
        // on Discord gets its correction there too, even if the bigger cluster is less dense.
        boolean announcedBefore = earlier.stream().anyMatch(old -> old.waypoint() != null);
        if (density >= notificationDensityThreshold.get() || announcedBefore) {
            String description = String.format(
                (update ? "More of an already reported stash was found.\nPreviously reported: " + previously + "\n\n" : "") +
                "Coordinates: %s%s\n" +
                "Dimension: %s\n" +
                "Found %d valuable blocks\n" +
                "Volume: %.2f blocks\n" +
                "Density: %.6f\n" +
                "Rating: %s\n\n" +
                "Container List:\n%s",
                coords, modeInfo, dimension, valuableBlocks.size(), volume, density, rating, containerList.toString()
            );

            // No second @everyone for a correction
            String title = update ? "Stash Updated" : "Stash Found!";
            DiscordEmbed embed = new DiscordEmbed(title, description, update ? 0x33CC99 : 0x00FF00);
            DiscordWebhook.sendMessage(update ? "" : "@everyone", embed);

            // Create a waypoint
            String waypointName = valuableBlocks.size() + STASH_WAYPOINT_MARKER + coords;
            Waypoint waypoint = new Waypoint.Builder()
                .name(waypointName)
                .pos(stashPos)
                .dimension(dimension)
                .build();
            Waypoints.get().add(waypoint);
            info((update ? "Moved waypoint to: " : "Created waypoint: ") + waypointName);
            return waypoint;
        }

        // Log that we skipped notification due to low density
        info("Skipped Discord notification for low density stash (density: " + String.format("%.6f", density) +
             ", threshold: " + String.format("%.6f", notificationDensityThreshold.get()) + ")");
        return null;
    }

    private String getRatingFromDensity(double density) {
        if (density > 0.1) return "Very High Density";
        if (density > 0.01) return "High Density";
        if (density > 0.005) return "Medium Density";
        if (density > 0.001) return "Low Density";
        return "Very Low Density";
    }
}
