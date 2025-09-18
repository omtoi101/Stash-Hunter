package com.baseminer.basefinder.commands;

import com.baseminer.basefinder.modules.BaseFinderModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ClearBasesCommand extends Command {
    public ClearBasesCommand() {
        super("clear-bases", "Clears the list of reported bases.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Modules.get().get(BaseFinderModule.class).clearReportedBases();
            com.baseminer.basefinder.utils.Logger.log("Cleared the list of reported bases.");
            info("Cleared the list of reported bases.");
            return SINGLE_SUCCESS;
        });
    }
}
