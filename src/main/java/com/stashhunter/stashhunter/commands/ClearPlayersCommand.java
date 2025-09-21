package com.stashhunter.stashhunter.commands;

import com.stashhunter.stashhunter.modules.StashHunterModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ClearPlayersCommand extends Command {
    public ClearPlayersCommand() {
        super("clear-players", "Clears the list of reported players.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Modules.get().get(StashHunterModule.class).clearReportedPlayers();
            com.stashhunter.stashhunter.utils.Logger.log("Cleared the list of reported players.");
            info("Cleared the list of reported players.");
            return SINGLE_SUCCESS;
        });
    }
}
