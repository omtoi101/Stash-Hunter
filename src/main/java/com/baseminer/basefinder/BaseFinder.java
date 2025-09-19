package com.baseminer.basefinder;

import com.baseminer.basefinder.utils.Config;
import com.baseminer.basefinder.commands.BaseFinderCommand;
import com.baseminer.basefinder.commands.ClearBasesCommand;
import com.baseminer.basefinder.commands.ClearPlayersCommand;
import com.baseminer.basefinder.hud.BaseFinderHud;
import com.baseminer.basefinder.modules.BaseFinderModule;
import com.baseminer.basefinder.modules.StuckDetector;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class BaseFinder extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Base Finder");
    public static final HudGroup HUD_GROUP = new HudGroup("Base Finder");


    @Override
    public void onInitialize() {
        LOG.info("Initializing Base Finder");

        Config.load();

        // Modules
        Modules.get().add(new BaseFinderModule());
        Modules.get().add(new StuckDetector());

        // Commands
        Commands.add(new BaseFinderCommand());
        Commands.add(new ClearBasesCommand());
        Commands.add(new ClearPlayersCommand());

        // HUD
        Hud.get().register(BaseFinderHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.baseminer.basefinder";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("omtoi", "base-finder");
    }
}
