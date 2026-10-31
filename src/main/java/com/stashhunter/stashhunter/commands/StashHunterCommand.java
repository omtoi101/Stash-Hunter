package com.stashhunter.stashhunter.commands;

import com.stashhunter.stashhunter.modules.StashHunterModule;
import com.stashhunter.stashhunter.modules.AutoElytraRepair;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.GridWaypoints;
import com.stashhunter.stashhunter.utils.SpiralWaypoints;
import meteordevelopment.meteorclient.systems.modules.Modules;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class StashHunterCommand extends Command {
    public StashHunterCommand() {
        super("stashhunter", "Controls the stash finding flight system.", "sh");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
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

        // Spiral outward from a center: nearest ground first, for searching around spawn or a portal
        builder.then(literal("spiral")
            .then(argument("args", StringArgumentType.greedyString())
                .executes(context -> {
                    String[] parts = StringArgumentType.getString(context, "args").trim().split("\\s+");

                    // 1-2 args: centered on the player; 3-4 args: explicit center first
                    if (parts.length < 1 || parts.length > 4) {
                        error("Invalid arguments. Usage: spiral [<x> <z>] <radius> [gap]");
                        return SINGLE_SUCCESS;
                    }

                    try {
                        boolean explicitCenter = parts.length >= 3;
                        int centerX = parseCoordinate(explicitCenter ? parts[0] : "~", true);
                        int centerZ = parseCoordinate(explicitCenter ? parts[1] : "~", false);
                        int offset = explicitCenter ? 2 : 0;
                        int radius = Integer.parseInt(parts[offset]);

                        int gap = 128; // Same default as the rectangle's strip width
                        if (parts.length > offset + 1) {
                            gap = Integer.parseInt(parts[offset + 1]);
                            if (gap < 50 || gap > 500) {
                                error("Gap must be between 50 and 500.");
                                return SINGLE_SUCCESS;
                            }
                        }

                        return startSpiral(centerX, centerZ, radius, gap);

                    } catch (NumberFormatException e) {
                        error("Invalid coordinate, radius or gap format. Use numbers, ~, or ~<offset>.");
                        return SINGLE_SUCCESS;
                    }
                })
            )
        );

        // Stop scanning
        builder.then(literal("stop")
            .executes(context -> {
                // A post-completion Baritone landing runs with active=false; stop must still cancel it.
                if (ElytraController.isActive() || ElytraController.isLandingAfterCompletion()) {
                    ElytraController.stop();
                    info("Stash hunter flight stopped.");
                } else if (keepPausedAfterRepair()) {
                    info("Elytra repair in progress - it finishes, then the flight stays stopped.");
                } else {
                    error("Stash hunter is not currently active.");
                }
                return SINGLE_SUCCESS;
            })
        );

        // Pause scanning
        builder.then(literal("pause")
            .executes(context -> {
                if (ElytraController.isActive()) {
                    ElytraController.pause();
                    info("Stash hunter flight paused.");
                } else if (keepPausedAfterRepair()) {
                    info("Elytra repair in progress - the flight is saved and stays paused afterwards.");
                } else {
                    error("Stash hunter is not currently active.");
                }
                return SINGLE_SUCCESS;
            })
        );

        // Resume scanning
        builder.then(literal("resume")
            .executes(context -> {
                if (ElytraController.isActive()) {
                    error("Stash hunter is already active.");
                } else {
                    ensureModuleActive();
                    ElytraController.resume();
                    info("Stash hunter flight resumed.");
                }
                return SINGLE_SUCCESS;
            })
            .then(argument("timestamp", LongArgumentType.longArg())
                .executes(context -> {
                    if (ElytraController.isActive()) {
                        error("Stash hunter is already active.");
                    } else {
                        long timestamp = LongArgumentType.getLong(context, "timestamp");
                        ensureModuleActive();
                        ElytraController.resume(timestamp);
                        info("Stash hunter flight resumed.");
                    }
                    return SINGLE_SUCCESS;
                })
            )
        );

        // List trips
        builder.then(literal("list")
            .executes(context -> {
                info("Saved trips:");
                for (com.stashhunter.stashhunter.utils.TripManager.TripData trip : com.stashhunter.stashhunter.utils.TripManager.getTrips()) {
                    info("  " + trip.timestamp);
                }
                return SINGLE_SUCCESS;
            })
        );

        // Status command
        builder.then(literal("status")
            .executes(context -> {
                String status = ElytraController.getStatus();
                info("Stash-Hunter Status: " + status);

                if (ElytraController.isActive()) {
                    info("Current waypoint: " + (ElytraController.getCurrentWaypoint() + 1) +
                        "/" + ElytraController.getTotalWaypoints());

                    if (ElytraController.getCurrentTarget() != null) {
                        var target = ElytraController.getCurrentTarget();
                        info("Current target: " + (int)target.x + ", " + (int)target.z);
                    }
                }
                return SINGLE_SUCCESS;
            })
        );

        // Flight altitude command
        builder.then(literal("altitude")
            .executes(context -> {
                info("Flight altitude: §fY=" + Config.flightAltitude + " §7(100-400)");
                return SINGLE_SUCCESS;
            })
            .then(argument("y", IntegerArgumentType.integer(100, 400))
                .executes(context -> {
                    StashHunterModule module = Modules.get().get(StashHunterModule.class);
                    if (module == null) {
                        error("Stash-Hunter module not found.");
                        return SINGLE_SUCCESS;
                    }
                    int altitude = module.setFlightAltitude(IntegerArgumentType.getInteger(context, "y"));
                    info("Flight altitude set to §fY=" + altitude + (ElytraController.isActive() ? " §7- the current flight follows." : "."));
                    return SINGLE_SUCCESS;
                })
            )
        );

        // Help command
        builder.then(literal("help")
            .executes(context -> {
                info("Stash-Hunter Commands:");
                info("§7.stashhunter start <x1> <z1> <x2> <z2> [stripWidth] §f- Start scanning an area");
                info("§7.stashhunter spiral [<x> <z>] <radius> [gap] §f- Scan in a square spiral (centered on you without x/z)");
                info("§7.stashhunter stop §f- Stop the current scanning operation");
                info("§7.stashhunter pause §f- Pause the current scanning operation");
                info("§7.stashhunter resume [timestamp] §f- Resume the latest or a specific trip");
                info("§7.stashhunter list §f- List all saved trips");
                info("§7.stashhunter status §f- Show current status and progress");
                info("§7.stashhunter altitude [y] §f- Show or set the flight altitude (100-400, applies to the current flight)");
                info("§7.stashhunter repair §f- Show elytra repair status");
                info("§7.stashhunter repair toggle §f- Toggle auto elytra repair");
                info("§7.stashhunter help §f- Show this help message");
                info("");
                info("§eCoordinate Examples:");
                info("§f.stashhunter start 1000 1000 5000 5000 150 §7- Absolute coordinates");
                info("§f.stashhunter start ~ ~ ~4000 ~4000 150 §7- Relative to current position");
                info("§f.stashhunter start ~-2000 ~-2000 ~2000 ~2000 120 §7- Relative with offsets");
                info("§f.stashhunter spiral 3000 §7- Spiral out to 3000 blocks around you");
                info("");
                info("§7Use §f~§7 for relative coordinates (current position)");
                info("§7Use §f~<number>§7 for relative coordinates with offset");
                return SINGLE_SUCCESS;
            })
        );

        // Repair status command
        builder.then(literal("repair")
            .executes(context -> {
                AutoElytraRepair repairModule = Modules.get().get(AutoElytraRepair.class);
                if (repairModule == null) {
                    error("Auto Elytra Repair module not found.");
                    return SINGLE_SUCCESS;
                }

                if (!repairModule.isActive()) {
                    info("Auto Elytra Repair: §cDisabled");
                } else if (repairModule.isRepairing()) {
                    info("Auto Elytra Repair: §eActive - " + repairModule.getCurrentStateName());
                } else {
                    info("Auto Elytra Repair: §aEnabled - Monitoring");
                }

                // Show current elytra status
                if (MeteorClient.mc.player != null) {
                    net.minecraft.world.item.ItemStack chestSlot = MeteorClient.mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                    if (chestSlot.getItem() == net.minecraft.world.item.Items.ELYTRA) {
                        int durability = chestSlot.getMaxDamage() - chestSlot.getDamageValue();
                        int maxDurability = chestSlot.getMaxDamage();
                        info("Current Elytra: " + durability + "/" + maxDurability + " durability");
                    } else {
                        info("No Elytra currently equipped");
                    }
                }

                return SINGLE_SUCCESS;
            })
            .then(literal("toggle")
                .executes(context -> {
                    AutoElytraRepair repairModule = Modules.get().get(AutoElytraRepair.class);
                    if (repairModule == null) {
                        error("Auto Elytra Repair module not found.");
                        return SINGLE_SUCCESS;
                    }

                    repairModule.toggle();
                    info("Auto Elytra Repair: " + (repairModule.isActive() ? "§aEnabled" : "§cDisabled"));
                    return SINGLE_SUCCESS;
                })
            )
        );

        // Default help when no arguments
        builder.executes(context -> {
            info("Use §7.stashhunter help §ffor command usage.");
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

    // A running elytra repair has paused the flight itself; tell it not to resume afterwards.
    private static boolean keepPausedAfterRepair() {
        AutoElytraRepair repair = Modules.get().get(AutoElytraRepair.class);
        if (repair == null || !repair.isActive() || !repair.isRepairing()) return false;
        repair.keepFlightPaused();
        return true;
    }

    private int startScanning(int x1, int z1, int x2, int z2, int stripWidth) {
        if (ElytraController.isActive()) {
            error("Stash hunter is already active! Use '.stashhunter stop' first.");
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
        long estimatedWaypoints = GridWaypoints.count(x1, x2, stripWidth);

        info("Starting stash finding process:");
        info("§7Area: §f" + area + " blocks²");
        info("§7From: §f(" + x1 + ", " + z1 + ") §7to §f(" + x2 + ", " + z2 + ")");
        info("§7Strip width: §f" + stripWidth + " blocks");
        info("§7Estimated waypoints: §f" + estimatedWaypoints);
        info("§7Make sure you have an Elytra equipped and are at flight altitude!");

        ensureModuleActive();
        ElytraController.start(x1, z1, x2, z2, stripWidth);
        return SINGLE_SUCCESS;
    }

    private int startSpiral(int centerX, int centerZ, int radius, int gap) {
        if (ElytraController.isActive()) {
            error("Stash hunter is already active! Use '.stashhunter stop' first.");
            return SINGLE_SUCCESS;
        }

        // Same bounds as the rectangle mode: 50..25000 radius = 100..50000 blocks across.
        if (radius < 50) {
            error("Spiral radius too small! Minimum is 50 blocks.");
            return SINGLE_SUCCESS;
        }
        if (radius > 25000) {
            error("Spiral radius too large! Maximum is 25000 blocks.");
            return SINGLE_SUCCESS;
        }

        info("Starting spiral search:");
        info("§7Center: §f(" + centerX + ", " + centerZ + ")");
        info("§7Radius: §f" + radius + " blocks");
        info("§7Gap: §f" + gap + " blocks");
        info("§7Estimated waypoints: §f" + SpiralWaypoints.count(radius, gap));
        info("§7Make sure you have an Elytra equipped and are at flight altitude!");

        ensureModuleActive();
        ElytraController.startSpiral(centerX, centerZ, radius, gap);
        return SINGLE_SUCCESS;
    }

    // ElytraController is only ticked by StashHunterModule; without it a started flight never moves.
    private void ensureModuleActive() {
        StashHunterModule module = Modules.get().get(StashHunterModule.class);
        if (module != null && !module.isActive()) {
            module.toggle();
            info("Enabled the stash-hunter module, which drives the flight.");
        }
    }
}
