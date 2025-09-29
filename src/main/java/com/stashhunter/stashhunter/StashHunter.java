package com.stashhunter.stashhunter;

import com.stashhunter.stashhunter.commands.ClearPlayersCommand;
import com.stashhunter.stashhunter.commands.ClearStashesCommand;
import com.stashhunter.stashhunter.commands.StashHunterCommand;
import com.stashhunter.stashhunter.events.PlayerDisconnectEvent;
import com.stashhunter.stashhunter.hud.StashHunterHud;
import com.stashhunter.stashhunter.modules.*;
import com.stashhunter.stashhunter.utils.Config;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public class StashHunter extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Stash-Hunter");
    public static final HudGroup HUD_GROUP = new HudGroup("Stash-Hunter");


    @Override
    public void onInitialize() {
        LOG.info("Initializing Stash-Hunter");

        Config.load();

        // Modules
        Modules.get().add(new StashHunterModule());
        Modules.get().add(new StuckDetector());
        Modules.get().add(new AltitudeLossDetector());
        Modules.get().add(new NewerNewChunks());
        Modules.get().add(new AutoElytraRepair());

        boolean baritoneLoaded = checkModLoaded("baritone", "baritone-meteor");
        boolean xaeroWorldMapLoaded = checkModLoaded("xaeroworldmap");
        boolean xaeroPlusLoaded = checkModLoaded("xaeroplus");

        if (xaeroWorldMapLoaded && xaeroPlusLoaded) {
            if (baritoneLoaded) {
                Modules.get().add(new TrailFollower());
            }
        }

        // Commands
        Commands.add(new StashHunterCommand());
        Commands.add(new ClearStashesCommand());
        Commands.add(new ClearPlayersCommand());

        // HUD
        Hud.get().register(StashHunterHud.INFO);

        // Events
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MeteorClient.EVENT_BUS.post(PlayerDisconnectEvent.get());
        });
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.stashhunter.stashhunter";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("omtoi", "stash-hunter");
    }

    private boolean checkModLoaded(String... modIds) {
        for (String modId : modIds) {
            if (FabricLoader.getInstance().isModLoaded(modId)) {
                return true;
            }
        }
        return false;
    }
}