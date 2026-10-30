package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

// Reports when the player flies from unclassified/new terrain into chunks NewerNewChunks marked as old.
// Only the transition is reported, so a long stretch of old chunks produces a single message.
public class OldChunkNotifier extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> includeOldGeneration = sgGeneral.add(new BoolSetting.Builder()
        .name("include-old-generation")
        .description("Also notify for chunks generated in an old Minecraft version.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeBeingUpdated = sgGeneral.add(new BoolSetting.Builder()
        .name("include-being-updated")
        .description("Also notify for chunks that haven't been explored since <=1.17.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Minimum seconds between two notifications.")
        .defaultValue(60)
        .min(0)
        .sliderMax(600)
        .build()
    );

    private final Setting<Boolean> discord = sgGeneral.add(new BoolSetting.Builder()
        .name("discord")
        .description("Send the notification to the Discord webhook configured in the Stash-Hunter module.")
        .defaultValue(true)
        .build()
    );

    private ChunkPos lastChunk;
    private ResourceKey<Level> lastDimension;
    private boolean inOldArea = false;
    private int cooldownTicks = 0;

    public OldChunkNotifier() {
        super(StashHunter.CATEGORY, "old-chunk-notifier", "Notifies when you enter old chunks detected by NewerNewChunks.");
    }

    @Override
    public void onActivate() {
        reset();
        if (!Modules.get().isActive(NewerNewChunks.class)) {
            warning("NewerNewChunks is not active, no chunks will be detected.");
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (cooldownTicks > 0) cooldownTicks--;

        // GameLeftEvent doesn't fire on a dimension change. ChunkPos has no dimension, and a stale
        // inOldArea would swallow the first report in the new dimension. The cooldown stays, so
        // hopping through a portal doesn't produce a new message each time.
        ResourceKey<Level> dimension = mc.level.dimension();
        if (lastDimension != null && !dimension.equals(lastDimension)) {
            lastChunk = null;
            inOldArea = false;
        }
        lastDimension = dimension;

        ChunkPos chunk = mc.player.chunkPosition();
        if (chunk.equals(lastChunk)) return;
        lastChunk = chunk;

        NewerNewChunks chunks = Modules.get().get(NewerNewChunks.class);
        if (chunks == null || !chunks.isActive()) return;

        String kind = oldChunkKind(chunks, chunk);
        if (kind == null) {
            // Unclassified chunks keep the current state, so gaps in the detection don't re-trigger.
            if (chunks.isNewChunk(chunk)) inOldArea = false;
            return;
        }
        if (inOldArea) return;
        inOldArea = true;
        if (cooldownTicks > 0) return;
        cooldownTicks = cooldown.get() * 20;
        notifyOldChunk(chunk, kind);
    }

    private String oldChunkKind(NewerNewChunks chunks, ChunkPos chunk) {
        if (chunks.isOldChunk(chunk)) return "Old chunk";
        if (includeOldGeneration.get() && chunks.isOldGenerationChunk(chunk)) return "Old-version chunk";
        if (includeBeingUpdated.get() && chunks.isBeingUpdatedChunk(chunk)) return "Chunk unexplored since <=1.17";
        return null;
    }

    private void notifyOldChunk(ChunkPos chunk, String kind) {
        String dimension = mc.level.dimension().identifier().getPath();
        String position = mc.player.blockPosition().toShortString();
        info("Entered old chunks (" + kind + ") at " + position + ".");

        if (!discord.get()) return;
        DiscordEmbed embed = new DiscordEmbed(
            "Old Chunks Entered",
            kind + " at chunk " + chunk.x() + ", " + chunk.z() + " (" + position + ") in " + dimension + ".",
            0xBEFF00
        );
        DiscordWebhook.sendMessage("", embed);
    }

    private void reset() {
        lastChunk = null;
        lastDimension = null;
        inOldArea = false;
        cooldownTicks = 0;
    }
}
