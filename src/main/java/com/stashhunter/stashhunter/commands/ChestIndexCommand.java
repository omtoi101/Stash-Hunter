package com.stashhunter.stashhunter.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stashhunter.stashhunter.modules.ChestIndexer;
import com.stashhunter.stashhunter.utils.ChestIndex;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.util.List;

public class ChestIndexCommand extends Command {
    private static final int MAX_HITS = 10;

    public ChestIndexCommand() {
        super("chest-index", "Searches the containers recorded by the chest-index module.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            ChestIndex index = index();
            if (index == null) return SINGLE_SUCCESS;
            info("%d containers indexed on this server (%s). Use .chest-index search <item>.", index.size(), index.file().getFileName());
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
                    int total = hits.stream().mapToInt(ChestIndex.Hit::count).sum();
                    info("\"%s\": %d items in %d containers%s:", query, total, hits.size(),
                        hits.size() > MAX_HITS ? " (top " + MAX_HITS + ")" : "");
                    for (ChestIndex.Hit hit : hits.subList(0, Math.min(MAX_HITS, hits.size()))) {
                        ChestIndex.Entry entry = hit.entry();
                        info("%d at %d, %d, %d (%s, %s)", hit.count(), entry.pos().x(), entry.pos().y(), entry.pos().z(),
                            entry.block(), entry.dimension());
                    }
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("clear").executes(context -> {
            ChestIndex index = index();
            if (index == null) return SINGLE_SUCCESS;
            index.clear();
            Modules.get().get(ChestIndexer.class).resetSession();
            info("Cleared the chest index of this server.");
            return SINGLE_SUCCESS;
        }));
    }

    private ChestIndex index() {
        ChestIndex index = Modules.get().get(ChestIndexer.class).index();
        if (index == null) error("Join a world first.");
        return index;
    }
}
