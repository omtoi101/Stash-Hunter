package com.stashhunter.stashhunter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.modules.ChestIndexer;
import com.stashhunter.stashhunter.utils.ChestIndex;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.ServerKey;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.misc.text.RunnableClickEvent;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.stream.Collectors;

public class ChestIndexCommand extends Command {
    private static final int MAX_HITS = 10;
    // Marks the waypoints this command adds, so a second click on the same result doesn't add another.
    private static final String WAYPOINT_SUFFIX = " (chest-index)";

    public ChestIndexCommand() {
        super("chest-index", "Searches the containers recorded by the chest-index module.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            ChestIndex index = index();
            if (index == null) return SINGLE_SUCCESS;
            info("%d containers indexed on this server (%s). Use .chest-index search <item> or .chest-index shulker <name>.",
                index.size(), index.file().getFileName());
            return SINGLE_SUCCESS;
        });

        builder.then(literal("search")
            .then(argument("item", StringArgumentType.greedyString())
                .executes(context -> {
                    ChestIndex index = index();
                    if (index == null) return SINGLE_SUCCESS;
                    String query = StringArgumentType.getString(context, "item");
                    List<ChestIndex.Hit> hits = index.search(query);
                    if (hits.isEmpty()) {
                        info("No indexed container holds \"%s\".", query);
                        return SINGLE_SUCCESS;
                    }
                    highlight(hits.stream().map(ChestIndex.Hit::entry).toList());
                    int total = hits.stream().mapToInt(ChestIndex.Hit::count).sum();
                    info("\"%s\": %d items in %d containers%s:", query, total, hits.size(),
                        hits.size() > MAX_HITS ? " (top " + MAX_HITS + ")" : "");
                    String server = ServerKey.current();
                    for (ChestIndex.Hit hit : hits.subList(0, Math.min(MAX_HITS, hits.size()))) {
                        info(hitLine(hit.count() + " at ", hit.entry(), hit.count() + "x " + query.trim(), server));
                    }
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("shulker")
            .then(argument("name", StringArgumentType.greedyString())
                .executes(context -> {
                    ChestIndex index = index();
                    if (index == null) return SINGLE_SUCCESS;
                    String query = StringArgumentType.getString(context, "name");
                    List<ChestIndex.NameHit> hits = index.searchShulkerNames(query);
                    if (hits.isEmpty()) {
                        info("No indexed container holds a shulker box named like \"%s\".", query);
                        return SINGLE_SUCCESS;
                    }
                    highlight(hits.stream().map(ChestIndex.NameHit::entry).toList());
                    int total = hits.stream().mapToInt(hit -> hit.shulkers().size()).sum();
                    info("Shulker boxes named \"%s\": %d in %d containers%s:", query, total, hits.size(),
                        hits.size() > MAX_HITS ? " (top " + MAX_HITS + ")" : "");
                    String server = ServerKey.current();
                    for (ChestIndex.NameHit hit : hits.subList(0, Math.min(MAX_HITS, hits.size()))) {
                        String names = hit.shulkers().stream().map(s -> "\"" + s.name() + "\"").collect(Collectors.joining(", "));
                        String label = hit.shulkers().getFirst().name() + (hit.shulkers().size() > 1 ? " +" + (hit.shulkers().size() - 1) : "");
                        info(hitLine(names + " at ", hit.entry(), label, server));
                    }
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("clear").executes(context -> {
            ChestIndex index = index();
            if (index == null) return SINGLE_SUCCESS;
            index.clear();
            Modules.get().get(ChestIndexer.class).resetSession();
            Modules.get().get(ChestIndexer.class).highlight(List.of());
            info("Cleared the chest index of this server.");
            return SINGLE_SUCCESS;
        }));
    }

    private ChestIndex index() {
        ChestIndex index = Modules.get().get(ChestIndexer.class).index();
        if (index == null) error("Join a world first.");
        return index;
    }

    private static void highlight(List<ChestIndex.Entry> entries) {
        Modules.get().get(ChestIndexer.class).highlight(entries);
    }

    /**
     * "<before>[x, y, z] (block, dimension) [+waypoint]". Clicking the coordinates sends Baritone
     * next to the container if it is installed and the container is in this dimension; otherwise it
     * copies them. "[+waypoint]" adds a Meteor waypoint named after {@code label}.
     */
    private Component hitLine(String before, ChestIndex.Entry entry, String label, String server) {
        ChestIndex.Pos pos = entry.pos();
        BlockPos target = new BlockPos(pos.x(), pos.y(), pos.z());
        String coords = pos.x() + " " + pos.y() + " " + pos.z();
        boolean here = entry.dimension().equals(ChestIndexer.currentDimension());
        boolean canWalk = here && BaritoneBridge.isModLoaded();

        ClickEvent click;
        Component hover;
        if (canWalk) {
            click = new RunnableClickEvent(() -> walkTo(target, entry.dimension(), server));
            boolean opens = Modules.get().get(ChestIndexer.class).opensAfterWalk();
            hover = Component.literal("Click: Baritone walks next to this container (#goto " + coords + ")"
                + (opens ? " and opens it" : ""));
        } else {
            click = new ClickEvent.CopyToClipboard(coords);
            hover = Component.literal(here ? "Click: copy the coordinates (Baritone isn't installed)"
                : "In " + entry.dimension() + " - click: copy the coordinates");
        }
        MutableComponent line = Component.literal(before)
            .append(link("[" + pos.x() + ", " + pos.y() + ", " + pos.z() + "]", canWalk ? ChatFormatting.GREEN : ChatFormatting.AQUA, click, hover))
            .append(Component.literal(" (" + entry.block() + ", " + entry.dimension() + ")"));

        Dimension dimension = meteorDimension(entry.dimension());
        if (dimension != null) {
            String name = label + WAYPOINT_SUFFIX;
            line.append(Component.literal(" "))
                .append(link("[+waypoint]", ChatFormatting.GOLD,
                    new RunnableClickEvent(() -> addWaypoint(name, target, dimension, server)),
                    Component.literal("Click: add the Meteor waypoint \"" + name + "\"")));
        }
        return line;
    }

    private static MutableComponent link(String text, ChatFormatting color, ClickEvent click, Component hover) {
        return Component.literal(text).withStyle(style -> style.applyFormat(color)
            .withUnderlined(true)
            .withClickEvent(click)
            .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    // The links run when clicked, maybe long after the search and on another server.
    private boolean sameServer(String server) {
        if (MeteorClient.mc.level == null) {
            error("Join a world first.");
            return false;
        }
        if (!server.equals(ServerKey.current())) {
            error("That search result is from another server.");
            return false;
        }
        return true;
    }

    private void walkTo(BlockPos target, String dimension, String server) {
        if (!sameServer(server)) return;
        if (!dimension.equals(ChestIndexer.currentDimension())) {
            error("That container is in %s.", dimension);
        } else if (ElytraController.isActive()) {
            error("Stop the stash hunter flight first (.stashhunter stop).");
        } else if (Modules.get().get(ChestIndexer.class).walkTo(target)) {
            info("Baritone is walking to %s.", target.toShortString());
        } else {
            error("Baritone isn't available.");
        }
    }

    private void addWaypoint(String name, BlockPos pos, Dimension dimension, String server) {
        if (!sameServer(server)) return;
        for (Waypoint waypoint : Waypoints.get()) {
            if (waypoint.dimension.get() == dimension && waypoint.pos.get().equals(pos) && waypoint.name.get().endsWith(WAYPOINT_SUFFIX)) {
                info("There already is a waypoint for this container: \"%s\".", waypoint.name.get());
                return;
            }
        }
        Waypoints.get().add(new Waypoint.Builder().name(name).pos(pos).dimension(dimension).build());
        info("Added waypoint \"%s\" at %s.", name, pos.toShortString());
    }

    // Meteor waypoints know only the three vanilla dimensions; Dimension.toString() is their id.
    private static Dimension meteorDimension(String id) {
        for (Dimension dimension : Dimension.values()) {
            if (dimension.toString().equals(id)) return dimension;
        }
        return null;
    }
}
