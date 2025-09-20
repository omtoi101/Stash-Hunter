package com.baseminer.basefinder.modules;

import com.baseminer.basefinder.BaseFinder;
import com.baseminer.basefinder.events.PlayerDeathEvent;
import com.baseminer.basefinder.utils.Config;
import com.baseminer.basefinder.utils.DiscordEmbed;
import com.baseminer.basefinder.utils.DiscordWebhook;
import com.baseminer.basefinder.utils.ElytraController;
import com.baseminer.basefinder.utils.WorldScanner;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BaseFinderModule extends Module {
    // Settings
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<String> discordWebhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("discord-webhook-url")
        .description("The Discord webhook URL to send notifications to.")
        .defaultValue(Config.discordWebhookUrl)
        .onChanged(v -> {
            Config.discordWebhookUrl = v;
            Config.save();
        })
        .build()
    );

    private final Setting<Integer> blockDetectionThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("block-detection-threshold")
        .description("The number of valuable blocks to find before a base is detected.")
        .defaultValue(Config.blockDetectionThreshold)
        .min(1)
        .sliderMax(50)
        .onChanged(v -> {
            Config.blockDetectionThreshold = v;
            Config.save();
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
            Config.save();
        })
        .build()
    );

    private final Setting<Boolean> storageOnlyMode = sgGeneral.add(new BoolSetting.Builder()
        .name("storage-only-mode")
        .description("Only search for storage containers (chests, shulkers). Disable to search for all base blocks.")
        .defaultValue(Config.storageOnlyMode)
        .onChanged(v -> {
            Config.storageOnlyMode = v;
            Config.save();
        })
        .build()
    );

    private final Setting<Integer> maxVolumeThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("max-volume-threshold")
        .description("Maximum volume to prevent natural structure detection.")
        .defaultValue(Config.maxVolumeThreshold)
        .min(0)
        .sliderMax(50000)
        .onChanged(v -> {
            Config.maxVolumeThreshold = v;
            Config.save();
        })
        .build()
    );

    private final Setting<Boolean> filterNaturalStructures = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-natural-structures")
        .description("Filter out likely natural structures (dungeons, etc.).")
        .defaultValue(Config.filterNaturalStructures)
        .onChanged(v -> {
            Config.filterNaturalStructures = v;
            Config.save();
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
            Config.save();
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
            Config.save();
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
            Config.save();
        })
        .build()
    );

    private final Setting<Integer> flightAltitude = sgGeneral.add(new IntSetting.Builder()
        .name("flight-altitude")
        .description("The altitude to fly at.")
        .defaultValue(Config.flightAltitude)
        .min(100)
        .sliderMax(400)
        .onChanged(v -> {
            Config.flightAltitude = v;
            Config.save();
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
            Config.save();
        })
        .build()
    );

    private final Setting<Boolean> playerDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("player-detection")
        .description("Whether to notify when a player is detected.")
        .defaultValue(Config.playerDetection)
        .onChanged(v -> {
            Config.playerDetection = v;
            Config.save();
        })
        .build()
    );

    private final Setting<Boolean> notifyOnDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-on-death")
        .description("Whether to notify when you die.")
        .defaultValue(Config.notifyOnDeath)
        .onChanged(v -> {
            Config.notifyOnDeath = v;
            Config.save();
        })
        .build()
    );

    private final Setting<Boolean> notifyOnCompletion = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-on-completion")
        .description("Whether to notify when scanning is completed.")
        .defaultValue(Config.notifyOnCompletion)
        .onChanged(v -> {
            Config.notifyOnCompletion = v;
            Config.save();
        })
        .build()
    );

    // State
    private final Map<PlayerEntity, Long> reportedPlayers = new ConcurrentHashMap<>();
    private final List<BlockPos> reportedBases = new ArrayList<>();
    private int tickCounter = 0;
    private int lastHealthCheck = -1; // Track health for death detection
    private int elytraBrokenTicks = 0;

    public BaseFinderModule() {
        super(BaseFinder.CATEGORY, "base-finder", "Automatically finds bases by flying around and scanning for valuable blocks.");
    }

    @Override
    public void onActivate() {
        reportedPlayers.clear();
        reportedBases.clear();
        lastHealthCheck = -1; // Reset health tracking
    }

    @Override
    public void onDeactivate() {
        ElytraController.stop();
    }

    public void clearReportedBases() {
        reportedBases.clear();
    }

    public void clearReportedPlayers() {
        reportedPlayers.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        ElytraController.onTick();

        if (mc.player == null || mc.world == null) {
            return;
        }

        // Elytra check
        if (ElytraController.isActive()) {
            ItemStack chestSlot = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            boolean elytraMissing = chestSlot.isEmpty() || (chestSlot.getItem() == Items.ELYTRA && chestSlot.isDamaged() && chestSlot.getDamage() >= chestSlot.getMaxDamage() - 1);

            if (elytraMissing) {
                elytraBrokenTicks++;
                if (elytraBrokenTicks > 40) { // ~2 seconds leeway
                    // Send Discord notification
                    DiscordEmbed embed = new DiscordEmbed(
                        "Out of Elytras!",
                        "The bot has run out of elytras or the equipped elytra broke, and will now disconnect.",
                        0xFF0000
                    );
                    DiscordWebhook.sendMessage("@everyone", embed);

                    // Disconnect from server
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().getConnection().disconnect(Text.of("Ran out of elytras or elytra broke."));
                    }

                    // Stop elytra controller
                    ElytraController.stop();

                    // Deactivate the module
                    toggle();
                    return; // Stop further processing in onTick
                }
            } else {
                elytraBrokenTicks = 0;
            }
        }

        // Death detection - check if player health dropped to 0 or respawned
        if (notifyOnDeath.get()) {
            float currentHealth = mc.player.getHealth();
            
            // If health went from positive to 0, player died
            if (lastHealthCheck > 0 && currentHealth <= 0) {
                DiscordEmbed embed = new DiscordEmbed(
                    "Bot Died!", 
                    "Death location: " + mc.player.getBlockPos().toShortString() +
                    "\nLast health: " + lastHealthCheck + " → 0", 
                    0xFF0000
                );
                DiscordWebhook.sendMessage("@everyone", embed);
                info("Death detected and notified to Discord");
            }
            
            // If we were at 0 health and now have health, we respawned
            if (lastHealthCheck <= 0 && currentHealth > 0) {
                DiscordEmbed embed = new DiscordEmbed(
                    "Bot Respawned", 
                    "Respawn location: " + mc.player.getBlockPos().toShortString() +
                    "\nHealth restored: " + currentHealth, 
                    0x00FF00
                );
                DiscordWebhook.sendMessage("", embed);
                info("Respawn detected and notified to Discord");
            }
            
            lastHealthCheck = (int)currentHealth;
        }

        // Scan for blocks every scanInterval ticks
        if (tickCounter % scanInterval.get() == 0) {
            scanForBlocks();
        }

        // Player detection logic
        if (playerDetection.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == null || player.equals(mc.player)) {
                    continue;
                }

                if (mc.player.distanceTo(player) < 100) {
                    if (!reportedPlayers.containsKey(player) ||
                        System.currentTimeMillis() - reportedPlayers.get(player) > 300000) { // 5 minute cooldown

                        DiscordEmbed embed = new DiscordEmbed(
                            "Player Detected!",
                            "Player: " + player.getName().getString() +
                            "\nCoordinates: " + player.getBlockPos().toShortString() +
                            "\nDistance: " + String.format("%.1f", mc.player.distanceTo(player)) + " blocks",
                            0xFFFF00
                        );
                        DiscordWebhook.sendMessage("", embed);
                        reportedPlayers.put(player, System.currentTimeMillis());
                    }
                }
            }
        }

        // Clean up old reported players every minute
        if (tickCounter % 1200 == 0) {
            reportedPlayers.entrySet().removeIf(entry ->
                System.currentTimeMillis() - entry.getValue() > 300000);
        }

        // Clean up old reported bases every 10 minutes
        if (tickCounter % 12000 == 0) {
            reportedBases.clear();
        }

        // Check if ElytraController completed and send notification
        if (notifyOnCompletion.get() && ElytraController.justCompleted()) {
            DiscordEmbed embed = new DiscordEmbed(
                "Scanning Completed!", 
                "Base finder has finished scanning the designated area.\n" +
                "Total waypoints completed: " + ElytraController.getTotalWaypoints() +
                "\nFinal location: " + (mc.player != null ? mc.player.getBlockPos().toShortString() : "Unknown"), 
                0x0099FF
            );
            DiscordWebhook.sendMessage("@everyone", embed);
            info("Scanning completion notified to Discord");
        }
    }

    private void scanForBlocks() {
        if (mc.player == null || mc.world == null) return;

        List<BlockPos> valuableBlocksInRange = new ArrayList<>();
        BlockPos playerPos = mc.player.getBlockPos();

        // Get chunks within scan radius
        int chunkRadius = (scanRadius.get() / 16) + 1;
        ChunkPos playerChunk = new ChunkPos(playerPos);

        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                Chunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);

                if (chunk != null) {
                    scanChunk(chunk, playerPos, valuableBlocksInRange);
                }
            }
        }

        if (valuableBlocksInRange.size() >= blockDetectionThreshold.get()) {
            // Group blocks into clusters to separate actual stashes from natural structures
            List<List<BlockPos>> clusters = clusterBlocks(valuableBlocksInRange, Config.maxClusterDistance);
            
            for (List<BlockPos> cluster : clusters) {
                if (cluster.size() >= blockDetectionThreshold.get()) {
                    processCluster(cluster);
                }
            }
        }
    }

    /**
     * Groups nearby blocks into clusters to separate stashes from natural structures
     */
    private List<List<BlockPos>> clusterBlocks(List<BlockPos> blocks, int maxDistance) {
        List<List<BlockPos>> clusters = new ArrayList<>();
        List<BlockPos> unprocessed = new ArrayList<>(blocks);

        while (!unprocessed.isEmpty()) {
            BlockPos seed = unprocessed.remove(0);
            List<BlockPos> cluster = new ArrayList<>();
            cluster.add(seed);

            // Find all blocks within maxDistance of any block in the cluster
            boolean foundNew = true;
            while (foundNew) {
                foundNew = false;
                List<BlockPos> toRemove = new ArrayList<>();

                for (BlockPos unprocessedBlock : unprocessed) {
                    for (BlockPos clusterBlock : cluster) {
                        if (unprocessedBlock.isWithinDistance(clusterBlock, maxDistance)) {
                            cluster.add(unprocessedBlock);
                            toRemove.add(unprocessedBlock);
                            foundNew = true;
                            break;
                        }
                    }
                }

                unprocessed.removeAll(toRemove);
            }

            clusters.add(cluster);
        }

        return clusters;
    }

    private void processCluster(List<BlockPos> cluster) {
        BlockPos basePos = calculateBaseCenter(cluster);

        // Check if we've already reported a base near this location
        boolean alreadyReported = reportedBases.stream()
            .anyMatch(reportedBase -> reportedBase.isWithinDistance(basePos, 100));

        if (alreadyReported) {
            return;
        }

        double volume = WorldScanner.getBoundingBoxVolume(cluster);
        double density = cluster.size() / Math.max(volume, 1.0);

        // Enhanced filtering for natural structures
        if (isNaturalStructure(cluster, volume, density)) {
            info("Skipped natural structure at " + basePos.toShortString() + 
                 " (volume: " + String.format("%.0f", volume) + 
                 ", density: " + String.format("%.6f", density) + ")");
            return;
        }

        reportedBases.add(basePos);
        reportBase(basePos, cluster, volume, density);
    }

    /**
     * Enhanced natural structure detection
     */
    private boolean isNaturalStructure(List<BlockPos> blocks, double volume, double density) {
        if (!filterNaturalStructures.get()) {
            return false;
        }

        // Volume check - natural structures tend to be very large
        if (volume > Config.maxVolumeThreshold) {
            return true;
        }

        // Density check - natural structures have very low density
        if (density < Config.minDensityThreshold) {
            return true;
        }

        // Count block types to identify natural structures
        Map<Block, Integer> blockCounts = countBlockTypes(blocks);
        int totalBlocks = blocks.size();

        // Check for mineshaft patterns (mostly chests with some barrels)
        int chestCount = blockCounts.getOrDefault(Blocks.CHEST, 0);
        int barrelCount = blockCounts.getOrDefault(Blocks.BARREL, 0);
        int shulkerCount = getTotalShulkerCount(blockCounts);

        // If more than 80% are regular chests/barrels and very few shulkers, likely natural
        double naturalStorageRatio = (double)(chestCount + barrelCount) / totalBlocks;
        double artificialStorageRatio = (double)shulkerCount / totalBlocks;

        if (naturalStorageRatio > 0.8 && artificialStorageRatio < 0.1) {
            return true;
        }

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

        // Check Y-level distribution - natural structures often span many Y levels
        int minY = blocks.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int maxY = blocks.stream().mapToInt(BlockPos::getY).max().orElse(0);
        int ySpread = maxY - minY;

        // If spread across more than 30 Y levels, likely natural structure
        if (ySpread > 30) {
            return true;
        }

        // Check horizontal spread vs vertical spread ratio
        int minX = blocks.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int maxX = blocks.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ = blocks.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxZ = blocks.stream().mapToInt(BlockPos::getZ).max().orElse(0);

        int xSpread = maxX - minX;
        int zSpread = maxZ - minZ;
        int horizontalSpread = Math.max(xSpread, zSpread);

        // If horizontal spread is much larger than vertical and density is low, likely natural
        if (horizontalSpread > 100 && ySpread < 20 && density < 0.01) {
            return true;
        }

        return false;
    }

    private Map<Block, Integer> countBlockTypes(List<BlockPos> blocks) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockPos pos : blocks) {
            try {
                Block block = mc.world.getBlockState(pos).getBlock();
                counts.put(block, counts.getOrDefault(block, 0) + 1);
            } catch (Exception e) {
                // Ignore errors when accessing block states
            }
        }
        return counts;
    }

    private int getTotalShulkerCount(Map<Block, Integer> blockCounts) {
        return blockCounts.entrySet().stream()
            .filter(entry -> isShulkerBox(entry.getKey()))
            .mapToInt(Map.Entry::getValue)
            .sum();
    }

    private boolean isShulkerBox(Block block) {
        return block == Blocks.SHULKER_BOX ||
               block == Blocks.BLACK_SHULKER_BOX ||
               block == Blocks.BLUE_SHULKER_BOX ||
               block == Blocks.BROWN_SHULKER_BOX ||
               block == Blocks.CYAN_SHULKER_BOX ||
               block == Blocks.GRAY_SHULKER_BOX ||
               block == Blocks.GREEN_SHULKER_BOX ||
               block == Blocks.LIGHT_BLUE_SHULKER_BOX ||
               block == Blocks.LIGHT_GRAY_SHULKER_BOX ||
               block == Blocks.LIME_SHULKER_BOX ||
               block == Blocks.MAGENTA_SHULKER_BOX ||
               block == Blocks.ORANGE_SHULKER_BOX ||
               block == Blocks.PINK_SHULKER_BOX ||
               block == Blocks.PURPLE_SHULKER_BOX ||
               block == Blocks.RED_SHULKER_BOX ||
               block == Blocks.WHITE_SHULKER_BOX ||
               block == Blocks.YELLOW_SHULKER_BOX;
    }

    private void scanChunk(Chunk chunk, BlockPos playerPos, List<BlockPos> valuableBlocks) {
        List<Block> activeBlocks = Config.getActiveBlockList();

        // Scan through all block entities in the chunk first (most efficient)
        chunk.getBlockEntityPositions().forEach(pos -> {
            if (pos.isWithinDistance(playerPos, scanRadius.get())) {
                try {
                    BlockState blockState = mc.world.getBlockState(pos);
                    if (activeBlocks.contains(blockState.getBlock())) {
                        valuableBlocks.add(pos.toImmutable());
                    }
                } catch (Exception e) {
                    // Ignore errors when accessing block states
                }
            }
        });

        // Only do full block scanning if not in storage-only mode and within a smaller radius
        if (!storageOnlyMode.get()) {
            int limitedScanRadius = Math.min(scanRadius.get(), 32); // Much smaller radius for full scanning

            for (int x = chunk.getPos().getStartX(); x <= chunk.getPos().getEndX(); x += 2) { // Skip every other block
                for (int z = chunk.getPos().getStartZ(); z <= chunk.getPos().getEndZ(); z += 2) {
                    if (Math.sqrt(Math.pow(x - playerPos.getX(), 2) + Math.pow(z - playerPos.getZ(), 2)) > limitedScanRadius) {
                        continue;
                    }

                    // Scan only common base building heights
                    int minY = Math.max(mc.world.getBottomY(), -64);
                    int maxY = Math.min(mc.world.getHeight(), 80); // Focus on common base heights

                    for (int y = minY; y < maxY; y += 2) { // Skip every other Y level for performance
                        BlockPos pos = new BlockPos(x, y, z);
                        if (pos.isWithinDistance(playerPos, limitedScanRadius)) {
                            try {
                                BlockState blockState = mc.world.getBlockState(pos);
                                if (activeBlocks.contains(blockState.getBlock())) {
                                    valuableBlocks.add(pos.toImmutable());
                                }
                            } catch (Exception e) {
                                // Ignore errors when accessing block states
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockPos calculateBaseCenter(List<BlockPos> blocks) {
        if (blocks.isEmpty()) return mc.player.getBlockPos();

        int totalX = 0, totalY = 0, totalZ = 0;
        for (BlockPos pos : blocks) {
            totalX += pos.getX();
            totalY += pos.getY();
            totalZ += pos.getZ();
        }

        return new BlockPos(
            totalX / blocks.size(),
            totalY / blocks.size(),
            totalZ / blocks.size()
        );
    }

    private void reportBase(BlockPos basePos, List<BlockPos> valuableBlocks, double volume, double density) {
        String coords = basePos.toShortString();
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

        String modeInfo = storageOnlyMode.get() ? " (Storage Only)" : " (Full Base)";
        String logMessage = String.format("%s at: %s with %d valuable blocks (density: %.6f)",
            storageOnlyMode.get() ? "Stash Found" : "Base Found", coords, valuableBlocks.size(), density);
        
        // Always log to console/chat
        info(logMessage);

        // Only send Discord notification if density meets threshold
        if (density >= Config.notificationDensityThreshold) {
            String description = String.format(
                "Coordinates: %s%s\n" +
                "Found %d valuable blocks\n" +
                "Volume: %.2f blocks\n" +
                "Density: %.6f\n" +
                "Rating: %s\n\n" +
                "Container List:\n%s",
                coords, modeInfo, valuableBlocks.size(), volume, density, rating, containerList.toString()
            );

            String title = storageOnlyMode.get() ? "Stash Found!" : "Base Found!";
            DiscordEmbed embed = new DiscordEmbed(title, description, 0x00FF00);
            DiscordWebhook.sendMessage("@everyone", embed);

            // Create a waypoint
            String waypointName = String.format("%d Blocks at %s", valuableBlocks.size(), coords);
            Waypoint waypoint = new Waypoint.Builder()
                .name(waypointName)
                .pos(basePos)
                .build();
            Waypoints.get().add(waypoint);
            info("Created waypoint: " + waypointName);
        } else {
            // Log that we skipped notification due to low density
            info("Skipped Discord notification for low density stash (density: " + String.format("%.6f", density) + 
                 ", threshold: " + String.format("%.6f", Config.notificationDensityThreshold) + ")");
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