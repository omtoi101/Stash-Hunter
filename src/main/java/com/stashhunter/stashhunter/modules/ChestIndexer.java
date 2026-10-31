package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.ChestIndex;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.ServerKey;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
    Clean-room implementation of PLAN item 53, written against the Minecraft and Meteor APIs only.
    No code from jeff mod (miles352/meteor-stashhunting-addon, no license).

    Every container that opens after a click on a chest, trapped chest, barrel or shulker box is
    read into the per-server ChestIndex: by auto-open, or by the player. A container counts as read
    once the server sent its contents: a new menu has stateId 0 until the content packet arrives.
*/
public class ChestIndexer extends Module {
    private static final Path INDEX_DIR = MeteorClient.FOLDER.toPath().resolve("StashHunter").resolve("chest-index");
    // How long a click may take to open its container (blocked chest, lag) before it's given up.
    private static final int OPEN_TIMEOUT_TICKS = 40;
    // A container the player opened is re-read this often, so taking items out shows in the index.
    private static final int REREAD_INTERVAL_TICKS = 10;
    private static final int SAVE_INTERVAL_TICKS = 1200;
    // Bounds the "already opened this session" memory.
    private static final int MAX_HANDLED = 8192;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open")
        .description("Open containers in reach one after another, read them and close them again. Off: only index containers you open yourself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait between two auto-opened containers (20 ticks = 1 second).")
        .defaultValue(10)
        .min(2)
        .sliderRange(2, 40)
        .visible(autoOpen::get)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Look at a container before auto-opening it. Some anti-cheats reject clicks on blocks you don't look at.")
        .defaultValue(true)
        .visible(autoOpen::get)
        .build()
    );

    private final Setting<Boolean> skipIndexed = sgGeneral.add(new BoolSetting.Builder()
        .name("skip-indexed")
        .description("Don't auto-open containers that are already in the index (also from earlier sessions). Turn off to refresh it.")
        .defaultValue(true)
        .visible(autoOpen::get)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Report each indexed container in chat.")
        .defaultValue(true)
        .build()
    );

    // Client thread only.
    private ChestIndex index;
    private ClientLevel lastLevel;
    private final Set<BlockPos> handled = new HashSet<>(); // auto-opened (or given up) since activation
    private BlockPos autoTarget;          // the container auto-open clicked last

    private BlockPos pendingPos;          // clicked container whose menu hasn't arrived yet
    private boolean pendingAuto;
    private int pendingSince;

    private AbstractContainerMenu menu;   // open container being read
    private BlockPos menuPos;
    private boolean menuAuto;
    private boolean menuAnnounced;

    private int ticks;
    private int cooldown;
    private int ticksSinceSave;

    public ChestIndexer() {
        super(StashHunter.CATEGORY, "chest-index",
            "Records what the containers you open (or that it opens for you) hold, shulker contents included. Search with .chest-index.");
    }

    @Override
    public void onActivate() {
        resetSession();
    }

    @Override
    public void onDeactivate() {
        if (index != null) index.saveIfDirty();
        resetSession();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (index != null) index.saveIfDirty();
        index = null; // the next server has its own file
        resetSession();
    }

    /** Forgets what was opened this session, so auto-open goes over everything in reach again. */
    public void resetSession() {
        handled.clear();
        autoTarget = null;
        pendingPos = null;
        menu = null;
        lastLevel = null;
        cooldown = 0;
    }

    /** The index of the current server, or null outside a world. */
    public ChestIndex index() {
        if (index == null && mc.level != null) index = ChestIndex.load(INDEX_DIR.resolve(ServerKey.current() + ".json"));
        return index;
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.level == null) return;
        BlockPos pos = event.result.getBlockPos();
        if (!isIndexable(mc.level.getBlockState(pos))) return;
        pendingPos = pos.immutable();
        pendingAuto = pendingPos.equals(autoTarget);
        pendingSince = ticks;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;
        ticks++;
        if (mc.level != lastLevel) {
            // Positions of another dimension mean nothing here.
            resetSession();
            lastLevel = mc.level;
        }
        if (++ticksSinceSave >= SAVE_INTERVAL_TICKS) {
            ticksSinceSave = 0;
            if (index != null) index.saveIfDirty();
        }

        AbstractContainerMenu open = mc.player.containerMenu;
        if (menu != null && open != menu) {
            // Closed by the player or the server. The client keeps the last contents in the old
            // menu, so the final state (after taking items out) still gets recorded.
            if (!menuAuto) record(menu);
            menu = null;
        }
        if (menu == null && pendingPos != null && isStorageMenu(open) && open.getStateId() != 0) {
            menu = open;
            menuPos = pendingPos;
            menuAuto = pendingAuto;
            menuAnnounced = false;
            pendingPos = null;
        }
        if (menu != null) {
            if (menuAuto) {
                record(menu);
                mc.player.closeContainer();
                menu = null;
                cooldown = delay.get();
            } else if (!menuAnnounced || ticks % REREAD_INTERVAL_TICKS == 0) {
                record(menu);
            }
            return;
        }

        if (pendingPos != null && ticks - pendingSince > OPEN_TIMEOUT_TICKS) pendingPos = null;
        if (!autoOpen.get() || pendingPos != null || --cooldown > 0 || !canAutoOpen()) return;
        BlockPos target = nextTarget();
        if (target != null) open(target);
    }

    private boolean canAutoOpen() {
        // Screens (chat, inventory), sneaking (opens nothing while holding an item) and flights
        // (a screen would release the movement keys) all get in the way.
        return mc.gui.screen() == null && mc.player.isAlive() && !mc.player.isShiftKeyDown() && !mc.player.isFallFlying()
            && mc.player.containerMenu == mc.player.inventoryMenu && !ElytraController.isActive();
    }

    private BlockPos nextTarget() {
        ChestIndex known = index();
        String dimension = dimension();
        int radius = (int) Math.ceil(mc.player.blockInteractionRange()) + 1;
        BlockPos center = mc.player.blockPosition();
        Vec3 eyes = mc.player.getEyePosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!mc.player.isWithinBlockInteractionRange(pos, 0) || handled.contains(pos)) continue;
                    BlockState state = mc.level.getBlockState(pos);
                    if (!isIndexable(state)) continue;
                    if (skipIndexed.get() && known != null && known.contains(dimension, toPos(pos))) continue;
                    // A chest under a solid block (or a cat) doesn't open; don't wait out the timeout.
                    if (state.getBlock() instanceof ChestBlock && ChestBlock.isChestBlockedAt(mc.level, pos)) continue;
                    double distance = eyes.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private void open(BlockPos target) {
        if (handled.size() >= MAX_HANDLED) handled.clear();
        handled.add(target);
        autoTarget = target;
        // Wait for this click even if the rotation delays it by a tick.
        pendingPos = target;
        pendingAuto = true;
        pendingSince = ticks;
        cooldown = delay.get();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), BlockUtils.getDirection(target), target, false);
        Runnable click = () -> BlockUtils.interact(hit, InteractionHand.MAIN_HAND, true);
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), click);
        else click.run();
    }

    private void record(AbstractContainerMenu container) {
        BlockState state = mc.level.getBlockState(menuPos);
        if (!isIndexable(state)) return; // broken in the meantime

        List<ChestIndex.Stack> stacks = new ArrayList<>();
        int slots = 0;
        for (Slot slot : container.slots) {
            if (slot.container == mc.player.getInventory()) continue;
            slots++;
            if (slot.hasItem()) stacks.add(toStack(slot.getItem()));
        }
        BlockPos pair = otherHalf(menuPos, state, slots);
        ChestIndex.Entry entry = ChestIndex.entry(dimension(), toPos(menuPos), pair != null ? toPos(pair) : null,
            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), slots, Instant.now(), stacks);
        if (pair != null) handled.add(pair);

        ChestIndex known = index();
        if (!entry.sameContents(known.get(entry.dimension(), entry.pos()))) known.put(entry);
        if (!menuAnnounced) {
            menuAnnounced = true;
            if (chatFeedback.get()) {
                info("Indexed %s at %s: %d items, %d shulker boxes.", state.getBlock().getName().getString(),
                    menuPos.toShortString(), entry.totalItems(), entry.shulkers().size());
            }
        }
    }

    private static ChestIndex.Stack toStack(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        List<ChestIndex.Stack> inner = contents == null ? null
            : contents.nonEmptyItemCopyStream().map(s -> new ChestIndex.Stack(itemId(s), s.getCount(), null, null)).toList();
        Component name = stack.getCustomName();
        return new ChestIndex.Stack(itemId(stack), stack.getCount(), name != null ? name.getString() : null, inner);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // Both halves of a double chest share one 54-slot menu.
    private static BlockPos otherHalf(BlockPos pos, BlockState state, int slots) {
        if (slots <= 27 || !(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }

    private static boolean isStorageMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
    }

    private static final Set<Block> INDEXABLE = Set.copyOf(Config.storageBlocks);

    private static boolean isIndexable(BlockState state) {
        return INDEXABLE.contains(state.getBlock());
    }

    private String dimension() {
        return mc.level.dimension().identifier().toString();
    }

    private static ChestIndex.Pos toPos(BlockPos pos) {
        return new ChestIndex.Pos(pos.getX(), pos.getY(), pos.getZ());
    }
}
