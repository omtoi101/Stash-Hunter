package com.baseminer.basefinder.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class Config {
    private static final File CONFIG_FILE = new File(MeteorClient.FOLDER, "base-finder.properties");
    private static final Properties properties = new Properties();

    // Default configuration values
    public static String discordWebhookUrl = "";
    public static int blockDetectionThreshold = 10; // Increased from 8 to reduce false positives
    public static int scanRadius = 64; // Reduced from 128 to avoid natural structures
    public static int flightAltitude = 320;
    public static int scanInterval = 40;
    public static boolean playerDetection = true;
    public static boolean notifyOnDeath = true;
    public static boolean notifyOnCompletion = true; // New setting for completion notifications
    public static boolean notifyOnDisconnect = true;
    public static boolean storageOnlyMode = true; // Storage-only detection by default
    public static int maxVolumeThreshold = 5000; // Reduced from 10000 - more aggressive filtering
    public static boolean filterNaturalStructures = true;
    public static double minDensityThreshold = 0.002; // New setting for minimum density
    public static double notificationDensityThreshold = 0.005; // New setting for Discord notification threshold
    public static int maxClusterDistance = 30; // New setting for clustering blocks

    // Stuck Detector settings
    public static String stuckDetectorWebhookUrl = "";
    public static int stuckDetectorThreshold = 3;
    public static boolean stuckDetectorAutoFix = true;

    // Storage containers only (for stash finding)
    public static List<Block> storageBlocks = new ArrayList<>(Arrays.asList(
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.BARREL,

        // All shulker boxes
        Blocks.SHULKER_BOX,
        Blocks.BLACK_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX,
        Blocks.BROWN_SHULKER_BOX,
        Blocks.CYAN_SHULKER_BOX,
        Blocks.GRAY_SHULKER_BOX,
        Blocks.GREEN_SHULKER_BOX,
        Blocks.LIGHT_BLUE_SHULKER_BOX,
        Blocks.LIGHT_GRAY_SHULKER_BOX,
        Blocks.LIME_SHULKER_BOX,
        Blocks.MAGENTA_SHULKER_BOX,
        Blocks.ORANGE_SHULKER_BOX,
        Blocks.PINK_SHULKER_BOX,
        Blocks.PURPLE_SHULKER_BOX,
        Blocks.RED_SHULKER_BOX,
        Blocks.WHITE_SHULKER_BOX,
        Blocks.YELLOW_SHULKER_BOX
    ));

    // All base-indicating blocks (for full base detection)
    public static List<Block> baseBlocks = new ArrayList<>(Arrays.asList(
        // Storage containers
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.BARREL,

        // All shulker boxes
        Blocks.SHULKER_BOX,
        Blocks.BLACK_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX,
        Blocks.BROWN_SHULKER_BOX,
        Blocks.CYAN_SHULKER_BOX,
        Blocks.GRAY_SHULKER_BOX,
        Blocks.GREEN_SHULKER_BOX,
        Blocks.LIGHT_BLUE_SHULKER_BOX,
        Blocks.LIGHT_GRAY_SHULKER_BOX,
        Blocks.LIME_SHULKER_BOX,
        Blocks.MAGENTA_SHULKER_BOX,
        Blocks.ORANGE_SHULKER_BOX,
        Blocks.PINK_SHULKER_BOX,
        Blocks.PURPLE_SHULKER_BOX,
        Blocks.RED_SHULKER_BOX,
        Blocks.WHITE_SHULKER_BOX,
        Blocks.YELLOW_SHULKER_BOX,

        // Functional blocks that indicate a base
        Blocks.FURNACE,
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.BREWING_STAND,
        Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL,
        Blocks.CHIPPED_ANVIL,
        Blocks.DAMAGED_ANVIL,
        Blocks.CRAFTING_TABLE,

        // Beds (indicate player presence)
        Blocks.WHITE_BED,
        Blocks.ORANGE_BED,
        Blocks.MAGENTA_BED,
        Blocks.LIGHT_BLUE_BED,
        Blocks.YELLOW_BED,
        Blocks.LIME_BED,
        Blocks.PINK_BED,
        Blocks.GRAY_BED,
        Blocks.LIGHT_GRAY_BED,
        Blocks.CYAN_BED,
        Blocks.PURPLE_BED,
        Blocks.BLUE_BED,
        Blocks.BROWN_BED,
        Blocks.GREEN_BED,
        Blocks.RED_BED,
        Blocks.BLACK_BED,

        // Valuable/rare blocks
        Blocks.BEACON,
        Blocks.CONDUIT,

        // Player-placed redstone
        Blocks.HOPPER,
        Blocks.DISPENSER,
        Blocks.DROPPER
    ));

    // Get the appropriate block list based on mode
    public static List<Block> getActiveBlockList() {
        return storageOnlyMode ? storageBlocks : baseBlocks;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
                properties.load(fis);

                discordWebhookUrl = properties.getProperty("discordWebhookUrl", "");
                blockDetectionThreshold = getIntProperty("blockDetectionThreshold", 10);
                scanRadius = getIntProperty("scanRadius", 64);
                flightAltitude = getIntProperty("flightAltitude", 320);
                scanInterval = getIntProperty("scanInterval", 40);
                playerDetection = getBoolProperty("playerDetection", true);
                notifyOnDeath = getBoolProperty("notifyOnDeath", true);
                notifyOnCompletion = getBoolProperty("notifyOnCompletion", true);
                notifyOnDisconnect = getBoolProperty("notifyOnDisconnect", true);
                storageOnlyMode = getBoolProperty("storageOnlyMode", true);
                maxVolumeThreshold = getIntProperty("maxVolumeThreshold", 5000);
                filterNaturalStructures = getBoolProperty("filterNaturalStructures", true);
                minDensityThreshold = getDoubleProperty("minDensityThreshold", 0.002);
                notificationDensityThreshold = getDoubleProperty("notificationDensityThreshold", 0.005);
                maxClusterDistance = getIntProperty("maxClusterDistance", 30);

                // Load Stuck Detector settings
                stuckDetectorWebhookUrl = properties.getProperty("stuckDetectorWebhookUrl", "");
                stuckDetectorThreshold = getIntProperty("stuckDetectorThreshold", 3);
                stuckDetectorAutoFix = getBoolProperty("stuckDetectorAutoFix", true);

            } catch (IOException e) {
                System.err.println("Failed to load Base Finder config: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Create config file with defaults if it doesn't exist
            save();
        }
    }

    public static void save() {
        try {
            // Ensure the meteor client folder exists
            if (!MeteorClient.FOLDER.exists()) {
                MeteorClient.FOLDER.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
                properties.setProperty("discordWebhookUrl", discordWebhookUrl);
                properties.setProperty("blockDetectionThreshold", String.valueOf(blockDetectionThreshold));
                properties.setProperty("scanRadius", String.valueOf(scanRadius));
                properties.setProperty("flightAltitude", String.valueOf(flightAltitude));
                properties.setProperty("scanInterval", String.valueOf(scanInterval));
                properties.setProperty("playerDetection", String.valueOf(playerDetection));
                properties.setProperty("notifyOnDeath", String.valueOf(notifyOnDeath));
                properties.setProperty("notifyOnCompletion", String.valueOf(notifyOnCompletion));
                properties.setProperty("notifyOnDisconnect", String.valueOf(notifyOnDisconnect));
                properties.setProperty("storageOnlyMode", String.valueOf(storageOnlyMode));
                properties.setProperty("maxVolumeThreshold", String.valueOf(maxVolumeThreshold));
                properties.setProperty("filterNaturalStructures", String.valueOf(filterNaturalStructures));
                properties.setProperty("minDensityThreshold", String.valueOf(minDensityThreshold));
                properties.setProperty("notificationDensityThreshold", String.valueOf(notificationDensityThreshold));
                properties.setProperty("maxClusterDistance", String.valueOf(maxClusterDistance));

                // Save Stuck Detector settings
                properties.setProperty("stuckDetectorWebhookUrl", stuckDetectorWebhookUrl);
                properties.setProperty("stuckDetectorThreshold", String.valueOf(stuckDetectorThreshold));
                properties.setProperty("stuckDetectorAutoFix", String.valueOf(stuckDetectorAutoFix));

                properties.store(fos, "Base Finder Configuration - Auto-generated");
            }
        } catch (IOException e) {
            System.err.println("Failed to save Base Finder config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            System.err.println("Invalid integer value for " + key + ", using default: " + defaultValue);
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
            System.err.println("Invalid double value for " + key + ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Validates the current configuration and fixes any invalid values
     */
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

        if (flightAltitude < 50) {
            flightAltitude = 50;
            changed = true;
        } else if (flightAltitude > 400) {
            flightAltitude = 400;
            changed = true;
        }

        if (scanInterval < 1) {
            scanInterval = 1;
            changed = true;
        }

        if (maxVolumeThreshold < 100) {
            maxVolumeThreshold = 100;
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

        if (changed) {
            save();
        }
    }

    /**
     * Determines if a collection of blocks is likely a natural structure
     * This is a legacy method kept for compatibility
     */
    public static boolean isLikelyNaturalStructure(List<Block> blocks, double volume) {
        if (!filterNaturalStructures) {
            return false;
        }

        // Simple volume check for backwards compatibility
        return volume > maxVolumeThreshold;
    }
}
