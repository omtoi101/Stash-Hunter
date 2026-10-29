package com.stashhunter.stashhunter.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import com.stashhunter.stashhunter.StashHunter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/*
    Ported from: https://github.com/BleachDrinker420/BleachHack/blob/master/BleachHack-Fabric-1.16/src/main/java/bleach/hack/module/mods/NewChunks.java
    updated by etianl :D
*/
public class NewerNewChunks extends Module {
	public enum DetectMode {
		Normal,
		IgnoreBlockExploit,
		BlockExploitMode
	}
	private final SettingGroup specialGroup = settings.createGroup("Detection Settings");
	private final SettingGroup specialGroup2 = settings.createGroup("Detection for chunks that were generated in old versions.");
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	private final SettingGroup sgCdata = settings.createGroup("Saved Chunk Data");
	private final SettingGroup sgcacheCdata = settings.createGroup("Cached Chunk Data");
	private final SettingGroup sgRender = settings.createGroup("Render");

	private final Setting<Boolean> PaletteExploit = specialGroup.add(new BoolSetting.Builder()
			.name("PaletteExploit")
			.description("Detects new chunks by scanning the order of chunk section palettes. Highlights chunks being updated from an old version. Only works for server versions >= 1.18!")
			.defaultValue(true)
			.build()
	);
	private final Setting<Boolean> beingUpdatedDetector = specialGroup.add(new BoolSetting.Builder()
			.name("Detection for chunks that haven't been explored since <=1.17")
			.description("Marks chunks as their own color if they are currently being updated from old version. Requires PaletteExploit and server version >= 1.18!")
			.defaultValue(true)
			.visible(() -> PaletteExploit.get())
			.build()
	);
	private final Setting<Boolean> overworldOldChunksDetector = specialGroup2.add(new BoolSetting.Builder()
			.name("Pre 1.17 Overworld OldChunk Detector")
			.description("Marks chunks as generated in an old version if they have specific blocks above Y 0 and are in the overworld.")
			.defaultValue(true)
			.build()
	);
	private final Setting<Boolean> netherOldChunksDetector = specialGroup2.add(new BoolSetting.Builder()
			.name("Pre 1.16 Nether OldChunk Detector")
			.description("Marks chunks as generated in an old version if they are missing blocks found in the new Nether.")
			.defaultValue(true)
			.build()
	);
	private final Setting<Boolean> endOldChunksDetector = specialGroup2.add(new BoolSetting.Builder()
			.name("Pre 1.13 End OldChunk Detector")
			.description("Marks chunks as generated in an old version if they have the biome of minecraft:the_end.")
			.defaultValue(true)
			.build()
	);
	public final Setting<DetectMode> detectmode = sgGeneral.add(new EnumSetting.Builder<DetectMode>()
			.name("Chunk Detection Mode")
			.description("Anything other than normal is for old servers where build limits are being increased due to updates.")
			.defaultValue(DetectMode.Normal)
			.build()
	);
    public final Setting<Boolean> dynamicTrailDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("dynamic-trail-detection")
        .description("Enables dynamic following of new chunk trails.")
        .defaultValue(true)
        .build()
    );
	private final Setting<Boolean> liquidexploit = sgGeneral.add(new BoolSetting.Builder()
			.name("LiquidExploit")
			.description("Estimates newchunks based on flowing liquids.")
			.defaultValue(false)
			.build()
	);
	private final Setting<Boolean> blockupdateexploit = sgGeneral.add(new BoolSetting.Builder()
			.name("BlockUpdateExploit")
			.description("Estimates newchunks based on block updates. THESE MAY POSSIBLY BE OLD. BlockExploitMode needed to help determine false positives.")
			.defaultValue(false)
			.build()
	);
	private final Setting<Boolean> remove = sgcacheCdata.add(new BoolSetting.Builder()
			.name("RemoveOnModuleDisabled")
			.description("Removes the cached chunks when disabling the module.")
			.defaultValue(true)
			.build()
	);
	private final Setting<Boolean> worldleaveremove = sgcacheCdata.add(new BoolSetting.Builder()
			.name("RemoveOnLeaveWorldOrChangeDimensions")
			.description("Removes the cached chunks when leaving the world or changing dimensions.")
			.defaultValue(true)
			.build()
	);
	private final Setting<Boolean> removerenderdist = sgcacheCdata.add(new BoolSetting.Builder()
			.name("RemoveOutsideRenderDistance")
			.description("Removes the cached chunks when they leave the defined render distance.")
			.defaultValue(false)
			.build()
	);

	private final Setting<Boolean> save = sgCdata.add(new BoolSetting.Builder()
			.name("SaveChunkData")
			.description("Saves the cached chunks to a file.")
			.defaultValue(true)
			.build()
	);
	private final Setting<Boolean> load = sgCdata.add(new BoolSetting.Builder()
			.name("LoadChunkData")
			.description("Loads the saved chunks from the file.")
			.defaultValue(true)
			.build()
	);
	private final Setting<Boolean> autoreload = sgCdata.add(new BoolSetting.Builder()
			.name("AutoReloadChunks")
			.description("Reloads the chunks automatically from your savefiles on a delay.")
			.defaultValue(false)
			.visible(load::get)
			.build()
	);
	private final Setting<Integer> removedelay = sgCdata.add(new IntSetting.Builder()
			.name("AutoReloadDelayInSeconds")
			.description("Reloads the chunks automatically from your savefiles on a delay.")
			.sliderRange(1,300)
			.defaultValue(60)
			.visible(() -> autoreload.get() && load.get())
			.build()
	);

	@Override
	public WWidget getWidget(GuiTheme theme) {
		WTable table = theme.table();
		WButton deletedata = table.add(theme.button("**DELETE CHUNK DATA**")).expandX().minWidth(100).widget();
		deletedata.action = () -> {
			if (deletewarning==0) error("PRESS AGAIN WITHIN 5s TO DELETE ALL CHUNK DATA FOR THIS DIMENSION.");
			deletewarningTicks=0;
			deletewarning++;
		};
		table.row();
		return table;
	}
	public final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
			.name("Render-Distance(Chunks)")
			.description("How many chunks from the character to render the detected chunks.")
			.defaultValue(64)
			.min(6)
			.sliderRange(6,1024)
			.build()
	);
	public final Setting<Integer> renderHeight = sgRender.add(new IntSetting.Builder()
			.name("render-height")
			.description("The height at which new chunks will be rendered")
			.defaultValue(0)
			.sliderRange(-112,319)
			.build()
	);

	private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
			.name("shape-mode")
			.description("How the shapes are rendered.")
			.defaultValue(ShapeMode.Both)
			.build()
	);

	private final Setting<SettingColor> newChunksSideColor = sgRender.add(new ColorSetting.Builder()
			.name("new-chunks-side-color")
			.description("Color of the chunks that are completely new.")
			.defaultValue(new SettingColor(255, 0, 0, 95))
			.visible(() -> (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both))
			.build()
	);
	private final Setting<SettingColor> tickexploitChunksSideColor = sgRender.add(new ColorSetting.Builder()
			.name("BlockExploitChunks-side-color")
			.description("MAY POSSIBLY BE OLD. Color of the chunks that have been triggered via block ticking packets")
			.defaultValue(new SettingColor(0, 0, 255, 75))
			.visible(() -> (shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both) && detectmode.get()== DetectMode.BlockExploitMode)
			.build()
	);

	private final Setting<SettingColor> oldChunksSideColor = sgRender.add(new ColorSetting.Builder()
			.name("old-chunks-side-color")
			.description("Color of the chunks that have been loaded before.")
			.defaultValue(new SettingColor(0, 255, 0, 40))
			.visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
			.build()
	);
	private final Setting<SettingColor> beingUpdatedOldChunksSideColor = sgRender.add(new ColorSetting.Builder()
			.name("being-updated-chunks-side-color")
			.description("Color of the chunks that haven't been explored since versions <=1.17.")
			.defaultValue(new SettingColor(255, 210, 0, 60))
			.visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
			.build()
	);
	private final Setting<SettingColor> OldGenerationOldChunksSideColor = sgRender.add(new ColorSetting.Builder()
			.name("old-version-chunks-side-color")
			.description("Color of the chunks that have been loaded before in old versions.")
			.defaultValue(new SettingColor(190, 255, 0, 40))
			.visible(() -> shapeMode.get() == ShapeMode.Sides || shapeMode.get() == ShapeMode.Both)
			.build()
	);

	private final Setting<SettingColor> newChunksLineColor = sgRender.add(new ColorSetting.Builder()
			.name("new-chunks-line-color")
			.description("Color of the chunks that are completely new.")
			.defaultValue(new SettingColor(255, 0, 0, 205))
			.visible(() -> (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both))
			.build()
	);
	private final Setting<SettingColor> tickexploitChunksLineColor = sgRender.add(new ColorSetting.Builder()
			.name("BlockExploitChunks-line-color")
			.description("MAY POSSIBLY BE OLD. Color of the chunks that have been triggered via block ticking packets")
			.defaultValue(new SettingColor(0, 0, 255, 170))
			.visible(() -> (shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both) && detectmode.get()== DetectMode.BlockExploitMode)
			.build()
	);

	private final Setting<SettingColor> oldChunksLineColor = sgRender.add(new ColorSetting.Builder()
			.name("old-chunks-line-color")
			.description("Color of the chunks that have been loaded before.")
			.defaultValue(new SettingColor(0, 255, 0, 80))
			.visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both)
			.build()
	);
	private final Setting<SettingColor> beingUpdatedOldChunksLineColor = sgRender.add(new ColorSetting.Builder()
			.name("being-updated-chunks-line-color")
			.description("Color of the chunks that haven't been explored since versions <=1.17.")
			.defaultValue(new SettingColor(255, 220, 0, 100))
			.visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both)
			.build()
	);
	private final Setting<SettingColor> OldGenerationOldChunksLineColor = sgRender.add(new ColorSetting.Builder()
			.name("old-version-chunks-line-color")
			.description("Color of the chunks that have been loaded before in old versions.")
			.defaultValue(new SettingColor(190, 255, 0, 80))
			.visible(() -> shapeMode.get() == ShapeMode.Lines || shapeMode.get() == ShapeMode.Both)
			.build()
	);
	private int deletewarningTicks=666;
	private int deletewarning=0;
	private String serverip;
	private String world;
	// Client thread only: the packet handler passes every set access through mc.execute(...).
	private final Set<ChunkPos> newChunks = new HashSet<>();
	private final Set<ChunkPos> oldChunks = new HashSet<>();
	private final Set<ChunkPos> beingUpdatedOldChunks = new HashSet<>();
	private final Set<ChunkPos> OldGenerationOldChunks = new HashSet<>();
	private final Set<ChunkPos> tickexploitChunks = new HashSet<>();
	private static final Direction[] searchDirs = new Direction[] { Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.UP };
	private int errticks=0;
	private int autoreloadticks=0;
	private int loadingticks=0;
	private boolean worldchange=false;
	private boolean saveDataWasOn = false;
	// Detections waiting to be appended to their data file, keyed by the file's full path. The path is
	// resolved when the chunk is detected, so a later dimension change can't misroute it.
	private final Map<Path, List<ChunkPos>> pendingWrites = new LinkedHashMap<>();
	private static final int FLUSH_INTERVAL_TICKS = 100;
	private int flushTicks = 0;
	private enum ChunkKind {
		NEW("NewChunkData.txt"),
		OLD("OldChunkData.txt"),
		BEING_UPDATED("BeingUpdatedChunkData.txt"),
		OLD_GENERATION("OldGenerationChunkData.txt"),
		BLOCK_EXPLOIT("BlockExploitChunkData.txt");
		final Path file;
		ChunkKind(String file) { this.file = Paths.get(file); }
	}
	private static final Set<Block> ORE_BLOCKS = new HashSet<>();
	static {
		ORE_BLOCKS.add(Blocks.COAL_ORE);
		ORE_BLOCKS.add(Blocks.DEEPSLATE_COAL_ORE);
		ORE_BLOCKS.add(Blocks.COPPER_ORE);
		ORE_BLOCKS.add(Blocks.DEEPSLATE_COPPER_ORE);
		ORE_BLOCKS.add(Blocks.IRON_ORE);
		ORE_BLOCKS.add(Blocks.DEEPSLATE_IRON_ORE);
		ORE_BLOCKS.add(Blocks.GOLD_ORE);
		ORE_BLOCKS.add(Blocks.DEEPSLATE_GOLD_ORE);
		ORE_BLOCKS.add(Blocks.LAPIS_ORE);
		ORE_BLOCKS.add(Blocks.DEEPSLATE_LAPIS_ORE);
		ORE_BLOCKS.add(Blocks.DIAMOND_ORE);
		ORE_BLOCKS.add(Blocks.DEEPSLATE_DIAMOND_ORE);
		ORE_BLOCKS.add(Blocks.REDSTONE_ORE);
		ORE_BLOCKS.add(Blocks.DEEPSLATE_REDSTONE_ORE);
		ORE_BLOCKS.add(Blocks.EMERALD_ORE);
		ORE_BLOCKS.add(Blocks.DEEPSLATE_EMERALD_ORE);
	}
	private static final Set<Block> DEEPSLATE_BLOCKS = new HashSet<>();
	static {
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE);
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE_COPPER_ORE);
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE_IRON_ORE);
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE_COAL_ORE);
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE_REDSTONE_ORE);
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE_EMERALD_ORE);
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE_GOLD_ORE);
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE_LAPIS_ORE);
		DEEPSLATE_BLOCKS.add(Blocks.DEEPSLATE_DIAMOND_ORE);
	}
	private static final Set<Block> NEW_OVERWORLD_BLOCKS = new HashSet<>();
	static {
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.AMETHYST_BLOCK);
		NEW_OVERWORLD_BLOCKS.add(Blocks.BUDDING_AMETHYST);
		NEW_OVERWORLD_BLOCKS.add(Blocks.AZALEA);
		NEW_OVERWORLD_BLOCKS.add(Blocks.FLOWERING_AZALEA);
		NEW_OVERWORLD_BLOCKS.add(Blocks.BIG_DRIPLEAF);
		NEW_OVERWORLD_BLOCKS.add(Blocks.BIG_DRIPLEAF_STEM);
		NEW_OVERWORLD_BLOCKS.add(Blocks.SMALL_DRIPLEAF);
		NEW_OVERWORLD_BLOCKS.add(Blocks.CAVE_VINES);
		NEW_OVERWORLD_BLOCKS.add(Blocks.CAVE_VINES_PLANT);
		NEW_OVERWORLD_BLOCKS.add(Blocks.SPORE_BLOSSOM);
		NEW_OVERWORLD_BLOCKS.add(Blocks.COPPER_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE_COPPER_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE_IRON_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE_COAL_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE_REDSTONE_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE_EMERALD_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE_GOLD_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE_LAPIS_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DEEPSLATE_DIAMOND_ORE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.GLOW_LICHEN);
		NEW_OVERWORLD_BLOCKS.add(Blocks.RAW_COPPER_BLOCK);
		NEW_OVERWORLD_BLOCKS.add(Blocks.RAW_IRON_BLOCK);
		NEW_OVERWORLD_BLOCKS.add(Blocks.DRIPSTONE_BLOCK);
		NEW_OVERWORLD_BLOCKS.add(Blocks.MOSS_BLOCK);
		NEW_OVERWORLD_BLOCKS.add(Blocks.MOSS_CARPET);
		NEW_OVERWORLD_BLOCKS.add(Blocks.POINTED_DRIPSTONE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.SMOOTH_BASALT);
		NEW_OVERWORLD_BLOCKS.add(Blocks.TUFF);
		NEW_OVERWORLD_BLOCKS.add(Blocks.CALCITE);
		NEW_OVERWORLD_BLOCKS.add(Blocks.HANGING_ROOTS);
		NEW_OVERWORLD_BLOCKS.add(Blocks.ROOTED_DIRT);
		NEW_OVERWORLD_BLOCKS.add(Blocks.AZALEA_LEAVES);
		NEW_OVERWORLD_BLOCKS.add(Blocks.FLOWERING_AZALEA_LEAVES);
		NEW_OVERWORLD_BLOCKS.add(Blocks.POWDER_SNOW);
	}
	private static final Set<Block> NEW_NETHER_BLOCKS = new HashSet<>();
	static {
		NEW_NETHER_BLOCKS.add(Blocks.ANCIENT_DEBRIS);
		NEW_NETHER_BLOCKS.add(Blocks.BASALT);
		NEW_NETHER_BLOCKS.add(Blocks.BLACKSTONE);
		NEW_NETHER_BLOCKS.add(Blocks.GILDED_BLACKSTONE);
		NEW_NETHER_BLOCKS.add(Blocks.POLISHED_BLACKSTONE_BRICKS);
		NEW_NETHER_BLOCKS.add(Blocks.CRIMSON_STEM);
		NEW_NETHER_BLOCKS.add(Blocks.CRIMSON_NYLIUM);
		NEW_NETHER_BLOCKS.add(Blocks.NETHER_GOLD_ORE);
		NEW_NETHER_BLOCKS.add(Blocks.WARPED_NYLIUM);
		NEW_NETHER_BLOCKS.add(Blocks.WARPED_STEM);
		NEW_NETHER_BLOCKS.add(Blocks.TWISTING_VINES);
		NEW_NETHER_BLOCKS.add(Blocks.WEEPING_VINES);
		NEW_NETHER_BLOCKS.add(Blocks.BONE_BLOCK);
		NEW_NETHER_BLOCKS.add(Blocks.IRON_CHAIN);
		NEW_NETHER_BLOCKS.add(Blocks.OBSIDIAN);
		NEW_NETHER_BLOCKS.add(Blocks.CRYING_OBSIDIAN);
		NEW_NETHER_BLOCKS.add(Blocks.SOUL_SOIL);
		NEW_NETHER_BLOCKS.add(Blocks.SOUL_FIRE);
	}
	Set<Path> FILE_PATHS = new HashSet<>(Set.of(
			Paths.get("OldChunkData.txt"),
			Paths.get("BeingUpdatedChunkData.txt"),
			Paths.get("OldGenerationChunkData.txt"),
			Paths.get("NewChunkData.txt"),
			Paths.get("BlockExploitChunkData.txt")
	));
	public NewerNewChunks() {
		super(StashHunter.CATEGORY,"NewerNewChunks", "Detects new chunks by scanning chunk section palettes (1.18+ only), liquid flow, and block ticking packets.");
	}
	public boolean isNewChunk(ChunkPos pos) {
		return newChunks.contains(pos);
	}
	private void clearChunkData() {
		newChunks.clear();
		oldChunks.clear();
		beingUpdatedOldChunks.clear();
		OldGenerationOldChunks.clear();
		tickexploitChunks.clear();
	}
	@Override
	public void onActivate() {
		saveDataWasOn = save.get();
		if (autoreload.get()) {
			clearChunkData();
		}
		autoreloadticks=0;
		loadingticks=0;
		worldchange=false;
		if (mc.level == null) return;

		if (save.get() || load.get()) {
			world= mc.level.dimension().identifier().toString().replace(':', '_');
			updateServerIp();
		}
		if (save.get()){
			try {
				Files.createDirectories(Paths.get("StashHunter", "NewChunks", serverip, world));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// Check server version and warn if PaletteExploit is enabled for older servers
		if (PaletteExploit.get()) {
			// You can add version detection logic here if needed
			// For now, just show a warning in the info
			info("PaletteExploit enabled - make sure your server is version 1.18 or higher!");
		}
		if (save.get() || load.get()) {
			Path baseDir = Paths.get("StashHunter", "NewChunks", serverip, world);

			for (Path fileName : FILE_PATHS) {
				Path fullPath = baseDir.resolve(fileName);
				try {
					Files.createDirectories(fullPath.getParent());
					if (Files.notExists(fullPath)) {
						Files.createFile(fullPath);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if (load.get()){
			loadData();
		}
	}
	private void updateServerIp() {
		if (mc.hasSingleplayerServer()){
			String[] array = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toString().replace(':', '_').split("/|\\\\");
			serverip=array[array.length-2];
		} else {
			serverip = mc.getCurrentServer().ip.replace(':', '_');
		}
	}
	@Override
	public void onDeactivate() {
		autoreloadticks=0;
		loadingticks=0;
		worldchange=false;
		flushPendingWrites();
		if (remove.get()|autoreload.get()) {
			clearChunkData();
		}
		super.onDeactivate();
	}
	@EventHandler
	private void onScreenOpen(OpenScreenEvent event) {
		if (event.screen instanceof DisconnectedScreen) {
			if (worldleaveremove.get()) {
				clearChunkData();
			}
		}
		if (event.screen instanceof LevelLoadingScreen) {
			worldchange=true;
		}
	}
	@EventHandler
	private void onGameLeft(GameLeftEvent event) {
		flushPendingWrites();
		serverip = null; // re-resolved on the next world join
		if (worldleaveremove.get()) {
			clearChunkData();
		}
	}
	@EventHandler
	private void onPreTick(TickEvent.Pre event) {
		if (mc.level == null) return;
		String currentWorld = mc.level.dimension().identifier().toString().replace(':', '_');
		if (!currentWorld.equals(world) || ++flushTicks >= FLUSH_INTERVAL_TICKS) {
			flushPendingWrites();
			flushTicks = 0;
		}
		world = currentWorld;
		if (serverip == null) updateServerIp();

		if (deletewarningTicks<=100) deletewarningTicks++;
		else deletewarning=0;
		if (deletewarning>=2){
			updateServerIp();
			clearChunkData();
			Path dataDir = Paths.get("StashHunter", "NewChunks", serverip, world);
			pendingWrites.keySet().removeIf(path -> path.startsWith(dataDir));
			try {
				Files.deleteIfExists(Paths.get("StashHunter", "NewChunks", serverip, world, "NewChunkData.txt"));
				Files.deleteIfExists(Paths.get("StashHunter", "NewChunks", serverip, world, "OldChunkData.txt"));
				Files.deleteIfExists(Paths.get("StashHunter", "NewChunks", serverip, world, "BeingUpdatedChunkData.txt"));
				Files.deleteIfExists(Paths.get("StashHunter", "NewChunks", serverip, world, "OldGenerationChunkData.txt"));
				Files.deleteIfExists(Paths.get("StashHunter", "NewChunks", serverip, world, "BlockExploitChunkData.txt"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			error("Chunk Data deleted for this Dimension.");
			deletewarning=0;
		}

		if (detectmode.get()== DetectMode.Normal && blockupdateexploit.get()){
			if (errticks<6){
				errticks++;}
			if (errticks==5){
				error("BlockExploitMode RECOMMENDED. Required to determine false positives from the Block Exploit from the OldChunks.");
			}
		} else errticks=0;

		// Additional warning for PaletteExploit on older servers
		if (PaletteExploit.get()) {
			// Add any server version checks here if you have access to server info
			// For example, you could check protocol version or other indicators
		}

		if (load.get()){
			if (loadingticks<1){
				loadData();
				loadingticks++;
			}
		} else if (!load.get()){
			loadingticks=0;
		}

		if (autoreload.get()) {
			autoreloadticks++;
			if (autoreloadticks==removedelay.get()*20){
				clearChunkData();
				if (load.get()){
					loadData();
				}
			} else if (autoreloadticks>=removedelay.get()*20){
				autoreloadticks=0;
			}
		}

		if (load.get() && worldchange){		//autoreload when entering different dimensions
			if (worldleaveremove.get()){
				clearChunkData();
			}
			loadData();
			worldchange=false;
		}

		// SaveChunkData switched on while the module is running: dump the chunks detected so far once.
		if (save.get() && !saveDataWasOn){
			for (ChunkPos chunk : newChunks){
				saveData(Paths.get("NewChunkData.txt"), chunk);
			}
			for (ChunkPos chunk : OldGenerationOldChunks){
				saveData(Paths.get("OldGenerationChunkData.txt"), chunk);
			}
			for (ChunkPos chunk : beingUpdatedOldChunks){
				saveData(Paths.get("BeingUpdatedChunkData.txt"), chunk);
			}
			for (ChunkPos chunk : oldChunks){
				saveData(Paths.get("OldChunkData.txt"), chunk);
			}
			for (ChunkPos chunk : tickexploitChunks){
				saveData(Paths.get("BlockExploitChunkData.txt"), chunk);
			}
		}
		saveDataWasOn = save.get();

		if (removerenderdist.get())removeChunksOutsideRenderDistance();
	}
	@EventHandler
	private void onRender(Render3DEvent event) {
		if (mc.level == null || mc.player == null) return;
		if (event.renderer == null) return;
		int px = mc.player.getBlockX();
		int pz = mc.player.getBlockZ();
		double maxDistance = renderDistance.get() * 16.0;
		double maxDistanceSq = maxDistance * maxDistance;
		double y = renderHeight.get();
		ShapeMode mode = shapeMode.get();
		if (newChunksLineColor.get().a > 5 || newChunksSideColor.get().a > 5) {
			renderChunks(newChunks, newChunksSideColor.get(), newChunksLineColor.get(), mode, event, px, pz, y, maxDistanceSq);
		}
		if (tickexploitChunksLineColor.get().a > 5 || tickexploitChunksSideColor.get().a > 5) {
			Color sides = oldChunksSideColor.get();
			Color lines = oldChunksLineColor.get();
			if (blockupdateexploit.get() && detectmode.get() == DetectMode.BlockExploitMode) {
				sides = tickexploitChunksSideColor.get();
				lines = tickexploitChunksLineColor.get();
			} else if (blockupdateexploit.get() && detectmode.get() == DetectMode.Normal) {
				sides = newChunksSideColor.get();
				lines = newChunksLineColor.get();
			}
			renderChunks(tickexploitChunks, sides, lines, mode, event, px, pz, y, maxDistanceSq);
		}
		if (oldChunksLineColor.get().a > 5 || oldChunksSideColor.get().a > 5){
			renderChunks(oldChunks, oldChunksSideColor.get(), oldChunksLineColor.get(), mode, event, px, pz, y, maxDistanceSq);
		}
		if (beingUpdatedOldChunksLineColor.get().a > 5 || beingUpdatedOldChunksSideColor.get().a > 5){
			renderChunks(beingUpdatedOldChunks, beingUpdatedOldChunksSideColor.get(), beingUpdatedOldChunksLineColor.get(), mode, event, px, pz, y, maxDistanceSq);
		}
		if (OldGenerationOldChunksLineColor.get().a > 5 || OldGenerationOldChunksSideColor.get().a > 5){
			renderChunks(OldGenerationOldChunks, OldGenerationOldChunksSideColor.get(), OldGenerationOldChunksLineColor.get(), mode, event, px, pz, y, maxDistanceSq);
		}
	}

	// Runs every frame: plain int/double math only, no per-chunk BlockPos/Vec3/AABB allocations.
	private void renderChunks(Set<ChunkPos> chunks, Color sides, Color lines, ShapeMode mode, Render3DEvent event, int px, int pz, double y, double maxDistanceSq) {
		for (ChunkPos c : chunks) {
			if (c == null) continue;
			double dx = c.getMiddleBlockX() - px;
			double dz = c.getMiddleBlockZ() - pz;
			if (dx * dx + dz * dz >= maxDistanceSq) continue;
			double minX = c.getMinBlockX();
			double minZ = c.getMinBlockZ();
			try {
				event.renderer.box(minX, y, minZ, minX + 16, y, minZ + 16, sides, lines, mode, 0);
			} catch (Exception e) {}
		}
	}

	// PacketEvent.Receive is posted on the Netty connection thread. Anything that reads mc.level or
	// touches the chunk sets / data files is handed to the client thread via mc.execute(...). This
	// handler runs before vanilla schedules its own handling of the packet, so the queued task still
	// sees the world as it was before the packet was applied.
	@EventHandler
	private void onReadPacket(PacketEvent.Receive event) {
		if (event.packet instanceof ServerboundChunkBatchReceivedPacket )return; //for some reason this packet keeps getting cast to other packets
		ClientLevel level = mc.level;
		if (level == null) return;
		if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet && liquidexploit.get()) {
			mc.execute(() -> {
				if (mc.level != level) return;
				packet.runUpdates((pos, state) -> {
					if (isFlowingNextToSource(level, pos, state)) promoteToNew(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
				});
			});
		}
		else if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
			mc.execute(() -> {
				if (mc.level != level) return;
				ChunkPos chunkPos = new ChunkPos(packet.getPos().getX() >> 4, packet.getPos().getZ() >> 4);
				if (blockupdateexploit.get()) markChunk(chunkPos, ChunkKind.BLOCK_EXPLOIT);
				if (liquidexploit.get() && isFlowingNextToSource(level, packet.getPos(), packet.getBlockState())) promoteToNew(chunkPos);
			});
		}
		else if (event.packet instanceof ClientboundLevelChunkWithLightPacket packet) {
			if (level.getChunkSource().getChunk(packet.getX(), packet.getZ(), false) == null) { // null until vanilla has loaded the chunk, i.e. only on its first arrival
				ChunkPos chunkPos = new ChunkPos(packet.getX(), packet.getZ());
				// Decoding and analysis stay on the Netty thread: they only touch our own private
				// LevelChunk copy, and getReadBuffer() wraps a fresh buffer for every caller.
				ChunkKind kind = classifyChunk(level, packet, chunkPos);
				if (kind != null) {
					mc.execute(() -> {
						if (mc.level == level) markChunk(chunkPos, kind);
					});
				}
			}
		}
	}

	private static boolean isFlowingNextToSource(ClientLevel level, BlockPos pos, BlockState state) {
		FluidState fluid = state.getFluidState();
		if (fluid.isEmpty() || fluid.isSource()) return false;
		for (Direction dir : searchDirs) {
			if (level.getBlockState(pos.relative(dir)).getFluidState().isSource()) return true;
		}
		return false;
	}

	private ChunkKind classifyChunk(ClientLevel level, ClientboundLevelChunkWithLightPacket packet, ChunkPos oldpos) {
		LevelChunk chunk = new LevelChunk(level, oldpos);
		try {
			chunk.replaceWithPacketData(packet.getChunkData().getReadBuffer(), packet.getChunkData().getHeightmaps(),
					packet.getChunkData().getBlockEntitiesTagsConsumer(packet.getX(), packet.getZ()));
		} catch (RuntimeException e) {
			return null; // a half-decoded chunk would only produce false positives
		}

		boolean isNewChunk = false;
		boolean isOldGeneration = false;
		boolean chunkIsBeingUpdated = false;
		boolean foundAnyOre = false;
		boolean isNewOverworldGeneration = false;
		boolean isNewNetherGeneration = false;
		LevelChunkSection[] sections = chunk.getSections();

		if (overworldOldChunksDetector.get() && level.dimension() == Level.OVERWORLD && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL) && !chunk.isEmpty()) {
			// Vanilla layout (min Y -64): section 4 holds y=0..15, the scan covers y < 208.
			int zeroSection = level.getSectionIndex(0);
			int overworldEnd = Math.min(sections.length, level.getSectionIndex(208));
			for (int i = 0; i < overworldEnd; i++) {
				LevelChunkSection section = sections[i];
				// Bug fix: was "!!section.hasOnlyAir()" (double negation cancels out), which
				// only ever scanned sections that were pure air - the detectors below need
				// sections that actually have content, i.e. NOT only air.
				if (section != null && !section.hasOnlyAir()) {
					for (int x = 0; x < 16; x++) {
						for (int y = 0; y < 16; y++) {
							for (int z = 0; z < 16; z++) {
								if (!foundAnyOre && ORE_BLOCKS.contains(section.getBlockState(x, y, z).getBlock())) foundAnyOre = true; //prevent false flags in flat world
								if (((y >= 5 && i == zeroSection) || i > zeroSection) && !isNewOverworldGeneration && (NEW_OVERWORLD_BLOCKS.contains(section.getBlockState(x, y, z).getBlock()) || DEEPSLATE_BLOCKS.contains(section.getBlockState(x, y, z).getBlock()))) {
									isNewOverworldGeneration = true;
									break;
								}
							}
						}
					}
				}
			}
			if (foundAnyOre && !isOldGeneration && !isNewOverworldGeneration) isOldGeneration = true;
		}

		if (netherOldChunksDetector.get() && level.dimension() == Level.NETHER && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL) && !chunk.isEmpty()) {
			int netherEnd = Math.min(sections.length, level.getSectionIndex(128)); // below the bedrock roof
			for (int i = 0; i < netherEnd; i++) {
				LevelChunkSection section = sections[i];
				// See the double-negation fix note in the overworld detector above.
				if (section != null && !section.hasOnlyAir()) {
					for (int x = 0; x < 16; x++) {
						for (int y = 0; y < 16; y++) {
							for (int z = 0; z < 16; z++) {
								if (!isNewNetherGeneration && NEW_NETHER_BLOCKS.contains(section.getBlockState(x, y, z).getBlock())) {
									isNewNetherGeneration = true;
									break;
								}
							}
						}
					}
				}
			}
			if (!isOldGeneration && !isNewNetherGeneration) isOldGeneration = true;
		}

		if (endOldChunksDetector.get() && level.dimension() == Level.END && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL) && !chunk.isEmpty()) {
			// Heuristic, best-effort End chunk detection: checks for old generation
			// patterns instead of biome palettes, which aren't reliably accessible here.
			try {
				// Look for old End generation patterns instead of biome palettes
				boolean hasOldEndStructure = false;
				for (int x = 0; x < 16 && !hasOldEndStructure; x++) {
					for (int z = 0; z < 16 && !hasOldEndStructure; z++) {
						// Check for typical old End generation (mostly end stone at surface)
						BlockState surfaceBlock = chunk.getBlockState(new BlockPos(x, 64, z));
						if (surfaceBlock.getBlock() == Blocks.END_STONE) {
							hasOldEndStructure = true;
						}
					}
				}
				if (hasOldEndStructure) {
					isOldGeneration = true;
				}
			} catch (Exception e) {
				// Fallback: assume old generation if we can't detect properly
				isOldGeneration = true;
			}
		}

		if (PaletteExploit.get()) {
			// Heuristic, best-effort palette-based detection - avoids relying on direct
			// palette-index access, which isn't a stable public API surface.
			boolean firstchunkappearsnew = false;
			int loops = 0;
			int newChunkQuantifier = 0;
			int oldChunkQuantifier = 0;
			try {
				for (LevelChunkSection section : sections) {
					// See the double-negation fix note in the overworld detector above.
					if (section != null && !section.hasOnlyAir()) {
						var blockStatesContainer = section.getStates();

						// Heuristic, best-effort detection based on block state container
						// patterns rather than direct palette access.
						int uniqueBlockTypes = 0;
						Set<Block> blocksFound = new HashSet<>();

						// Sample some blocks to estimate diversity
						for (int x = 0; x < 16; x += 4) {
							for (int y = 0; y < 16; y += 4) {
								for (int z = 0; z < 16; z += 4) {
									Block block = blockStatesContainer.get(x, y, z).getBlock();
									blocksFound.add(block);
								}
							}
						}
						uniqueBlockTypes = blocksFound.size();

						// New chunks typically have fewer unique block types in early sections
						if (loops == 0 && blocksFound.contains(Blocks.AIR) && uniqueBlockTypes <= 3) {
							firstchunkappearsnew = true;
							newChunkQuantifier++;
						} else if (uniqueBlockTypes > 5) {
							oldChunkQuantifier++;
						}

						// Detect being updated chunks by looking for bedrock in upper sections
						if (loops == 4 && blocksFound.contains(Blocks.BEDROCK) &&
							level.dimension() != Level.NETHER &&
							level.dimension() != Level.END) {
							if (!chunkIsBeingUpdated && beingUpdatedDetector.get()) {
								chunkIsBeingUpdated = true;
							}
						}

						loops++;
					}
				}

				if (loops > 0) {
					if (beingUpdatedDetector.get() && (level.dimension() == Level.NETHER || level.dimension() == Level.END)){
						double oldpercentage = ((double) oldChunkQuantifier / loops) * 100;
						if (oldpercentage >= 25) chunkIsBeingUpdated = true;
					}
					else if (level.dimension() != Level.NETHER && level.dimension() != Level.END){
						double percentage = ((double) newChunkQuantifier / loops) * 100;
						if (percentage >= 51 || firstchunkappearsnew) isNewChunk = true;
					}
				}
			} catch (Exception e) {
				// Fallback logic if palette detection fails
				if (firstchunkappearsnew) isNewChunk = true;
			}

			boolean bewlian = (level.dimension() == Level.END) ? isNewChunk : !isOldGeneration;
			if (isNewChunk && !chunkIsBeingUpdated && bewlian) return ChunkKind.NEW;
			if (!isNewChunk && !chunkIsBeingUpdated && isOldGeneration) return ChunkKind.OLD_GENERATION;
			if (chunkIsBeingUpdated) return ChunkKind.BEING_UPDATED;
			if (!isNewChunk) return ChunkKind.OLD;
		}
		if (liquidexploit.get()) {
			for (int x = 0; x < 16; x++) {
				for (int y = level.getMinY(); y < level.getMaxY(); y++) {
					for (int z = 0; z < 16; z++) {
						FluidState fluid = chunk.getFluidState(x, y, z);
						if (!fluid.isEmpty() && !fluid.isSource()) return ChunkKind.OLD;
					}
				}
			}
		}
		return null;
	}

	private boolean isKnownChunk(ChunkPos pos) {
		return newChunks.contains(pos) || oldChunks.contains(pos) || beingUpdatedOldChunks.contains(pos)
				|| OldGenerationOldChunks.contains(pos) || tickexploitChunks.contains(pos);
	}

	// Client thread only. A chunk is classified once, by the first detector that reports it.
	private void markChunk(ChunkPos pos, ChunkKind kind) {
		if (isKnownChunk(pos)) return;
		Set<ChunkPos> target = switch (kind) {
			case NEW -> newChunks;
			case OLD -> oldChunks;
			case BEING_UPDATED -> beingUpdatedOldChunks;
			case OLD_GENERATION -> OldGenerationOldChunks;
			case BLOCK_EXPLOIT -> tickexploitChunks;
		};
		target.add(pos);
		if (save.get()) saveData(kind.file, pos);
	}

	// Client thread only. Flowing liquid next to a source is fresh terrain, so it overrides a
	// block-update-exploit flag, but not any of the palette/liquid classifications.
	private void promoteToNew(ChunkPos pos) {
		if (newChunks.contains(pos) || oldChunks.contains(pos) || beingUpdatedOldChunks.contains(pos) || OldGenerationOldChunks.contains(pos)) return;
		if (tickexploitChunks.remove(pos)) unqueueData(ChunkKind.BLOCK_EXPLOIT.file, pos);
		newChunks.add(pos);
		if (save.get()) saveData(ChunkKind.NEW.file, pos);
	}
	private void loadData() {
		flushPendingWrites();
		loadChunkData(Paths.get("BlockExploitChunkData.txt"), tickexploitChunks);
		loadChunkData(Paths.get("OldChunkData.txt"), oldChunks);
		loadChunkData(Paths.get("NewChunkData.txt"), newChunks);
		loadChunkData(Paths.get("BeingUpdatedChunkData.txt"), beingUpdatedOldChunks);
		loadChunkData(Paths.get("OldGenerationChunkData.txt"), OldGenerationOldChunks);
	}
	private void loadChunkData(Path savedDataLocation, Set<ChunkPos> chunkSet) {
		try {
			Path filePath = Paths.get("StashHunter/NewChunks", serverip, world).resolve(savedDataLocation);
			List<String> allLines = Files.readAllLines(filePath);

			for (String line : allLines) {
				if (line != null && !line.isEmpty()) {
					String[] array = line.split(", ");
					if (array.length == 2) {
						int X = Integer.parseInt(array[0].replaceAll("\\[", "").replaceAll("\\]", ""));
						int Z = Integer.parseInt(array[1].replaceAll("\\[", "").replaceAll("\\]", ""));
						ChunkPos chunkPos = new ChunkPos(X, Z);
						if (!OldGenerationOldChunks.contains(chunkPos) && !beingUpdatedOldChunks.contains(chunkPos) && !tickexploitChunks.contains(chunkPos) && !newChunks.contains(chunkPos) && !oldChunks.contains(chunkPos)) {
							chunkSet.add(chunkPos);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	// Only queues the line; flushPendingWrites() appends each file's batch in a single write.
	private void saveData(Path savedDataLocation, ChunkPos chunkpos) {
		if (serverip == null || world == null) return;
		Path filePath = Paths.get("StashHunter", "NewChunks", serverip, world).resolve(savedDataLocation);
		pendingWrites.computeIfAbsent(filePath, path -> new ArrayList<>()).add(chunkpos);
	}
	// Drops a queued line again. Lines already flushed stay in the file; loadData() reads
	// BlockExploitChunkData.txt first, so such a chunk comes back as block-exploit, not new.
	private void unqueueData(Path savedDataLocation, ChunkPos chunkpos) {
		if (serverip == null || world == null) return;
		List<ChunkPos> queued = pendingWrites.get(Paths.get("StashHunter", "NewChunks", serverip, world).resolve(savedDataLocation));
		if (queued != null) queued.remove(chunkpos);
	}
	private void flushPendingWrites() {
		if (pendingWrites.isEmpty()) return;
		for (Map.Entry<Path, List<ChunkPos>> entry : pendingWrites.entrySet()) {
			StringBuilder data = new StringBuilder();
			for (ChunkPos chunkpos : entry.getValue()) {
				data.append(chunkpos).append(System.lineSeparator());
			}
			try {
				Files.createDirectories(entry.getKey().getParent());
				Files.write(entry.getKey(), data.toString().getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE,
						StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		pendingWrites.clear();
	}
	private void removeChunksOutsideRenderDistance() {
		if (mc.player == null) return;
		int px = mc.player.getBlockX();
		int pz = mc.player.getBlockZ();
		double maxDistance = renderDistance.get() * 16.0;
		double maxDistanceSq = maxDistance * maxDistance;

		removeChunksOutsideRenderDistance(newChunks, px, pz, maxDistanceSq);
		removeChunksOutsideRenderDistance(oldChunks, px, pz, maxDistanceSq);
		removeChunksOutsideRenderDistance(beingUpdatedOldChunks, px, pz, maxDistanceSq);
		removeChunksOutsideRenderDistance(OldGenerationOldChunks, px, pz, maxDistanceSq);
		removeChunksOutsideRenderDistance(tickexploitChunks, px, pz, maxDistanceSq);
	}
	private void removeChunksOutsideRenderDistance(Set<ChunkPos> chunkSet, int px, int pz, double maxDistanceSq) {
		chunkSet.removeIf(c -> {
			double dx = c.getMiddleBlockX() - px;
			double dz = c.getMiddleBlockZ() - pz;
			return dx * dx + dz * dz >= maxDistanceSq;
		});
	}
}
