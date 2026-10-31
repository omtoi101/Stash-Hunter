package com.stashhunter.stashhunter.utils;

import com.stashhunter.stashhunter.StashHunter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class Config {
    private static final File CONFIG_FILE = new File(MeteorClient.FOLDER, "stash-hunter.properties");
    private static final Properties properties = new Properties();

    // Settings fire onChanged for every slider step; saveLater() coalesces those into one write.
    private static final ScheduledExecutorService SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "StashHunter-ConfigSave");
        thread.setDaemon(true);
        return thread;
    });
    private static final long SAVE_DELAY_MS = 1000;
    private static ScheduledFuture<?> pendingSave;
    private static boolean shutDown;
    // Set by saveLater(), cleared by every write. A debounced save that already started and waits
    // for the lock then finds nothing left to do instead of writing a second time.
    private static boolean dirty;

    // Default configuration values
    public static String discordWebhookUrl = "";
    public static int blockDetectionThreshold = 10; // Increased from 8 to reduce false positives
    public static int scanRadius = 64; // Reduced from 128 to avoid natural structures
    public static int flightAltitude = 160;
    public static int scanInterval = 40;
    public static boolean scanByDistance = true; // also scan after flying half the scan radius
    public static boolean scanOnlyWhileFlying = false; // off: the module also scans while standing/walking
    public static boolean playerDetection = true;
    public static int playerDetectionRadius = 100; // blocks
    public static int playerReportCooldown = 5; // minutes before the same player is reported again
    public static boolean notifyOnDeath = true;
    public static boolean notifyOnCompletion = true; // New setting for completion notifications
    public static boolean notifyOnDisconnect = true;
    public static boolean mentionEveryone = false; // @everyone in Discord messages is opt-in
    public static boolean storageOnlyMode = true; // Storage-only detection by default
    public static int maxVolumeThreshold = 5000; // Reduced from 10000 - more aggressive filtering
    public static boolean filterNaturalStructures = true;
    public static int structureFilterRadius = 0; // opt-in; only used while activated-spawner-detector is on
    public static boolean includeSpawners = false; // opt-in: mob spawners count as stash blocks
    public static double minDensityThreshold = 0.002; // New setting for minimum density
    public static double notificationDensityThreshold = 0.005; // New setting for Discord notification threshold
    public static int maxClusterDistance = 30; // New setting for clustering blocks
    public static int maxClusterSize = 64; // max cluster diameter, keeps neighbouring stashes apart
    public static int reportDedupeRadius = 48; // don't re-report a stash this close to a known one

    // Stuck Detector settings
    public static String stuckDetectorWebhookUrl = "";
    public static int stuckDetectorThreshold = 3;
    public static boolean stuckDetectorAutoFix = true;

    // Use Baritone (if installed) for flight/ground path execution instead of the built-in
    // flight controller. Purely a manual override - ElytraController falls back automatically
    // when Baritone isn't installed regardless of this setting.
    public static boolean useBaritonePathing = true;

    // Opt-in: after a completed scan let Baritone's elytra process land instead of leaving the
    // player gliding at cruise altitude. Needs Baritone elytra pathing; no built-in fallback.
    public static boolean landAfterCompletion = false;

    // Pause the scan and let Baritone land once the rockets in the inventory drop to this count:
    // Baritone's elytra flight can't climb or take off again without them.
    public static boolean landOnLowFireworks = true;
    public static int lowFireworkThreshold = 8;


    // Storage containers only (for stash finding)
    // As of 26.x, colored variants (shulker boxes, beds) are no longer individual Blocks
    // constants - they live behind a ColorCollection<Block>, hence the .asList() spreads.
    public static List<Block> storageBlocks = new ArrayList<>(Arrays.asList(
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.BARREL,

        // All shulker boxes
        Blocks.SHULKER_BOX
    ));
    static {
        storageBlocks.addAll(Blocks.DYED_SHULKER_BOX.asList());
    }

    // All stash-indicating blocks (for full stash detection)
    public static List<Block> stashBlocks = new ArrayList<>(Arrays.asList(
        // Storage containers
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.BARREL,

        // All shulker boxes
        Blocks.SHULKER_BOX,

        // Functional blocks that indicate a stash
        Blocks.FURNACE,
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.BREWING_STAND,
        Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL,
        Blocks.CHIPPED_ANVIL,
        Blocks.DAMAGED_ANVIL,
        Blocks.CRAFTING_TABLE,

        // Valuable/rare blocks
        Blocks.BEACON,
        Blocks.CONDUIT,

        // Player-placed redstone
        Blocks.HOPPER,
        Blocks.DISPENSER,
        Blocks.DROPPER
    ));
    static {
        // Shulker boxes (indicate a stash) and beds (indicate player presence)
        stashBlocks.addAll(Blocks.DYED_SHULKER_BOX.asList());
        stashBlocks.addAll(Blocks.BED.asList());
    }

    // Get the appropriate block list based on mode
    public static List<Block> getActiveBlockList() {
        return storageOnlyMode ? storageBlocks : stashBlocks;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
                properties.load(fis);

                discordWebhookUrl = properties.getProperty("discordWebhookUrl", "");
                blockDetectionThreshold = getIntProperty("blockDetectionThreshold", 10);
                scanRadius = getIntProperty("scanRadius", 64);
                flightAltitude = getIntProperty("flightAltitude", 160);
                scanInterval = getIntProperty("scanInterval", 40);
                scanByDistance = getBoolProperty("scanByDistance", true);
                scanOnlyWhileFlying = getBoolProperty("scanOnlyWhileFlying", false);
                playerDetection = getBoolProperty("playerDetection", true);
                playerDetectionRadius = getIntProperty("playerDetectionRadius", 100);
                playerReportCooldown = getIntProperty("playerReportCooldown", 5);
                notifyOnDeath = getBoolProperty("notifyOnDeath", true);
                notifyOnCompletion = getBoolProperty("notifyOnCompletion", true);
                notifyOnDisconnect = getBoolProperty("notifyOnDisconnect", true);
                mentionEveryone = getBoolProperty("mentionEveryone", false);
                storageOnlyMode = getBoolProperty("storageOnlyMode", true);
                maxVolumeThreshold = getIntProperty("maxVolumeThreshold", 5000);
                filterNaturalStructures = getBoolProperty("filterNaturalStructures", true);
                structureFilterRadius = getIntProperty("structureFilterRadius", 0);
                includeSpawners = getBoolProperty("includeSpawners", false);
                minDensityThreshold = getDoubleProperty("minDensityThreshold", 0.002);
                notificationDensityThreshold = getDoubleProperty("notificationDensityThreshold", 0.005);
                maxClusterDistance = getIntProperty("maxClusterDistance", 30);
                maxClusterSize = getIntProperty("maxClusterSize", 64);
                reportDedupeRadius = getIntProperty("reportDedupeRadius", 48);

                // Load Stuck Detector settings
                stuckDetectorWebhookUrl = properties.getProperty("stuckDetectorWebhookUrl", "");
                stuckDetectorThreshold = getIntProperty("stuckDetectorThreshold", 3);
                stuckDetectorAutoFix = getBoolProperty("stuckDetectorAutoFix", true);

                useBaritonePathing = getBoolProperty("useBaritonePathing", true);
                landAfterCompletion = getBoolProperty("landAfterCompletion", false);
                landOnLowFireworks = getBoolProperty("landOnLowFireworks", true);
                lowFireworkThreshold = getIntProperty("lowFireworkThreshold", 8);

            } catch (IOException e) {
                StashHunter.LOG.warn("Failed to load Stash-Hunter config", e);
            }
        } else {
            // Create config file with defaults if it doesn't exist
            save();
        }
    }

    public static synchronized void saveLater() {
        // After shutdown() the saver is gone; write straight away instead of throwing.
        if (shutDown) {
            save();
            return;
        }
        dirty = true;
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = SAVER.schedule(Config::saveIfDirty, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static synchronized void saveIfDirty() {
        if (dirty) save();
    }

    // Called from the JVM shutdown hook: the saver is a daemon thread, so a save still waiting out
    // its debounce delay would otherwise die with the JVM. Write it synchronously instead.
    public static synchronized void shutdown() {
        if (shutDown) return;
        shutDown = true;
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = null;
        SAVER.shutdownNow();
        if (dirty) save();
    }

    public static synchronized void save() {
        dirty = false;
        try {
            // AtomicFiles also creates the meteor client folder if it is missing.
            AtomicFiles.write(CONFIG_FILE.toPath(), fos -> {
                properties.setProperty("discordWebhookUrl", discordWebhookUrl);
                properties.setProperty("blockDetectionThreshold", String.valueOf(blockDetectionThreshold));
                properties.setProperty("scanRadius", String.valueOf(scanRadius));
                properties.setProperty("flightAltitude", String.valueOf(flightAltitude));
                properties.setProperty("scanInterval", String.valueOf(scanInterval));
                properties.setProperty("scanByDistance", String.valueOf(scanByDistance));
                properties.setProperty("scanOnlyWhileFlying", String.valueOf(scanOnlyWhileFlying));
                properties.setProperty("playerDetection", String.valueOf(playerDetection));
                properties.setProperty("playerDetectionRadius", String.valueOf(playerDetectionRadius));
                properties.setProperty("playerReportCooldown", String.valueOf(playerReportCooldown));
                properties.setProperty("notifyOnDeath", String.valueOf(notifyOnDeath));
                properties.setProperty("notifyOnCompletion", String.valueOf(notifyOnCompletion));
                properties.setProperty("notifyOnDisconnect", String.valueOf(notifyOnDisconnect));
                properties.setProperty("mentionEveryone", String.valueOf(mentionEveryone));
                properties.setProperty("storageOnlyMode", String.valueOf(storageOnlyMode));
                properties.setProperty("maxVolumeThreshold", String.valueOf(maxVolumeThreshold));
                properties.setProperty("filterNaturalStructures", String.valueOf(filterNaturalStructures));
                properties.setProperty("structureFilterRadius", String.valueOf(structureFilterRadius));
                properties.setProperty("includeSpawners", String.valueOf(includeSpawners));
                properties.setProperty("minDensityThreshold", String.valueOf(minDensityThreshold));
                properties.setProperty("notificationDensityThreshold", String.valueOf(notificationDensityThreshold));
                properties.setProperty("maxClusterDistance", String.valueOf(maxClusterDistance));
                properties.setProperty("maxClusterSize", String.valueOf(maxClusterSize));
                properties.setProperty("reportDedupeRadius", String.valueOf(reportDedupeRadius));

                // Save Stuck Detector settings
                properties.setProperty("stuckDetectorWebhookUrl", stuckDetectorWebhookUrl);
                properties.setProperty("stuckDetectorThreshold", String.valueOf(stuckDetectorThreshold));
                properties.setProperty("stuckDetectorAutoFix", String.valueOf(stuckDetectorAutoFix));

                properties.setProperty("useBaritonePathing", String.valueOf(useBaritonePathing));
                properties.setProperty("landAfterCompletion", String.valueOf(landAfterCompletion));
                properties.setProperty("landOnLowFireworks", String.valueOf(landOnLowFireworks));
                properties.setProperty("lowFireworkThreshold", String.valueOf(lowFireworkThreshold));

                properties.store(fos, "Stash-Hunter Configuration - Auto-generated");
            });
        } catch (IOException e) {
            StashHunter.LOG.warn("Failed to save Stash-Hunter config", e);
        }
    }

    private static int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            StashHunter.LOG.warn("Invalid integer value for {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    private static boolean getBoolProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    private static double getDoubleProperty(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            StashHunter.LOG.warn("Invalid double value for {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Validates the current configuration and fixes any invalid values
     */
    // Bounds mirror the hard min/max of the matching Meteor settings, so a value the UI accepts
    // is never clamped to something else on the next start (and vice versa).
    public static void validate() {
        boolean changed = false;

        if (blockDetectionThreshold < 1) {
            blockDetectionThreshold = 1;
            changed = true;
        }

        if (scanRadius < 16) {
            scanRadius = 16;
            changed = true;
        } else if (scanRadius > 256) {
            scanRadius = 256;
            changed = true;
        }

        if (lowFireworkThreshold < 1) {
            lowFireworkThreshold = 1;
            changed = true;
        } else if (lowFireworkThreshold > 64) {
            lowFireworkThreshold = 64;
            changed = true;
        }

        if (flightAltitude < 100) {
            flightAltitude = 100;
            changed = true;
        } else if (flightAltitude > 400) {
            flightAltitude = 400;
            changed = true;
        }

        if (scanInterval < 1) {
            scanInterval = 1;
            changed = true;
        } else if (scanInterval > 1200) {
            scanInterval = 1200;
            changed = true;
        }

        if (playerDetectionRadius < 8) {
            playerDetectionRadius = 8;
            changed = true;
        } else if (playerDetectionRadius > 512) {
            playerDetectionRadius = 512;
            changed = true;
        }

        if (playerReportCooldown < 1) {
            playerReportCooldown = 1;
            changed = true;
        } else if (playerReportCooldown > 120) {
            playerReportCooldown = 120;
            changed = true;
        }

        if (maxVolumeThreshold < 100) {
            maxVolumeThreshold = 100;
            changed = true;
        }

        if (structureFilterRadius < 0) {
            structureFilterRadius = 0;
            changed = true;
        } else if (structureFilterRadius > 64) {
            structureFilterRadius = 64;
            changed = true;
        }

        if (minDensityThreshold < 0.0001) {
            minDensityThreshold = 0.0001;
            changed = true;
        }

        if (notificationDensityThreshold < 0.0) {
            notificationDensityThreshold = 0.0;
            changed = true;
        }

        if (maxClusterDistance < 10) {
            maxClusterDistance = 10;
            changed = true;
        }

        if (maxClusterSize < 16) {
            maxClusterSize = 16;
            changed = true;
        }

        if (reportDedupeRadius < 8) {
            reportDedupeRadius = 8;
            changed = true;
        }

        if (stuckDetectorThreshold < 1) {
            stuckDetectorThreshold = 1;
            changed = true;
        }

        if (changed) {
            save();
        }
    }
}
