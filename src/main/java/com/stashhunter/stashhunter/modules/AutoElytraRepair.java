package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.KeyHold;
import com.stashhunter.stashhunter.utils.ElytraController;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoEXP;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashSet;
import java.util.Set;

/**
 * Repairs the worn elytra on the ground, never in the air: the flight is paused, the player sinks
 * straight down with ElytraFly, AutoEXP repairs on the ground, then the flight takes off again.
 * A fast descent causes no fall damage: while gliding, the server resets the fall distance every
 * tick (its own deltaMovement of a player stays near 0; checked in the 26.2 bytecode).
 */
public class AutoElytraRepair extends Module {
    // Elytra loses 1 durability per second of flight. The descent takes (height above ground) /
    // (10 × ElytraFly vertical-speed) seconds, about 10 s from the default flight altitude, so the
    // threshold must keep it clear of CRITICAL_DURABILITY.
    private static final int MIN_REPAIR_THRESHOLD = 30;
    // Remaining durability at which further gliding is too risky.
    private static final int CRITICAL_DURABILITY = 10;
    // Stop repairing if the damage value has not dropped for this long (out of XP, AutoEXP done).
    private static final long REPAIR_STALL_TIMEOUT_MS = 5_000L;
    // Still in the air this long after the repair started: no safe ground found below.
    private static final long LANDING_TIMEOUT_MS = 90_000L;
    // Without Baritone the repair climbs back to cruise altitude itself; the flight resumes anyway after this.
    private static final long CLIMB_TIMEOUT_MS = 60_000L;
    // Ground the descent must not end on: it hurts, traps, or (pointed dripstone) adds fall damage.
    private static final Set<Block> HARMFUL_GROUND = Set.of(
        Blocks.MAGMA_BLOCK, Blocks.CACTUS, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.FIRE, Blocks.SOUL_FIRE,
        Blocks.SWEET_BERRY_BUSH, Blocks.WITHER_ROSE, Blocks.POWDER_SNOW, Blocks.COBWEB, Blocks.POINTED_DRIPSTONE
    );

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> repairThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("repair-threshold")
        .description("Durability threshold to trigger repair (remaining durability). The landing before the repair needs headroom.")
        .defaultValue(50)
        .min(MIN_REPAIR_THRESHOLD)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> swapToSpare = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-to-spare")
        .description("Before repairing, put on a spare elytra from the inventory that is above the repair threshold. Only lands to repair when there is none.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyRepairs = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-repairs")
        .description("Send Discord notifications during repair operations.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> repairTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("repair-timeout")
        .description("Maximum time to spend repairing on the ground in seconds.")
        .defaultValue(300)
        .min(60)
        .sliderMax(600)
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Enable debug logging for troubleshooting.")
        .defaultValue(false)
        .build()
    );

    // Repair state
    private RepairState currentState = RepairState.MONITORING;
    // Start of the current landing, repair or climb, for its timeout.
    private long phaseStartMs = 0;
    private boolean justFinishedRepairing = false;

    // ElytraFly in Vanilla mode moves the player only while a movement key is held: no keys hover,
    // sneak sinks straight down, jump climbs, forward moves on. Its settings are restored afterwards.
    private boolean originalAutoPilotState = false;
    private boolean originalAutoTakeoffState = false;
    private ElytraFlightModes originalFlightMode = ElytraFlightModes.Vanilla;
    private boolean elytraFlySettingsSaved = false; // only restore what was actually captured
    private boolean elytraFlyEnabledByRepair = false;
    private boolean autoExpEnabledByRepair = false;
    // Keys the repair holds down, so all of them can be let go when it ends.
    private final Set<KeyMapping> heldKeys = new HashSet<>();
    // Set by .stashhunter stop/pause during a repair: finish the repair, but leave the flight paused.
    private boolean keepFlightPaused = false;
    // Dimension the repair started in, to notice a portal/respawn into another one.
    private ResourceKey<Level> repairDimension = null;

    // Repair progress tracking
    private int lastRepairDamage = 0;
    private long lastRepairProgressMs = 0;

    // Set by handleEmergencyDisconnect; the module is toggled off on the next tick instead of
    // unsubscribing itself from inside the TickEvent it is currently handling.
    private boolean pendingDeactivate = false;

    private enum RepairState {
        MONITORING,
        LANDING,
        REPAIRING,
        TAKING_OFF,
        EMERGENCY_DISCONNECT
    }

    public AutoElytraRepair() {
        super(StashHunter.CATEGORY, "auto-elytra-repair", "Lands and repairs the elytra with XP bottles when it gets low on durability.");
    }

    @Override
    public void onActivate() {
        currentState = RepairState.MONITORING;
        pendingDeactivate = false;
        resetRepairState();
        debugLog("AutoElytraRepair activated");
    }

    @Override
    public void onDeactivate() {
        if (currentState != RepairState.MONITORING) {
            resumeNormalOperation();
            // Every repair state paused the flight; hand it back unless a disconnect is pending.
            if (isRepairing() && !keepFlightPaused && mc.player != null) ElytraController.resume();
            resetRepairState();
            currentState = RepairState.MONITORING;
        }
        debugLog("AutoElytraRepair deactivated");
    }

    // Kicked or disconnected mid-repair: the flight is paused already (trip saved), so only the
    // borrowed modules are restored. Otherwise the stale state would carry on after the next join.
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        abortRepair();
    }

    // Ends a repair without resuming the flight (it stays paused, its trip saved).
    private void abortRepair() {
        if (currentState != RepairState.MONITORING) resumeNormalOperation();
        resetRepairState();
        currentState = RepairState.MONITORING;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Runs before the null check: after an emergency disconnect there is no player anymore.
        if (pendingDeactivate) {
            pendingDeactivate = false;
            if (isActive()) toggle();
            return;
        }

        if (mc.player == null || mc.level == null) {
            return;
        }

        // Death or a portal mid-repair: StashHunterModule's halt can't stop a flight the repair has
        // paused already, so the repair must not resume it from the respawn point / other dimension.
        if (currentState != RepairState.MONITORING && currentState != RepairState.EMERGENCY_DISCONNECT
            && (mc.player.isDeadOrDying() || (repairDimension != null && !repairDimension.equals(mc.level.dimension())))) {
            warning("Repair aborted (death or dimension change). The flight stays paused - use .stashhunter resume.");
            abortRepair();
            return;
        }

        switch (currentState) {
            case MONITORING:
                handleMonitoring();
                break;
            case LANDING:
                handleLanding();
                break;
            case REPAIRING:
                handleRepairing();
                break;
            case TAKING_OFF:
                handleTakingOff();
                break;
            case EMERGENCY_DISCONNECT:
                handleEmergencyDisconnect();
                break;
        }
    }

    private void handleMonitoring() {
        // A Baritone landing (scan done, low rockets, repair impossible) still glides on this elytra.
        if (ElytraController.isLandingAfterCompletion()) {
            if (mc.player.isFallFlying() && isElytraCritical()) {
                if (swapToSpare.get() && swapToSpareElytra()) return;
                error("Elytra durability critical during the landing.");
                currentState = RepairState.EMERGENCY_DISCONNECT;
            }
            return;
        }
        if (!ElytraController.isActive()) return;
        ItemStack chestSlot = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestSlot.getItem() != Items.ELYTRA) return;

        if (needsRepair(chestSlot)) {
            if (swapToSpare.get() && swapToSpareElytra()) return;
            initiateRepairSequence();
        }
    }

    // One inventory move swaps the worn elytra out: no landing, no XP needed.
    private boolean swapToSpareElytra() {
        // With a chest or other container open, the inventory slot ids would hit that screen instead.
        if (mc.player.containerMenu != mc.player.inventoryMenu) return false;
        int best = -1;
        int bestRemaining = repairThreshold.get();
        var items = mc.player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.getItem() != Items.ELYTRA) continue;
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            if (remaining > bestRemaining) {
                best = i;
                bestRemaining = remaining;
            }
        }
        if (best == -1) return false;

        InvUtils.move().from(best).toArmor(2);
        info("Elytra durability low. Swapped to a spare elytra (" + bestRemaining + " durability).");
        notifyDiscord("Elytra Swapped", "Elytra durability low at " + mc.player.blockPosition().toShortString() + ". Put on a spare elytra with " + bestRemaining + " durability.", 0x00AAFF);
        return true;
    }

    private void initiateRepairSequence() {
        // AutoEXP pulls bottles from the whole inventory, so that is what has to be checked.
        if (!InvUtils.find(Items.EXPERIENCE_BOTTLE).found()) {
            cannotRepair("Elytra durability low, but no XP bottles in inventory. Cannot repair.");
            return;
        }
        // AutoEXP skips items without Mending, so the repair would only stall.
        if (!Utils.hasEnchantment(mc.player.getItemBySlot(EquipmentSlot.CHEST), Enchantments.MENDING)) {
            cannotRepair("Elytra durability low, but the elytra has no Mending. Cannot repair.");
            return;
        }

        // Pause the flight (also Baritone's elytra process) so it can't steer away from the repair
        // spot; the trip is saved and resumeFlight picks it up again.
        ElytraController.pause();
        repairDimension = mc.level.dimension();
        notifyDiscord("Elytra Repair Started", "Elytra durability low at " + mc.player.blockPosition().toShortString() + ". Landing to repair it.", 0xFFAA00);
        if (mc.player.onGround()) {
            info("Elytra durability low. Repairing on the ground.");
            startRepairing();
            return;
        }
        if (!borrowElytraFly()) return;
        info("Elytra durability low. Landing to repair it.");
        phaseStartMs = System.currentTimeMillis();
        currentState = RepairState.LANDING;
    }

    // The flight controller is paused, so ElytraFly is the only thing moving the player.
    private boolean borrowElytraFly() {
        if (elytraFlySettingsSaved) return true;
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly == null) {
            error("ElytraFly not found! Aborting repair.");
            currentState = RepairState.EMERGENCY_DISCONNECT;
            return false;
        }
        originalAutoPilotState = elytraFly.autoPilot.get();
        originalAutoTakeoffState = elytraFly.autoTakeOff.get();
        originalFlightMode = elytraFly.flightMode.get();
        elytraFlySettingsSaved = true;
        if (originalFlightMode != ElytraFlightModes.Vanilla) elytraFly.flightMode.set(ElytraFlightModes.Vanilla);
        elytraFly.autoPilot.set(false);
        mc.options.keyUp.setDown(false); // the forward key ElytraFly's autopilot may have pressed
        elytraFly.autoTakeOff.set(true);
        if (!elytraFly.isActive()) {
            elytraFly.toggle();
            elytraFlyEnabledByRepair = true;
        }
        return true;
    }

    private void handleLanding() {
        if (mc.player.onGround()) {
            releaseKeys();
            info("Landed. Repairing the elytra on the ground.");
            startRepairing();
            return;
        }
        if (System.currentTimeMillis() - phaseStartMs > LANDING_TIMEOUT_MS) {
            error("Could not land within " + LANDING_TIMEOUT_MS / 1000 + "s (no safe ground found below).");
            currentState = RepairState.EMERGENCY_DISCONNECT;
            return;
        }

        double groundY = safeGroundY();
        boolean safe = !Double.isNaN(groundY);
        // Sinking on beats disconnecting mid-air, but only if the elytra lasts until the ground.
        if (isElytraCritical() && !(safe && landsBeforeElytraFails(groundY))) {
            error("Elytra durability critical and no safe ground within reach. Aborting repair.");
            currentState = RepairState.EMERGENCY_DISCONNECT;
            return;
        }

        // Falling with a closed elytra (not opened yet, or the glide ended) would be no controlled descent.
        ElytraController.deployElytra();
        if (!safe && !heldKeys.contains(mc.options.keyUp)) info("No safe ground below (liquid, hazard or void). Moving on.");
        setHeld(mc.options.keyShift, safe);
        setHeld(mc.options.keyUp, !safe);
    }

    // Top of the ground straight below, or NaN if the descent must not end there: liquid, ground
    // that hurts, or no ground at all (the End's void).
    private double safeGroundY() {
        BlockPos.MutableBlockPos pos = mc.player.blockPosition().mutable();
        for (; pos.getY() >= mc.level.getMinY(); pos.move(Direction.DOWN)) {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || HARMFUL_GROUND.contains(state.getBlock())) return Double.NaN;
            VoxelShape shape = state.getCollisionShape(mc.level, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    // The elytra stops working at 1 durability and loses about 1 per second of gliding (Unbreaking
    // only stretches that). ElytraFly sinks 0.5 × vertical-speed blocks per tick while sneaking.
    private boolean landsBeforeElytraFails(double groundY) {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        ItemStack elytra = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        double blocksPerSecond = 10 * elytraFly.verticalSpeed.get();
        int secondsLeft = elytra.getMaxDamage() - elytra.getDamageValue() - 1;
        return blocksPerSecond > 0 && (mc.player.getY() - groundY) / blocksPerSecond < secondsLeft;
    }

    // On the ground the bottles break at the player's feet and the orbs reach them.
    private void startRepairing() {
        AutoEXP autoExp = Modules.get().get(AutoEXP.class);
        if (autoExp != null && !autoExp.isActive()) {
            autoExp.toggle();
            autoExpEnabledByRepair = true;
        }
        phaseStartMs = System.currentTimeMillis();
        lastRepairDamage = mc.player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
        lastRepairProgressMs = phaseStartMs;
        currentState = RepairState.REPAIRING;
    }

    private void handleRepairing() {
        ItemStack elytra = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (elytra.getItem() != Items.ELYTRA) {
            stayOnGround("Elytra was taken off during the repair.");
            return;
        }
        int damage = elytra.getDamageValue();
        if (damage == 0) {
            info("Elytra repaired.");
            startTakeoff();
            return;
        }

        long now = System.currentTimeMillis();
        if (damage < lastRepairDamage) {
            lastRepairDamage = damage;
            lastRepairProgressMs = now;
        }
        boolean stalled = now - lastRepairProgressMs > REPAIR_STALL_TIMEOUT_MS;
        boolean timedOut = now - phaseStartMs > repairTimeout.get() * 1000L;
        if (!stalled && !timedOut) return;

        // AutoEXP stops at its max-threshold (80 % by default), which is plenty to fly on.
        if (!needsRepair(elytra)) {
            info("Repair stopped at " + (elytra.getMaxDamage() - damage) + " durability, above the threshold.");
            startTakeoff();
        } else if (timedOut) {
            stayOnGround("Repair timeout reached.");
        } else {
            stayOnGround("Repair made no progress for " + REPAIR_STALL_TIMEOUT_MS / 1000 + "s (out of XP bottles?).");
        }
    }

    private void startTakeoff() {
        restoreAutoExp(); // bottles thrown in the air would only be lost
        if (keepFlightPaused) {
            resumeNormalOperation();
            info("Repair sequence ended. Flight stays stopped as requested.");
            resetRepairState();
            currentState = RepairState.MONITORING;
            return;
        }
        // Baritone's elytra process takes off from the ground by itself (rockets needed) and
        // climbs on its way to the next waypoint.
        if (ElytraController.isBaritoneFlight()) {
            resumeFlight();
            return;
        }
        if (!borrowElytraFly()) return;
        info("Taking off. Climbing back to cruise altitude (Y=" + Config.flightAltitude + ") first.");
        phaseStartMs = System.currentTimeMillis();
        currentState = RepairState.TAKING_OFF;
    }

    // Without Baritone the repair climbs back itself: in Vanilla mode ElytraFly only gains height
    // while jump is held, so the resumed flight would otherwise carry on just above the ground.
    private void handleTakingOff() {
        boolean reachedAltitude = mc.player.getY() >= Config.flightAltitude;
        boolean timedOut = System.currentTimeMillis() - phaseStartMs > CLIMB_TIMEOUT_MS;
        if (reachedAltitude || timedOut) {
            if (!reachedAltitude) warning("Climb to Y=" + Config.flightAltitude + " timed out (" + CLIMB_TIMEOUT_MS / 1000 + "s). Continuing at Y=" + (int) mc.player.getY() + ".");
            resumeFlight();
            return;
        }
        // On the ground jump gets the player airborne; deployElytra opens the elytra once they are.
        setHeld(mc.options.keyJump, true);
        ElytraController.deployElytra();
    }

    private void resumeFlight() {
        resumeNormalOperation();
        ElytraController.resume();
        if (ElytraController.isBaritoneFlight()) info("Resuming flight. Baritone takes off and climbs back to cruise altitude (Y=" + Config.flightAltitude + ").");
        else info("Resuming flight.");
        notifyDiscord("Elytra Repair Finished", "Repair sequence complete. Resuming flight.", 0x00FF00);
        resetRepairState();
        justFinishedRepairing = true; // after the reset, which clears it
        currentState = RepairState.MONITORING;
    }

    // On the ground the elytra can't fail mid-air: keep the flight paused (trip saved) instead of disconnecting.
    private void stayOnGround(String reason) {
        error(reason);
        warning("Flight paused on the ground. Restock and use .stashhunter resume.");
        notifyDiscord("Elytra Repair Failed - Paused", reason + " Flight paused on the ground at " + mc.player.blockPosition().toShortString() + ".", 0xFFAA00);
        abortRepair();
    }

    private void handleEmergencyDisconnect() {
        resumeNormalOperation();
        error("Emergency disconnect initiated...");
        notifyDiscord("Elytra Repair Failed", "Emergency disconnect: elytra repair failed.", 0xFF0000);
        if (ElytraController.isActive()) ElytraController.pause();
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Emergency disconnect: Elytra repair failed"));
        }
        resetRepairState();
        currentState = RepairState.MONITORING;
        pendingDeactivate = true;
    }

    // The elytra still has repair-threshold durability left: enough for Baritone to land nearby.
    // Only without Baritone (or if it can't take over) does it come to the emergency disconnect.
    private void cannotRepair(String reason) {
        error(reason);
        // Standing (e.g. Baritone about to take off again): nothing to land, the elytra can't break here.
        if (mc.player.onGround()) {
            ElytraController.pause();
            warning("Flight paused on the ground. Restock and use .stashhunter resume.");
            notifyDiscord("Elytra Repair Impossible - Paused", reason + " Flight paused on the ground.", 0xFFAA00);
            return;
        }
        if (ElytraController.landSafely()) {
            warning("Baritone is landing nearby instead. Restock and use .stashhunter resume.");
            notifyDiscord("Elytra Repair Impossible - Landing", reason + " Baritone is landing at a safe spot nearby.", 0xFFAA00);
        } else {
            currentState = RepairState.EMERGENCY_DISCONNECT;
        }
    }

    private void resetRepairState() {
        phaseStartMs = 0;
        justFinishedRepairing = false;
        originalAutoPilotState = false;
        originalAutoTakeoffState = false;
        originalFlightMode = ElytraFlightModes.Vanilla;
        elytraFlySettingsSaved = false;
        elytraFlyEnabledByRepair = false;
        autoExpEnabledByRepair = false;
        keepFlightPaused = false;
        repairDimension = null;
        lastRepairDamage = 0;
        lastRepairProgressMs = 0;
    }

    private void resumeNormalOperation() {
        releaseKeys();
        restoreElytraFly();
        restoreAutoExp();
        debugLog("AutoElytraRepair cleanup finished.");
    }

    private void restoreElytraFly() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null && elytraFlyEnabledByRepair && elytraFly.isActive()) {
            // Off before the flight controller resumes: ElytraFly would fight Baritone's elytra process.
            elytraFly.toggle();
        }
        elytraFlyEnabledByRepair = false;
        if (elytraFly != null && elytraFlySettingsSaved) {
            // Mode first: switching to Pitch40 resets autoPilot, so autoPilot is restored after it.
            elytraFly.flightMode.set(originalFlightMode);
            elytraFly.autoPilot.set(originalAutoPilotState);
            elytraFly.autoTakeOff.set(originalAutoTakeoffState);
        }
        elytraFlySettingsSaved = false;
    }

    // Only switch AutoEXP off if the repair switched it on; the player may run it on purpose.
    private void restoreAutoExp() {
        AutoEXP autoExp = Modules.get().get(AutoEXP.class);
        if (autoExp != null && autoExpEnabledByRepair && autoExp.isActive()) {
            autoExp.toggle();
        }
        autoExpEnabledByRepair = false;
    }

    // Refcounted via KeyHold, so another module's hold of the same key survives the release.
    private void setHeld(KeyMapping key, boolean hold) {
        if (hold == heldKeys.contains(key)) return;
        if (hold) {
            heldKeys.add(key);
            KeyHold.press(key);
        } else {
            heldKeys.remove(key);
            KeyHold.release(key);
        }
    }

    private void releaseKeys() {
        heldKeys.forEach(KeyHold::release);
        heldKeys.clear();
    }

    // Public Helpers for other modules

    /** The repair paused the flight; after it, the flight is not resumed (stop/pause command). */
    public void keepFlightPaused() {
        keepFlightPaused = true;
    }

    public boolean isRepairing() {
        return currentState != RepairState.MONITORING && currentState != RepairState.EMERGENCY_DISCONNECT;
    }

    public String getCurrentStateName() {
        return currentState.name();
    }

    public boolean justFinishedRepair() {
        if (justFinishedRepairing) {
            justFinishedRepairing = false;
            return true;
        }
        return false;
    }

    // Private helpers
    private boolean needsRepair(ItemStack elytra) {
        if (elytra.getItem() != Items.ELYTRA) return false;
        return (elytra.getMaxDamage() - elytra.getDamageValue()) <= repairThreshold.get();
    }

    private boolean isElytraCritical() {
        ItemStack elytra = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return elytra.getItem() == Items.ELYTRA
            && elytra.getMaxDamage() - elytra.getDamageValue() <= CRITICAL_DURABILITY;
    }

    private void notifyDiscord(String title, String description, int color) {
        if (notifyRepairs.get()) DiscordWebhook.sendMessage("", new DiscordEmbed(title, description, color));
    }

    private void debugLog(String message) {
        if (debugMode.get()) {
            info("[DEBUG] " + message);
        }
    }
}
