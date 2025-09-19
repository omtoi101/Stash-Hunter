package com.baseminer.basefinder.commands;

package com.baseminer.basefinder.commands;

import com.baseminer.basefinder.utils.ElytraController;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BaseFinderCommand extends Command {
    public BaseFinderCommand() {
        super("basefinder", "Controls the base finding flight system.", "bf");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // Start scanning with coordinates and optional strip width
        builder.then(literal("start")
            .then(argument("x1", StringArgumentType.string())
                .then(argument("z1", StringArgumentType.string())
                    .then(argument("x2", StringArgumentType.string())
                        .then(argument("z2", StringArgumentType.string())
                            .executes(context -> {
                                int x1, z1, x2, z2;
                                try {
                                    x1 = parseCoordinate(StringArgumentType.getString(context, "x1"), 'x');
                                    z1 = parseCoordinate(StringArgumentType.getString(context, "z1"), 'z');
                                    x2 = parseCoordinate(StringArgumentType.getString(context, "x2"), 'x');
                                    z2 = parseCoordinate(StringArgumentType.getString(context, "z2"), 'z');
                                } catch (CommandSyntaxException e) {
                                    error(e.getRawMessage().getString());
                                    return 0;
                                }

                                return startScanning(x1, z1, x2, z2, 128); // Default strip width
                            })
                            .then(argument("stripWidth", IntegerArgumentType.integer(50, 500))
                                .executes(context -> {
                                    int x1, z1, x2, z2;
                                    try {
                                        x1 = parseCoordinate(StringArgumentType.getString(context, "x1"), 'x');
                                        z1 = parseCoordinate(StringArgumentType.getString(context, "z1"), 'z');
                                        x2 = parseCoordinate(StringArgumentType.getString(context, "x2"), 'x');
                                        z2 = parseCoordinate(StringArgumentType.getString(context, "z2"), 'z');
                                    } catch (CommandSyntaxException e) {
                                        error(e.getRawMessage().getString());
                                        return 0;
                                    }
                                    int stripWidth = IntegerArgumentType.getInteger(context, "stripWidth");

                                    return startScanning(x1, z1, x2, z2, stripWidth);
                                })
                            )
                        )
                    )
                )
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
                info("§7You can use §c~§7 to specify your current coordinates (e.g. ~ ~ ~1000 ~1000).");
                info("§7/basefinder stop §f- Stop the current scanning operation");
                info("§7/basefinder status §f- Show current status and progress");
                info("§7/basefinder help §f- Show this help message");
                info("");
                info("§eExample: §f/basefinder start 1000 1000 5000 5000 150");
                info("This will scan from (1000, 1000) to (5000, 5000) with 150 block strip width");
                return SINGLE_SUCCESS;
            })
        );

        // Default help when no arguments
        builder.executes(context -> {
            info("Use §7/basefinder help §ffor command usage.");
            return SINGLE_SUCCESS;
        });
    }

    private int parseCoordinate(String coord, char axis) throws CommandSyntaxException {
        if (mc.player == null) {
            throw new CommandSyntaxException(null, () -> "Player not in game.");
        }

        if (!coord.startsWith("~")) {
            try {
                return Integer.parseInt(coord);
            } catch (NumberFormatException e) {
                throw new CommandSyntaxException(null, () -> "Invalid coordinate: " + coord);
            }
        }

        double base = (axis == 'x') ? mc.player.getX() : mc.player.getZ();

        if (coord.length() == 1) {
            return (int) base;
        }

        try {
            int offset = Integer.parseInt(coord.substring(1));
            return (int) (base + offset);
        } catch (NumberFormatException e) {
            throw new CommandSyntaxException(null, () -> "Invalid offset: " + coord);
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
