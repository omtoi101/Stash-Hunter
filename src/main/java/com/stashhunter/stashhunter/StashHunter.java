package com.stashhunter.stashhunter;

import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.commands.StashHunterCommand;
import com.stashhunter.stashhunter.commands.ClearStashesCommand;
import com.stashhunter.stashhunter.commands.ClearPlayersCommand;
import com.stashhunter.stashhunter.hud.StashHunterHud;
import com.stashhunter.stashhunter.modules.AltitudeLossDetector;
import com.stashhunter.stashhunter.modules.NewerNewChunks;
import com.stashhunter.stashhunter.modules.StashHunterModule;
import com.stashhunter.stashhunter.modules.StuckDetector;
import com.stashhunter.stashhunter.modules.AutoElytraRepair;
import com.stashhunter.stashhunter.modules.SlimeChunkOverlay;
import com.stashhunter.stashhunter.modules.OldChunkNotifier;
import com.stashhunter.stashhunter.modules.ActivatedSpawnerDetector;
import com.stashhunter.stashhunter.modules.AutoFirework;
import com.stashhunter.stashhunter.modules.OnlinePlayerActivityDetector;
import com.stashhunter.stashhunter.modules.PlayerAlarms;
import com.stashhunter.stashhunter.modules.AutoLogout;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class StashHunter extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Stash-Hunter");
    public static final HudGroup HUD_GROUP = new HudGroup("Stash-Hunter");


    @Override
    public void onInitialize() {
        LOG.info("Initializing Stash-Hunter");

        Config.load();
        Config.validate();

        // No Fabric API lifecycle events available; a JVM hook flushes the debounced config save
        // and drains queued webhooks. Config first: it's local and fast, Discord may block ~3 s.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Config.shutdown();
            DiscordWebhook.shutdown();
        }, "StashHunter-Shutdown"));

        // Modules
        Modules.get().add(new StashHunterModule());
        Modules.get().add(new StuckDetector());
        Modules.get().add(new AltitudeLossDetector());
        Modules.get().add(new NewerNewChunks());
        Modules.get().add(new AutoElytraRepair());
        Modules.get().add(new SlimeChunkOverlay());
        Modules.get().add(new OldChunkNotifier());
        Modules.get().add(new ActivatedSpawnerDetector());
        Modules.get().add(new AutoFirework());
        Modules.get().add(new OnlinePlayerActivityDetector());
        Modules.get().add(new PlayerAlarms());
        Modules.get().add(new AutoLogout());

        // Commands
        Commands.add(new StashHunterCommand());
        Commands.add(new ClearStashesCommand());
        Commands.add(new ClearPlayersCommand());

        // HUD
        Hud.get().register(StashHunterHud.INFO);
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
        return new GithubRepo("omtoi101", "Stash-Hunter");
    }
}
