package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.events.PlayerDisconnectEvent;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.WorldScanner;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
        .defaultValue(64)
        .min(16)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> reportDedupeRadius = sgGeneral.add(new IntSetting.Builder()
        .name("report-dedupe-radius")
        .description("A stash closer than this to an already reported one (or to an existing stash waypoint) is not reported again.")
        .defaultValue(48)
        .min(8)
        .sliderMax(200)
        .build()
    );

    @SuppressWarnings("unused") // value is read via Config.flightAltitude
    private final Setting<Integer> flightAltitude = sgGeneral.add(new IntSetting.Builder()
        .name("flight-altitude")
        .description("The altitude to fly at.")
        .defaultValue(Config.flightAltitude)
        .min(100)
        .sliderMax(400)
        .onChanged(v -> {
            Config.flightAltitude = v;
            Config.saveLater();
        })
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("The interval in ticks between scans.")
        .defaultValue(Config.scanInterval)
        .min(1)
        .sliderMax(100)
        .onChanged(v -> {
            Config.scanInterval = v;
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

    // State
    private final Map<UUID, Long> reportedPlayers = new ConcurrentHashMap<>();
    private final List<BlockPos> reportedStashes = new ArrayList<>();
    private static final String STASH_WAYPOINT_MARKER = " Blocks at "; // part of every stash waypoint name
    private int tickCounter = 0;
    private float lastHealth = -1; // Track health for death detection, -1 = not sampled yet
    private int elytraBrokenTicks = 0;
    private int reEngagingElytraTicks = 0;
    private boolean wasElytraBroken = false;
    private boolean holdingJump = false;
    private ResourceKey<Level> lastDimension = null; // Track dimension for world-change teardown

    public StashHunterModule() {
        super(StashHunter.CATEGORY, "stash-hunter", "Automatically finds stashes by flying around and scanning for valuable blocks.");
    }

    @Override
    public void onActivate() {
        reportedPlayers.clear();
        reportedStashes.clear();
        lastHealth = -1; // Reset health tracking
        lastDimension = null;

        // Reset elytra/tick state so a re-enable can't instantly trip the "out of elytras"
        // path (Discord blast + disconnect) from a stale broken-elytra counter, nor resume a
        // leftover jump hold or completion/notification timer from the previous run.
        elytraBrokenTicks = 0;
        wasElytraBroken = false;
        reEngagingElytraTicks = 0;
        tickCounter = 0;
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
    }

    // Looked up lazily: this module is registered before AutoElytraRepair, so it isn't available at construction time.
    private AutoElytraRepair autoElytraRepair() {
        return Modules.get().get(AutoElytraRepair.class);
    }

    // Only release the jump key if we pressed it, so the player (and other modules) can still use it.
    private void setHoldingJump(boolean hold) {
        if (hold == holdingJump) return;
        holdingJump = hold;
        mc.options.keyJump.setDown(hold);
    }

    public void clearReportedStashes() {
        reportedStashes.clear();
    }

    public void clearReportedPlayers() {
        reportedPlayers.clear();
    }

    @EventHandler
    private void onDisconnect(PlayerDisconnectEvent event) {
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
        if (ElytraController.isActive()) ElytraController.pause();
        reportedStashes.clear();
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
            reportedStashes.clear(); // positions are per dimension; existing waypoints still dedupe
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

        // Scan for blocks every scanInterval ticks
        if (tickCounter % scanInterval.get() == 0) {
            scanForBlocks();
        }

        // Player detection logic
        if (playerDetection.get()) {
            for (Player player : mc.level.players()) {
                if (player == null || player.equals(mc.player)) {
                    continue;
                }

                if (mc.player.distanceTo(player) < 100) {
                    // Keyed by UUID: the entity object is recreated whenever a player re-enters render distance
                    UUID playerId = player.getUUID();
                    if (!reportedPlayers.containsKey(playerId) ||
                        System.currentTimeMillis() - reportedPlayers.get(playerId) > 300000) { // 5 minute cooldown

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
                System.currentTimeMillis() - entry.getValue() > 300000);
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

        List<BlockPos> valuableBlocksInRange = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        Set<Block> activeBlocks = new HashSet<>(Config.getActiveBlockList());

        // Containers are found exactly through the block-entity list, so the block-by-block scan
        // only needs the active blocks without a block entity. That also keeps a container from
        // being counted by both passes.
        Set<Block> plainBlocks = new HashSet<>();
        if (!storageOnlyMode.get()) {
            for (Block block : activeBlocks) {
                if (!block.defaultBlockState().hasBlockEntity()) plainBlocks.add(block);
            }
        }

        // Get chunks within scan radius
        int chunkRadius = (scanRadius.get() / 16) + 1;
        ChunkPos playerChunk = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                LevelChunk chunk = mc.level.getChunk(playerChunk.x() + x, playerChunk.z() + z);

                if (chunk != null) {
                    scanChunk(chunk, playerPos, activeBlocks, plainBlocks, valuableBlocksInRange);
                }
            }
        }

        if (valuableBlocksInRange.size() >= blockDetectionThreshold.get()) {
            // Group blocks into clusters to separate actual stashes from natural structures
            List<List<BlockPos>> clusters = clusterBlocks(valuableBlocksInRange, maxClusterDistance.get(), maxClusterSize.get() / 2);

            for (List<BlockPos> cluster : clusters) {
                if (cluster.size() >= blockDetectionThreshold.get()) {
                    processCluster(cluster);
                }
            }
        }
    }

    /**
     * Groups nearby blocks into clusters to separate stashes from natural structures.
     * Seeds are taken densest-first; a block joins a cluster when it is within linkDistance of a
     * member and within maxRadius of the seed, so chains of blocks can't grow a cluster without bound.
     */
    private List<List<BlockPos>> clusterBlocks(List<BlockPos> blocks, int linkDistance, int maxRadius) {
        long linkSq = (long) linkDistance * linkDistance;
        long radiusSq = (long) maxRadius * maxRadius;

        // Grid with cell size = linkDistance: every neighbour of a block lies in the 27 surrounding cells.
        Map<Long, List<BlockPos>> grid = new HashMap<>();
        for (BlockPos pos : blocks) {
            grid.computeIfAbsent(cellKey(pos, linkDistance), k -> new ArrayList<>()).add(pos);
        }

        Map<BlockPos, Integer> neighbourCount = new HashMap<>();
        for (BlockPos pos : blocks) {
            neighbourCount.put(pos, neighbours(pos, grid, linkDistance, linkSq).size());
        }
        List<BlockPos> seeds = new ArrayList<>(blocks);
        seeds.sort(Comparator.comparingInt((BlockPos pos) -> neighbourCount.get(pos)).reversed());

        List<List<BlockPos>> clusters = new ArrayList<>();
        Set<BlockPos> assigned = new HashSet<>();
        for (BlockPos seed : seeds) {
            if (!assigned.add(seed)) continue;

            List<BlockPos> cluster = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            cluster.add(seed);
            queue.add(seed);
            while (!queue.isEmpty()) {
                for (BlockPos next : neighbours(queue.poll(), grid, linkDistance, linkSq)) {
                    if (next.distSqr(seed) <= radiusSq && assigned.add(next)) {
                        cluster.add(next);
                        queue.add(next);
                    }
                }
            }
            clusters.add(cluster);
        }

        return clusters;
    }

    private static long cellKey(BlockPos pos, int cellSize) {
        return BlockPos.asLong(Math.floorDiv(pos.getX(), cellSize), Math.floorDiv(pos.getY(), cellSize), Math.floorDiv(pos.getZ(), cellSize));
    }

    private static List<BlockPos> neighbours(BlockPos pos, Map<Long, List<BlockPos>> grid, int cellSize, long linkSq) {
        List<BlockPos> result = new ArrayList<>();
        int cx = Math.floorDiv(pos.getX(), cellSize), cy = Math.floorDiv(pos.getY(), cellSize), cz = Math.floorDiv(pos.getZ(), cellSize);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<BlockPos> cell = grid.get(BlockPos.asLong(cx + dx, cy + dy, cz + dz));
                    if (cell == null) continue;
                    for (BlockPos other : cell) {
                        if (other != pos && other.distSqr(pos) < linkSq) result.add(other);
                    }
                }
            }
        }
        return result;
    }

    private void processCluster(List<BlockPos> cluster) {
        BlockPos stashPos = WorldScanner.calculateCenter(cluster);

        // Check if we've already reported a base near this location
        int dedupeRadius = reportDedupeRadius.get();
        boolean alreadyReported = reportedStashes.stream()
            .anyMatch(reportedStash -> reportedStash.closerThan(stashPos, dedupeRadius));

        if (alreadyReported || hasStashWaypointNear(stashPos, dedupeRadius)) {
            return;
        }

        double volume = WorldScanner.getBoundingBoxVolume(cluster);
        double density = cluster.size() / Math.max(volume, 1.0);

        // Enhanced filtering for natural structures
        if (isNaturalStructure(cluster, volume, density)) {
            return;
        }

        reportedStashes.add(stashPos);
        reportStash(stashPos, cluster, volume, density);
    }

    // Survives module toggles and reconnects (Meteor persists waypoints per server), unlike reportedStashes.
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
    private boolean isNaturalStructure(List<BlockPos> blocks, double volume, double density) {
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

        // Count block types to identify natural structures
        Map<Block, Integer> blockCounts = countBlockTypes(blocks);
        int totalBlocks = blocks.size();

        // Check for mineshaft patterns (mostly chests with some barrels)
        int chestCount = blockCounts.getOrDefault(Blocks.CHEST, 0);
        // Check for Woodland Mansion: 44 chests and mostly wood/cobblestone
        if (chestCount == 44) {
            int darkOakLogCount = blockCounts.getOrDefault(Blocks.DARK_OAK_LOG, 0);
            int darkOakPlanksCount = blockCounts.getOrDefault(Blocks.DARK_OAK_PLANKS, 0);
            int cobblestoneCount = blockCounts.getOrDefault(Blocks.COBBLESTONE, 0);
            double mansionBlockRatio = (double)(darkOakLogCount + darkOakPlanksCount + cobblestoneCount) / totalBlocks;

            if (mansionBlockRatio > 0.5) {
                return true; // Likely a Woodland Mansion
            }
        }



        return false;
    }

    private Map<Block, Integer> countBlockTypes(List<BlockPos> blocks) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockPos pos : blocks) {
            try {
                Block block = mc.level.getBlockState(pos).getBlock();
                counts.put(block, counts.getOrDefault(block, 0) + 1);
            } catch (Exception e) {
                // Ignore errors when accessing block states
            }
        }
        return counts;
    }

    // Horizontal (cylindrical) distance on purpose: the bot flies far above the ground, so a
    // spherical radius around the player would never reach the stashes below it.
    private void scanChunk(LevelChunk chunk, BlockPos playerPos, Set<Block> activeBlocks, Set<Block> plainBlocks, List<BlockPos> valuableBlocks) {
        int radius = scanRadius.get();
        int radiusSq = radius * radius;
        int px = playerPos.getX(), pz = playerPos.getZ();

        // Scan through all block entities in the chunk first (most efficient)
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            int dx = pos.getX() - px, dz = pos.getZ() - pz;
            if (dx * dx + dz * dz < radiusSq && activeBlocks.contains(chunk.getBlockState(pos).getBlock())) {
                valuableBlocks.add(pos.immutable());
            }
        }

        if (plainBlocks.isEmpty()) return;

        // Block-by-block scan, limited to sections whose palette can contain one of the plain blocks.
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()
                || !section.maybeHas(state -> plainBlocks.contains(state.getBlock()))) continue;

            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int dx = minX + x - px, dz = minZ + z - pz;
                    if (dx * dx + dz * dz >= radiusSq) continue;

                    for (int y = 0; y < 16; y++) {
                        if (plainBlocks.contains(section.getBlockState(x, y, z).getBlock())) {
                            valuableBlocks.add(new BlockPos(minX + x, baseY + y, minZ + z));
                        }
                    }
                }
            }
        }
    }

    private void reportStash(BlockPos stashPos, List<BlockPos> valuableBlocks, double volume, double density) {
        String coords = stashPos.toShortString();
        String rating = getRatingFromDensity(density);

        Map<Block, Integer> counts = countBlockTypes(valuableBlocks);
        StringBuilder containerList = new StringBuilder();
        counts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .forEach(entry -> containerList
                .append(entry.getValue())
                .append("x ")
                .append(entry.getKey().getName().getString())
                .append("\n"));

        String modeInfo = storageOnlyMode.get() ? " (Storage Only)" : " (Full Stash)";
        String logMessage = String.format("Stash Found at: %s with %d valuable blocks (density: %.6f)",
            coords, valuableBlocks.size(), density);

        // Always log to console/chat
        info(logMessage);

        // Only send Discord notification if density meets threshold
        if (density >= notificationDensityThreshold.get()) {
            String description = String.format(
                "Coordinates: %s%s\n" +
                "Found %d valuable blocks\n" +
                "Volume: %.2f blocks\n" +
                "Density: %.6f\n" +
                "Rating: %s\n\n" +
                "Container List:\n%s",
                coords, modeInfo, valuableBlocks.size(), volume, density, rating, containerList.toString()
            );

            String title = "Stash Found!";
            DiscordEmbed embed = new DiscordEmbed(title, description, 0x00FF00);
            DiscordWebhook.sendMessage("@everyone", embed);

            // Create a waypoint
            String waypointName = valuableBlocks.size() + STASH_WAYPOINT_MARKER + coords;
            Waypoint waypoint = new Waypoint.Builder()
                .name(waypointName)
                .pos(stashPos)
                .dimension(PlayerUtils.getDimension())
                .build();
            Waypoints.get().add(waypoint);
            info("Created waypoint: " + waypointName);
        } else {
            // Log that we skipped notification due to low density
            info("Skipped Discord notification for low density stash (density: " + String.format("%.6f", density) +
                 ", threshold: " + String.format("%.6f", notificationDensityThreshold.get()) + ")");
        }
    }

    private String getRatingFromDensity(double density) {
        if (density > 0.1) return "Very High Density";
        if (density > 0.01) return "High Density";
        if (density > 0.005) return "Medium Density";
        if (density > 0.001) return "Low Density";
        return "Very Low Density";
    }
}
