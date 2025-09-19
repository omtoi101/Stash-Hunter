package com.baseminer.basefinder.commands;

import com.baseminer.basefinder.utils.ElytraController;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class BaseFinderCommand extends Command {
    public BaseFinderCommand() {
        super("basefinder", "Controls the base finding flight system.", "bf");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // Start scanning with coordinates and optional strip width using a greedy string
        builder.then(literal("start")
            .then(argument("args", StringArgumentType.greedyString())
                .executes(context -> {
                    String[] parts = StringArgumentType.getString(context, "args").split(" ");

                    // 1. Validate the number of arguments provided
                    if (parts.length < 4 || parts.length > 5) {
                        error("Invalid arguments. Usage: start <x1> <z1> <x2> <z2> [stripWidth]");
                        return SINGLE_SUCCESS;
                    }

                    try {
                        // 2. Parse coordinates from the parts
                        int x1 = parseCoordinate(parts[0], true);
                        int z1 = parseCoordinate(parts[1], false);
                        int x2 = parseCoordinate(parts[2], true);
                        int z2 = parseCoordinate(parts[3], false);

                        // 3. Parse optional stripWidth, using default if not present
                        int stripWidth = 128; // Default value
                        if (parts.length == 5) {
                            stripWidth = Integer.parseInt(parts[4]);
                            // You can add validation for the stripWidth range here if you want
                            if (stripWidth < 50 || stripWidth > 500) {
                                error("Strip width must be between 50 and 500.");
                                return SINGLE_SUCCESS;
                            }
                        }

                        return startScanning(x1, z1, x2, z2, stripWidth);

                    } catch (NumberFormatException e) {
                        error("Invalid coordinate or stripWidth format. Use numbers, ~, or ~<offset>.");
                        return SINGLE_SUCCESS;
                    }
                })
            )
        );

        // Stop scanning
        builder.then(literal("stop")
            .executes(context -> {
                if (ElytraController.isActive()) {
                    ElytraController.stop();
                    info("Base finder flight stopped.");
                } else {
                    error("Base finder is not currently active.");
                }
                return SINGLE_SUCCESS;
            })
        );

        // Status command
        builder.then(literal("status")
            .executes(context -> {
                String status = ElytraController.getStatus();
                info("Base Finder Status: " + status);

                if (ElytraController.isActive()) {
                    info("Current waypoint: " + ElytraController.getCurrentWaypoint() +
                        "/" + ElytraController.getTotalWaypoints());

                    if (ElytraController.getCurrentTarget() != null) {
                        var target = ElytraController.getCurrentTarget();
                        info("Current target: " + (int)target.x + ", " + (int)target.z);
                    }
                }
                return SINGLE_SUCCESS;
            })
        );

        // Help command
        builder.then(literal("help")
            .executes(context -> {
                info("Base Finder Commands:");
                info("§7/basefinder start <x1> <z1> <x2> <z2> [stripWidth] §f- Start scanning an area");
                info("§7/basefinder stop §f- Stop the current scanning operation");
                info("§7/basefinder status §f- Show current status and progress");
                info("§7/basefinder help §f- Show this help message");
                info("");
                info("§eCoordinate Examples:");
                info("§f/basefinder start 1000 1000 5000 5000 150 §7- Absolute coordinates");
                info("§f/basefinder start ~ ~ ~4000 ~4000 150 §7- Relative to current position");
                info("§f/basefinder start ~-2000 ~-2000 ~2000 ~2000 120 §7- Relative with offsets");
                info("");
                info("§7Use §f~§7 for relative coordinates (current position)");
                info("§7Use §f~<number>§7 for relative coordinates with offset");
                return SINGLE_SUCCESS;
            })
        );

        // Default help when no arguments
        builder.executes(context -> {
            info("Use §7/basefinder help §ffor command usage.");
            return SINGLE_SUCCESS;
        });
    }
    /**
     * Parses a coordinate string that can be either a number or relative (~)
     * @param coordStr The coordinate string (e.g., "100", "~", "~50", "~-25")
     * @param isX Whether this is an X coordinate (true) or Z coordinate (false)
     * @return The parsed absolute coordinate
     */
    private int parseCoordinate(String coordStr, boolean isX) throws NumberFormatException {
        if (MeteorClient.mc.player == null) {
            throw new NumberFormatException("Player is null");
        }

        coordStr = coordStr.trim();

        if (coordStr.equals("~")) {
            // Pure relative - use current position
            return isX ? (int) MeteorClient.mc.player.getX() : (int) MeteorClient.mc.player.getZ();
        } else if (coordStr.startsWith("~")) {
            // Relative with offset (e.g., ~100, ~-50)
            String offsetStr = coordStr.substring(1);
            int offset = Integer.parseInt(offsetStr);
            int currentCoord = isX ? (int) MeteorClient.mc.player.getX() : (int) MeteorClient.mc.player.getZ();
            return currentCoord + offset;
        } else {
            // Absolute coordinate
            return Integer.parseInt(coordStr);
        }
    }

    private int startScanning(int x1, int z1, int x2, int z2, int stripWidth) {
        if (ElytraController.isActive()) {
            error("Base finder is already active! Use '/basefinder stop' first.");
            return SINGLE_SUCCESS;
        }

        // Validate coordinates
        if (Math.abs(x2 - x1) < 100 || Math.abs(z2 - z1) < 100) {
            error("Scan area too small! Minimum area should be 100x100 blocks.");
            return SINGLE_SUCCESS;
        }

        if (Math.abs(x2 - x1) > 50000 || Math.abs(z2 - z1) > 50000) {
            error("Scan area too large! Maximum area should be 50000x50000 blocks.");
            return SINGLE_SUCCESS;
        }

        // Calculate area and estimated time
        long area = Math.abs((long)(x2 - x1) * (z2 - z1));
        long estimatedWaypoints = (Math.abs(x2 - x1) / stripWidth) * 2;

        info("Starting base finding process:");
        info("§7Area: §f" + area + " blocks²");
        info("§7From: §f(" + x1 + ", " + z1 + ") §7to §f(" + x2 + ", " + z2 + ")");
        info("§7Strip width: §f" + stripWidth + " blocks");
        info("§7Estimated waypoints: §f" + estimatedWaypoints);
        info("§7Make sure you have an Elytra equipped and are at flight altitude!");

        ElytraController.start(x1, z1, x2, z2, stripWidth);
        return SINGLE_SUCCESS;
    }
}
