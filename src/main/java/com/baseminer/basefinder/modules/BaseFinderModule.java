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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
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

    // State
    private final Map<PlayerEntity, Long> reportedPlayers = new ConcurrentHashMap<>();
    private final List<BlockPos> reportedBases = new ArrayList<>();
    private int tickCounter = 0;

    public BaseFinderModule() {
        super(BaseFinder.CATEGORY, "base-finder", "Automatically finds bases by flying around and scanning for valuable blocks.");
    }

    @Override
    public void onActivate() {
        reportedPlayers.clear();
        reportedBases.clear();
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
    private void onPlayerDeath(PlayerDeathEvent event) {
        if (notifyOnDeath.get() && event.player != null && event.player.equals(mc.player)) {
            DiscordEmbed embed = new DiscordEmbed("Bot Died!", "Coordinates: " + event.player.getBlockPos().toShortString(), 0xFF0000);
            DiscordWebhook.sendMessage("@everyone", embed);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        ElytraController.onTick();

        if (mc.player == null || mc.world == null) {
            return;
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
            BlockPos basePos = calculateBaseCenter(valuableBlocksInRange);

            // Check if we've already reported a base near this location
            boolean alreadyReported = reportedBases.stream()
                .anyMatch(reportedBase -> reportedBase.isWithinDistance(basePos, 200));

            if (!alreadyReported) {
                reportedBases.add(basePos);
                reportBase(basePos, valuableBlocksInRange);
            }
        }
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

    private void reportBase(BlockPos basePos, List<BlockPos> valuableBlocks) {
        double volume = WorldScanner.getBoundingBoxVolume(valuableBlocks);

        // Filter out likely natural structures
        Map<Block, Integer> counts = WorldScanner.countBlocks(valuableBlocks);
        List<Block> blockTypes = new ArrayList<>(counts.keySet());

        if (Config.isLikelyNaturalStructure(blockTypes, volume)) {
            // Skip reporting this as it's likely a natural structure
            info("Skipped likely natural structure at " + basePos.toShortString() +
                 " (volume: " + String.format("%.0f", volume) + ")");
            return;
        }

        String coords = basePos.toShortString();
        double density = valuableBlocks.size() / Math.max(volume, 1.0);
        String rating = getRatingFromDensity(density);

        StringBuilder containerList = new StringBuilder();
        counts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .forEach(entry -> containerList
                .append(entry.getValue())
                .append("x ")
                .append(entry.getKey().getName().getString())
                .append("\n"));

        String modeInfo = storageOnlyMode.get() ? " (Storage Only)" : " (Full Base)";
        String description = String.format(
            "Coordinates: %s%s\n" +
            "Found %d valuable blocks\n" +
            "Volume: %.2f blocks\n" +
            "Density: %.4f\n" +
            "Rating: %s\n\n" +
            "Container List:\n%s",
            coords, modeInfo, valuableBlocks.size(), volume, density, rating, containerList.toString()
        );

        String title = storageOnlyMode.get() ? "Stash Found!" : "Base Found!";
        DiscordEmbed embed = new DiscordEmbed(title, description, 0x00FF00);
        DiscordWebhook.sendMessage("@everyone", embed);

        String logMessage = String.format("%s at: %s with %d valuable blocks (density: %.4f)",
            title.replace("!", ""), coords, valuableBlocks.size(), density);
        info(logMessage);
    }

    private String getRatingFromDensity(double density) {
        if (density > 0.5) return "Very High Density";
        if (density > 0.2) return "High Density";
        if (density > 0.1) return "Medium Density";
        if (density > 0.05) return "Low Density";
        return "Very Low Density";
    }
}
