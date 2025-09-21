package com.stashhunter.stashhunter.commands;

import com.stashhunter.stashhunter.modules.StashHunterModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ClearStashesCommand extends Command {
    public ClearStashesCommand() {
        super("clear-stashes", "Clears the list of reported stashes.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Modules.get().get(StashHunterModule.class).clearReportedStashes();
            com.stashhunter.stashhunter.utils.Logger.log("Cleared the list of reported stashes.");
            info("Cleared the list of reported stashes.");
            return SINGLE_SUCCESS;
        });
    }
}
