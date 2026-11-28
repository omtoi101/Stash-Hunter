package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.signals.Signal;
import com.stashhunter.stashhunter.signals.SignalBlocks;
import com.stashhunter.stashhunter.signals.SignalCategory;
import com.stashhunter.stashhunter.signals.SignalKind;
import com.stashhunter.stashhunter.signals.SignalSummary;
import com.stashhunter.stashhunter.signals.Tier;
import com.stashhunter.stashhunter.signals.Verdict;
import com.stashhunter.stashhunter.signals.WaypointNames;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.DimensionIds;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.StashClustering;
import com.stashhunter.stashhunter.utils.StashGrowth;
import com.stashhunter.stashhunter.xaero.XaeroBridge;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports hints and stashes (chat, Discord, Meteor/Xaero waypoints) and keeps the ones reported this
 * session for report corrections and the Stash ESP. Split out of StashHunterModule; client thread only.
 */
final class StashReporter {
    /** Settings a report depends on, read by the module when it reports. */
    record Options(boolean saveToXaero, double notificationDensityThreshold, boolean discordHints, int dedupeRadius) {
    }

    /** Stash ESP settings. */
    record RenderOptions(int maxDistance, ShapeMode mode, SettingColor stashSides, SettingColor stashLines,
                         SettingColor hintSides, SettingColor hintLines, boolean boundingBox, boolean tracers) {
    }

    /**
     * A hint or stash reported this session: the reported position, all positions of its cluster (to
     * recognise it when more of it is found), the count it was reported with, its waypoint (null if
     * none was created), its XaeroBridge waypoint handle (null if none), its Stash ESP highlight (null
     * without positions to show) and its tier. Not persisted.
     */
    record FoundStash(Dimension dimension, BlockPos position, Set<BlockPos> blocks, int reportedBlocks,
                      Waypoint waypoint, Object xaeroWaypoint, StashHighlight highlight, Tier tier) {
    }

    /**
     * Positions the Stash ESP outlines. With {@code containersOnly}, a position whose block is no
     * longer a container is skipped; hints without containers show their signal positions as they are.
     */
    record StashHighlight(Dimension dimension, List<BlockPos> positions, boolean containersOnly, BlockPos min, BlockPos max) {
        StashHighlight(Dimension dimension, List<BlockPos> positions, boolean containersOnly) {
            this(dimension, List.copyOf(positions), containersOnly, corner(positions, true), corner(positions, false));
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

    // A set, not Config.storageBlocks (a list): render asks for every highlighted container each frame.
    private static final Set<Block> CONTAINERS = containers();

    private static Set<Block> containers() {
        Set<Block> containers = new HashSet<>(Config.storageBlocks);
        containers.addAll(List.of(
            Blocks.HOPPER, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
            Blocks.DISPENSER, Blocks.DROPPER, Blocks.ENDER_CHEST
        ));
        Blocks.COPPER_CHEST.forEach(containers::add); // CHEST signals in normal/sensitive
        return Set.copyOf(containers);
    }

    private static final int HINT_SUMMARY_KINDS = 3;

    private final Consumer<String> chat;
    private final List<FoundStash> found = new ArrayList<>();

    StashReporter(Consumer<String> chat) {
        this.chat = chat;
    }

    void clear() {
        found.clear();
    }

    /** Reports of this session in {@code dimension} that share a position with {@code positions}. */
    List<FoundStash> earlierReportsOf(Dimension dimension, Collection<BlockPos> positions) {
        List<Set<BlockPos>> known = new ArrayList<>();
        for (FoundStash stash : found) known.add(stash.dimension() == dimension ? stash.blocks() : Set.of());
        List<FoundStash> result = new ArrayList<>();
        for (int i : StashGrowth.overlapping(known, positions)) result.add(found.get(i));
        return result;
    }

    /** Highest tier among {@code earlier} and the hint/stash waypoints within {@code radius} of pos. */
    static Tier knownTier(Dimension dimension, BlockPos pos, int radius, List<FoundStash> earlier) {
        Tier highest = Tier.NONE;
        for (FoundStash stash : earlier) highest = max(highest, stash.tier());
        // Survives module toggles and reconnects (Meteor persists waypoints per server).
        for (Waypoint waypoint : Waypoints.get()) {
            if (waypoint.dimension.get() == dimension && waypoint.pos.get().closerThan(pos, radius)) {
                highest = max(highest, WaypointNames.tierOf(waypoint.name.get()));
            }
        }
        return highest;
    }

    static Tier max(Tier a, Tier b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    // ----- hints -----

    void reportHint(Dimension dimension, BlockPos center, List<Signal> signals, int score, Options options) {
        String coords = center.toShortString();
        String summary = SignalSummary.top(signals, HINT_SUMMARY_KINDS);
        String dimensionId = DimensionIds.idOf(dimension);
        chat.accept("Hint: " + summary + " at " + coords + " (" + dimensionId + ")");

        String name = WaypointNames.hint(summary, coords);
        Waypoint waypoint = new Waypoint.Builder().name(name).pos(center).dimension(dimension).build();
        Waypoints.get().add(waypoint);
        Object xaero = options.saveToXaero() ? XaeroBridge.addWaypoint(center, name, true) : null;

        if (options.discordHints()) {
            String description = "Coordinates: " + coords + "\n"
                + "Dimension: " + dimensionId + "\n"
                + "Score: " + score + "\n"
                + "Signals: " + SignalSummary.top(signals, Integer.MAX_VALUE);
            DiscordWebhook.sendMessage("", new DiscordEmbed("Stash Hint", description, 0xFFFF00));
        }

        Set<BlockPos> positions = positionsOf(signals);
        found.add(new FoundStash(dimension, center, positions, positions.size(), waypoint, xaero,
            highlightOf(dimension, signals), Tier.HINT));
    }

    // ----- stashes (normal / sensitive) -----

    /**
     * Reports a stash found by the signal score, or corrects {@code earlier} (same stash seen before,
     * or the hint it grew out of). {@code update} is true for every correction, also for a hint
     * reported in an earlier session that has no FoundStash.
     */
    void reportStash(Dimension dimension, BlockPos center, List<Signal> signals, Verdict verdict,
                     List<FoundStash> earlier, boolean update, String sensitivityName, Options options) {
        String previously = previously(earlier, update);
        replacePrevious(dimension, center, earlier, options.dedupeRadius());

        Set<BlockPos> positions = positionsOf(signals);
        List<BlockPos> storage = storagePositions(signals);
        // Density over the containers like 26.2.2; a stash by score alone may have none.
        List<BlockPos> measured = storage.isEmpty() ? new ArrayList<>(positions) : storage;
        double volume = StashClustering.boundingBoxVolume(measured);
        double density = measured.size() / Math.max(volume, 1.0);

        String coords = center.toShortString();
        String traces = SignalSummary.traces(signals);
        chat.accept(String.format(Locale.ROOT, "Stash %s at: %s (%s) with %d signals, score %d (container density: %.6f)%s",
            update ? "Updated" : "Found", coords, dimension, positions.size(), verdict.score(), density,
            update ? " - previously reported: " + previously : ""));

        boolean announcedBefore = earlier.stream().anyMatch(old -> old.tier() == Tier.STASH && old.waypoint() != null);
        Waypoint waypoint = null;
        // The density threshold only guards stashes found by the container count.
        if (!verdict.byContainers() || density >= options.notificationDensityThreshold() || announcedBefore) {
            String description = String.format(Locale.ROOT,
                (update ? "More of an already reported stash or hint was found.\nPreviously reported: " + previously + "\n\n" : "")
                    + "Coordinates: %s (sensitivity %s)\n"
                    + "Dimension: %s\n"
                    + "Found %d signals, score %d\n"
                    + "Volume: %.2f blocks\n"
                    + "Density: %.6f\n"
                    + "Rating: %s\n\n"
                    + "Container List:\n%s\n"
                    + "Traces: %s",
                coords, sensitivityName, dimension, positions.size(), verdict.score(), volume, density,
                ratingFromDensity(density), containerList(signals, true), traces.isEmpty() ? "none" : traces);
            // No second @everyone for a correction
            DiscordWebhook.sendMessage(update ? "" : "@everyone",
                new DiscordEmbed(update ? "Stash Updated" : "Stash Found!", description, update ? 0x33CC99 : 0x00FF00));

            String name = WaypointNames.stash(positions.size(), coords);
            waypoint = new Waypoint.Builder().name(name).pos(center).dimension(dimension).build();
            Waypoints.get().add(waypoint);
            chat.accept((update ? "Moved waypoint to: " : "Created waypoint: ") + name);
        } else {
            chat.accept("Skipped Discord notification for low density stash (density: " + String.format(Locale.ROOT, "%.6f", density)
                + ", threshold: " + String.format(Locale.ROOT, "%.6f", options.notificationDensityThreshold()) + ")");
        }

        Object xaero = waypoint != null && options.saveToXaero() ? XaeroBridge.addWaypoint(center, waypoint.name.get()) : null;
        found.add(new FoundStash(dimension, center, positions, positions.size(), waypoint, xaero,
            highlightOf(dimension, signals), Tier.STASH));
    }

    // ----- stashes (classic: the 26.2.2 texts) -----

    /**
     * The 26.2.2 report: {@code cluster} holds every stash block, {@code types} their kinds. Returns
     * nothing; the FoundStash is recorded here.
     */
    void reportStashClassic(Dimension dimension, BlockPos stashPos, List<BlockPos> cluster, Map<BlockPos, SignalKind> types,
                            List<FoundStash> earlier, boolean update, boolean storageOnlyMode, Options options) {
        String previously = previously(earlier, update);
        replacePrevious(dimension, stashPos, earlier, options.dedupeRadius());

        double volume = StashClustering.boundingBoxVolume(cluster);
        double density = cluster.size() / Math.max(volume, 1.0);
        String coords = stashPos.toShortString();
        String rating = ratingFromDensity(density);

        Map<String, Integer> counts = new HashMap<>();
        for (BlockPos pos : cluster) {
            SignalKind kind = types.get(pos);
            if (kind != null) counts.merge(blockName(pos, kind), 1, Integer::sum);
        }
        StringBuilder containerList = new StringBuilder();
        counts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .forEach(entry -> containerList.append(entry.getValue()).append("x ").append(entry.getKey()).append("\n"));

        String modeInfo = storageOnlyMode ? " (Storage Only)" : " (Full Stash)";
        String logMessage = String.format("Stash %s at: %s (%s) with %d valuable blocks (density: %.6f)%s",
            update ? "Updated" : "Found", coords, dimension, cluster.size(), density,
            update ? " - previously reported: " + previously : "");
        chat.accept(logMessage);

        // Only send Discord notification if density meets threshold. A stash already announced
        // on Discord gets its correction there too, even if the bigger cluster is less dense.
        boolean announcedBefore = earlier.stream().anyMatch(old -> old.waypoint() != null);
        Waypoint waypoint = null;
        if (density >= options.notificationDensityThreshold() || announcedBefore) {
            String description = String.format(
                (update ? "More of an already reported stash was found.\nPreviously reported: " + previously + "\n\n" : "") +
                    "Coordinates: %s%s\n" +
                    "Dimension: %s\n" +
                    "Found %d valuable blocks\n" +
                    "Volume: %.2f blocks\n" +
                    "Density: %.6f\n" +
                    "Rating: %s\n\n" +
                    "Container List:\n%s",
                coords, modeInfo, dimension, cluster.size(), volume, density, rating, containerList.toString()
            );
            String title = update ? "Stash Updated" : "Stash Found!";
            DiscordWebhook.sendMessage(update ? "" : "@everyone", new DiscordEmbed(title, description, update ? 0x33CC99 : 0x00FF00));

            String waypointName = WaypointNames.stash(cluster.size(), coords);
            waypoint = new Waypoint.Builder().name(waypointName).pos(stashPos).dimension(dimension).build();
            Waypoints.get().add(waypoint);
            chat.accept((update ? "Moved waypoint to: " : "Created waypoint: ") + waypointName);
        } else {
            chat.accept("Skipped Discord notification for low density stash (density: " + String.format("%.6f", density) +
                ", threshold: " + String.format("%.6f", options.notificationDensityThreshold()) + ")");
        }

        Object xaero = waypoint != null && options.saveToXaero() ? XaeroBridge.addWaypoint(stashPos, waypoint.name.get()) : null;
        List<BlockPos> containers = new ArrayList<>();
        for (BlockPos pos : cluster) {
            if (isStorage(types.get(pos))) containers.add(pos.immutable());
        }
        found.add(new FoundStash(dimension, stashPos, Set.copyOf(cluster), cluster.size(), waypoint, xaero,
            containers.isEmpty() ? null : new StashHighlight(dimension, containers, true), Tier.STASH));
    }

    // ----- shared -----

    // "N blocks at x, y, z" per earlier report, like 26.2.2; a hint from an earlier session has no entry.
    private static String previously(List<FoundStash> earlier, boolean update) {
        StringBuilder previously = new StringBuilder();
        for (FoundStash old : earlier) {
            if (!previously.isEmpty()) previously.append(", ");
            previously.append(old.tier() == Tier.HINT ? "hint with " + old.reportedBlocks() + " signals" : old.reportedBlocks() + " blocks")
                .append(" at ").append(old.position().toShortString());
        }
        if (previously.isEmpty() && update) previously.append("a hint nearby");
        return previously.toString();
    }

    /**
     * Drops what a stash report replaces: the earlier reports of the same stash, and every hint near
     * it (FoundStash of this session and hint waypoints from any session). Stash waypoints of other
     * stashes stay.
     */
    private void replacePrevious(Dimension dimension, BlockPos center, List<FoundStash> earlier, int radius) {
        List<FoundStash> drop = new ArrayList<>(earlier);
        for (FoundStash stash : found) {
            if (stash.tier() == Tier.HINT && stash.dimension() == dimension && stash.position().closerThan(center, radius)
                && !drop.contains(stash)) {
                drop.add(stash);
            }
        }
        for (FoundStash old : drop) {
            found.remove(old);
            if (old.waypoint() != null) Waypoints.get().remove(old.waypoint());
            XaeroBridge.removeWaypoint(old.xaeroWaypoint());
        }
        List<Waypoint> hintWaypoints = new ArrayList<>();
        for (Waypoint waypoint : Waypoints.get()) {
            if (waypoint.dimension.get() == dimension && WaypointNames.tierOf(waypoint.name.get()) == Tier.HINT
                && waypoint.pos.get().closerThan(center, radius)) {
                hintWaypoints.add(waypoint);
            }
        }
        for (Waypoint waypoint : hintWaypoints) Waypoints.get().remove(waypoint);
    }

    private static Set<BlockPos> positionsOf(List<Signal> signals) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (Signal s : signals) positions.add(s.pos().immutable());
        return Set.copyOf(positions);
    }

    private static boolean isStorage(SignalKind kind) {
        return kind != null && (kind.category() == SignalCategory.STRONG || kind.category() == SignalCategory.CONTAINER);
    }

    private static List<BlockPos> storagePositions(List<Signal> signals) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (Signal s : signals) {
            if (isStorage(s.kind())) positions.add(s.pos().immutable());
        }
        return new ArrayList<>(positions);
    }

    // Containers if there are any, otherwise every signal position (signs, beds, entities, ...).
    private static StashHighlight highlightOf(Dimension dimension, List<Signal> signals) {
        List<BlockPos> storage = storagePositions(signals);
        if (!storage.isEmpty()) return new StashHighlight(dimension, storage, true);
        List<BlockPos> all = new ArrayList<>(positionsOf(signals));
        return all.isEmpty() ? null : new StashHighlight(dimension, all, false);
    }

    private static String containerList(List<Signal> signals, boolean storageOnly) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Signal s : signals) {
            if (!storageOnly || isStorage(s.kind())) counts.merge(blockName(s.pos(), s.kind()), 1, Integer::sum);
        }
        if (counts.isEmpty()) return "none\n";
        StringBuilder list = new StringBuilder();
        counts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .forEach(entry -> list.append(entry.getValue()).append("x ").append(entry.getKey()).append("\n"));
        return list.toString();
    }

    /**
     * The block's own name ("White Shulker Box") while its chunk is loaded and still holds a block of
     * that kind; otherwise the kind's label. Members of a cluster may lie in chunks no longer loaded.
     */
    private static String blockName(BlockPos pos, SignalKind kind) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.isLoaded(pos)) {
            Block block = level.getBlockState(pos).getBlock();
            if (SignalBlocks.kindOf(block) == kind || (kind == SignalKind.SPAWNER && block == Blocks.SPAWNER)) {
                return block.getName().getString();
            }
        }
        String label = kind.label();
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private static String ratingFromDensity(double density) {
        if (density > 0.1) return "Very High Density";
        if (density > 0.01) return "High Density";
        if (density > 0.005) return "Medium Density";
        if (density > 0.001) return "Low Density";
        return "Very Low Density";
    }

    // ----- Stash ESP -----

    void render(Render3DEvent event, Dimension dimension, RenderOptions options) {
        if (found.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || event.renderer == null) return;

        double maxDistanceSq = (double) options.maxDistance() * options.maxDistance();
        StashHighlight nearest = null;
        SettingColor nearestLines = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (FoundStash stash : found) {
            StashHighlight highlight = stash.highlight();
            if (highlight == null || highlight.dimension() != dimension) continue;
            double distSq = player.distanceToSqr(highlight.centerX(), highlight.centerY(), highlight.centerZ());
            if (distSq > maxDistanceSq) continue;

            boolean hint = stash.tier() == Tier.HINT;
            SettingColor sides = hint ? options.hintSides() : options.stashSides();
            SettingColor lines = hint ? options.hintLines() : options.stashLines();
            for (BlockPos p : highlight.positions()) {
                // Skip containers that were broken since; unloaded chunks read as air, so keep those.
                if (highlight.containersOnly() && level.isLoaded(p) && !CONTAINERS.contains(level.getBlockState(p).getBlock())) continue;
                event.renderer.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, sides, lines, options.mode(), 0);
            }
            if (options.boundingBox()) {
                event.renderer.box(highlight.min().getX(), highlight.min().getY(), highlight.min().getZ(),
                    highlight.max().getX() + 1, highlight.max().getY() + 1, highlight.max().getZ() + 1, sides, lines, ShapeMode.Lines, 0);
            }
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = highlight;
                nearestLines = lines;
            }
        }

        if (options.tracers() && nearest != null) {
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                nearest.centerX(), nearest.centerY(), nearest.centerZ(), nearestLines);
        }
    }
}
