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
import meteordevelopment.meteorclient.systems.modules.movement.Scaffold;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class AutoElytraRepair extends Module {
    // Elytra loses 1 durability per second of flight. The stop/scaffold/positioning phases can
    // take ~15 s, so the threshold must keep the sequence clear of CRITICAL_DURABILITY.
    private static final int MIN_REPAIR_THRESHOLD = 30;
    // Remaining durability at which further gliding is too risky - abort to the safest option.
    private static final int CRITICAL_DURABILITY = 10;
    // Abort the repair if the damage value has not dropped for this long (out of XP, AutoEXP stuck).
    private static final long REPAIR_STALL_TIMEOUT_MS = 5_000L;
    // Scaffold placement tries before giving up (each try waits 2 s for a block below the player).
    private static final int MAX_SCAFFOLD_ATTEMPTS = 3;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> repairThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("repair-threshold")
        .description("Durability threshold to trigger repair (remaining durability).")
        .defaultValue(50)
        .min(MIN_REPAIR_THRESHOLD)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> swapToSpare = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-to-spare")
        .description("Before repairing, put on a spare elytra from the inventory that is above the repair threshold. Only repairs mid-air when there is none.")
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
        .description("Maximum time to spend repairing in seconds.")
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

    // "module/setting" pairs already reported as missing, so the warning is logged once.
    private final Set<String> missingSettingWarned = new HashSet<>();

    // Repair state
    private RepairState currentState = RepairState.MONITORING;
    private long repairStartTime = 0;
    private int timer = 0;
    private boolean justFinishedRepairing = false;

    // Scaffold-repair specific state
    private boolean scaffoldSetupDone = false;
    private int scaffoldAttempts = 0;
    private boolean originalAutoPilotState = false;
    private ElytraFlightModes originalFlightMode = ElytraFlightModes.Vanilla;
    private boolean elytraFlySettingsSaved = false; // only restore what was actually captured
    // ElytraFly (Vanilla mode, no keys held) zeroes horizontal speed: that is the hover during repair.
    private boolean elytraFlyEnabledByRepair = false;
    private boolean autoExpEnabledByRepair = false;
    // Set by .stashhunter stop/pause during a repair: finish the repair, but leave the flight paused.
    private boolean keepFlightPaused = false;
    // Dimension the repair started in, to notice a portal/respawn into another one.
    private ResourceKey<Level> repairDimension = null;
    private boolean originalScaffoldAirPlace = false;
    private boolean originalScaffoldAutoSwitch = false;
    // Only undo on Scaffold what the repair itself changed (the player may run it on purpose).
    private boolean scaffoldSettingsSaved = false;
    private boolean scaffoldEnabledByRepair = false;

    // NEW: deceleration wait state (added to allow ElytraFly to slow down before scaffold placement)
    private boolean decelWaiting = false;
    private boolean decelInitiated = false; // to ensure we only initiate deceleration wait once
    private int decelTimer = 0; // ticks to wait for deceleration

    // Movement detection for repair sequence
    private int stopWaitTimer = 0;
    private Vec3 lastPlayerPosition = null;
    private int stationaryTicks = 0;
    private static final int REQUIRED_STATIONARY_TICKS = 20; // 1 second of being stationary

    // Climb-to-altitude timeout tracking
    private boolean climbingToCruise = false;
    private long climbStartTimeMs = 0;

    // Track ElytraFly autoTakeoff original state
    private boolean originalAutoTakeoffState = false;

    // Positioning for repair (hover above placed block)
    private double targetRepairAltitude = 0;
    private long positioningStartMs = 0;

    // Repair progress tracking
    private int lastRepairDamage = 0;
    private long lastRepairProgressMs = 0;

    // Set by handleEmergencyDisconnect; the module is toggled off on the next tick instead of
    // unsubscribing itself from inside the TickEvent it is currently handling.
    private boolean pendingDeactivate = false;

    private enum RepairState {
        MONITORING,
        STOPPING_AUTOPILOT,
        WAITING_FOR_STOP,
        POSITIONING_FOR_REPAIR,
        SCAFFOLDING,
        REPAIRING,
        REPAIRING_IN_PROGRESS,
        RESUMING_FLIGHT,
        EMERGENCY_DISCONNECT
    }

    public AutoElytraRepair() {
        super(StashHunter.CATEGORY, "auto-elytra-repair", "Automatically repairs elytras when they get low on durability.");
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
        KeyHold.cancel(this);
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
        KeyHold.cancel(this);
        if (currentState != RepairState.MONITORING) resumeNormalOperation();
        resetRepairState();
        currentState = RepairState.MONITORING;
        repairDimension = null;
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
            case STOPPING_AUTOPILOT:
                handleStoppingAutopilot();
                break;
            case WAITING_FOR_STOP:
                handleWaitingForStop();
                break;
            case POSITIONING_FOR_REPAIR:
                handlePositioningForRepair();
                break;
            case SCAFFOLDING:
                handleScaffolding();
                break;
            case REPAIRING:
                handleRepairing();
                break;
            case REPAIRING_IN_PROGRESS:
                handleRepairingInProgress();
                break;
            case RESUMING_FLIGHT:
                handleResumingFlight();
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

    // One inventory move swaps the worn elytra out: no stop, no scaffold, no XP needed.
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

    private void handleStoppingAutopilot() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly == null) {
            error("ElytraFly not found! Aborting repair.");
            currentState = RepairState.EMERGENCY_DISCONNECT;
            return;
        }

        // The flight controller is paused (initiateRepairSequence). ElytraFly in Vanilla mode with
        // no movement keys held keeps the glide but zeroes horizontal speed, so the player hovers.
        originalAutoPilotState = elytraFly.autoPilot.get();
        originalAutoTakeoffState = elytraFly.autoTakeOff.get();
        originalFlightMode = elytraFly.flightMode.get();
        elytraFlySettingsSaved = true;
        if (originalFlightMode != ElytraFlightModes.Vanilla) elytraFly.flightMode.set(ElytraFlightModes.Vanilla);
        elytraFly.autoPilot.set(false);
        mc.options.keyUp.setDown(false);
        elytraFly.autoTakeOff.set(true);
        if (!elytraFly.isActive()) {
            elytraFly.toggle();
            elytraFlyEnabledByRepair = true;
        }

        info("Flight paused, ElytraFly hovering. Waiting for player to stop moving...");
        currentState = RepairState.WAITING_FOR_STOP;
        lastPlayerPosition = mc.player.position();
        stationaryTicks = 0;
    }

    private void handleWaitingForStop() {
        if (abortIfElytraCritical()) return;

        Vec3 currentPos = mc.player.position();

        // Check if player has moved significantly
        if (lastPlayerPosition != null) {
            double distance = currentPos.distanceTo(lastPlayerPosition);
            if (distance > 0.1) { // Player is still moving
                stationaryTicks = 0;
                lastPlayerPosition = currentPos;
                debugLog("Player still moving, distance: " + String.format("%.2f", distance));
            } else {
                stationaryTicks++;
                debugLog("Player stationary for " + stationaryTicks + " ticks");
            }
        } else {
            lastPlayerPosition = currentPos;
        }

        // Check if player has been stationary long enough
        if (stationaryTicks >= REQUIRED_STATIONARY_TICKS) {
            info("Player has stopped moving. Proceeding with repair sequence.");
            currentState = RepairState.SCAFFOLDING;
            lastPlayerPosition = null;
            stationaryTicks = 0;
        }

        // Timeout after 10 seconds
        stopWaitTimer++;
        if (stopWaitTimer > 200) { // 10 seconds at 20 TPS
            warning("Timeout waiting for player to stop. Proceeding anyway.");
            currentState = RepairState.SCAFFOLDING;
            lastPlayerPosition = null;
            stationaryTicks = 0;
            stopWaitTimer = 0;
        }
    }

    private void handleScaffolding() {
        if (abortIfElytraCritical()) return;

        // If we recently flipped autoHover/autoPilot, wait for deceleration to complete before placing blocks
        // Use decelInitiated to ensure we only initiate the deceleration wait once.
        if (!scaffoldSetupDone) {
            ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
            Scaffold scaffold = Modules.get().get(Scaffold.class);
            if (elytraFly == null || scaffold == null) {
                error("Required modules not found. Aborting.");
                currentState = RepairState.EMERGENCY_DISCONNECT;
                return;
            }

            // If we haven't started the deceleration sequence yet, do so once.
            if (!decelInitiated) {
                mc.options.keyUp.setDown(false);

                // Give ElytraFly some time to slow the player down before enabling scaffold placement
                decelInitiated = true;
                decelWaiting = true;
                decelTimer = 20; // wait 20 ticks (~1 second). Adjust as needed.
                info("Waiting " + decelTimer + " ticks for deceleration...");
                return;
            }

            // If we're waiting for deceleration, count down or check velocity
            if (decelWaiting) {
                // If player is essentially stopped, end wait early
                try {
                    double vsq = mc.player.getDeltaMovement().lengthSqr();
                    if (vsq < 0.0001) {
                        // Player is stopped
                        decelWaiting = false;
                        info("Player velocity low — deceleration detected. Proceeding with scaffold setup.");
                    }
                } catch (Exception e) {
                    // Fallback to tick-based waiting if velocity check fails for any reason
                }

                // Tick-based countdown
                decelTimer--;
                if (decelTimer <= 0) {
                    decelWaiting = false;
                    info("Deceleration wait complete. Proceeding with scaffold setup.");
                } else {
                    debugLog("Deceleration wait: " + decelTimer + " ticks remaining...");
                    return;
                }
            }

            // Now actually enable scaffold and auto exp together
            originalScaffoldAirPlace = readBool(scaffold, "air-place", false);
            originalScaffoldAutoSwitch = readBool(scaffold, "auto-switch", true);
            scaffoldSettingsSaved = true;
            writeBool(scaffold, "air-place", true);
            writeBool(scaffold, "auto-switch", true);

            if (!scaffold.isActive()) {
                scaffold.toggle();
                scaffoldEnabledByRepair = true;
            }

            // Enable AutoEXP to allow XP bottles to smash on placed blocks
            AutoEXP autoExp = Modules.get().get(AutoEXP.class);
            if (autoExp != null && !autoExp.isActive()) {
                autoExp.toggle();
                autoExpEnabledByRepair = true;
                info("Enabled AutoEXP for repair operations");
            }

            info("Scaffold and AutoEXP enabled. Waiting for block placement...");
            timer = 40;
            scaffoldSetupDone = true;
            // reset decel flags so the next repair sequence will re-initiate properly
            decelInitiated = false;
            decelWaiting = false;
            return;
        }

        // After scaffold is set up, monitor block placement and timeout
        timer--;
        if (timer <= 0) {
            // Resuming flight below the threshold would only restart the repair right away.
            scaffoldAttempts++;
            if (scaffoldAttempts < MAX_SCAFFOLD_ATTEMPTS) {
                warning("Scaffold failed to place a block in time. Retrying (" + scaffoldAttempts + "/" + MAX_SCAFFOLD_ATTEMPTS + ")...");
                timer = 40;
            } else {
                error("Scaffold failed to place a block " + MAX_SCAFFOLD_ATTEMPTS + " times (no blocks in the hotbar?).");
                currentState = RepairState.EMERGENCY_DISCONNECT;
            }
            return;
        }

        if (!mc.level.getBlockState(mc.player.blockPosition().below()).isAir()) {
            info("Block placed successfully.");
            // Calculate safe hover altitude above the placed block (block top + 0.6)
            double blockTopY = mc.player.blockPosition().below().getY() + 1;
            targetRepairAltitude = blockTopY + 0.6;
            positioningStartMs = System.currentTimeMillis();

            // Ensure elytra engaged before positioning
            ElytraController.deployElytra();

            currentState = RepairState.POSITIONING_FOR_REPAIR;
        }
    }

    private void handlePositioningForRepair() {
        // Aim upward slightly to gain altitude until targetRepairAltitude or timeout (~2s)
        if (mc.player == null) return;

        // The block is already placed below us, so skip the climb and repair right here.
        if (isElytraCritical()) {
            warning("Elytra durability critical. Skipping positioning and repairing at current altitude.");
            currentState = RepairState.REPAIRING;
            repairStartTime = System.currentTimeMillis();
            return;
        }

        double y = mc.player.getY();
        if (y >= targetRepairAltitude) {
            info("Reached repair hover altitude (" + String.format("%.2f", y) + ")");
            currentState = RepairState.REPAIRING;
            repairStartTime = System.currentTimeMillis();
            return;
        }

        // Timeout safety
        if (System.currentTimeMillis() - positioningStartMs > 2000L) {
            warning("Positioning timeout; proceeding with repair at current altitude.");
            currentState = RepairState.REPAIRING;
            repairStartTime = System.currentTimeMillis();
            return;
        }

        // Keep gliding and pitch up to climb gently
        ElytraController.deployElytra();
        mc.player.setXRot(-20);
    }

    private void handleRepairing() {
        if (scaffoldSettingsSaved) {
            restoreScaffold();
            info("Scaffold settings restored.");
        }

        if (System.currentTimeMillis() - repairStartTime > repairTimeout.get() * 1000L) {
            warning("Repair timeout reached. Resuming flight.");
            currentState = RepairState.RESUMING_FLIGHT;
            return;
        }

        ItemStack chestElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestElytra.getItem() != Items.ELYTRA) {
            warning("No elytra equipped during repair.");
            currentState = RepairState.RESUMING_FLIGHT;
            return;
        }

        if (chestElytra.getDamageValue() == 0) {
            info("Equipped elytra repaired. Resuming flight.");
            currentState = RepairState.RESUMING_FLIGHT;
            return;
        }

        AutoEXP autoExp = Modules.get().get(AutoEXP.class);
        if (autoExp != null && !autoExp.isActive()) {
            autoExp.toggle();
            autoExpEnabledByRepair = true;
        }
        lastRepairDamage = chestElytra.getDamageValue();
        lastRepairProgressMs = System.currentTimeMillis();
        currentState = RepairState.REPAIRING_IN_PROGRESS;
    }

    private void handleRepairingInProgress() {
        ItemStack currentElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (currentElytra.getItem() != Items.ELYTRA) {
            warning("Elytra was unequipped during repair!");
            currentState = RepairState.REPAIRING;
            return;
        }

        if (currentElytra.getDamageValue() == 0) {
            info("Equipped elytra repaired.");
            currentState = RepairState.REPAIRING;
            return;
        }

        if (System.currentTimeMillis() - repairStartTime > repairTimeout.get() * 1000L) {
            warning("Repair timeout reached during repair-in-progress.");
            currentState = RepairState.RESUMING_FLIGHT;
            return;
        }

        int damage = currentElytra.getDamageValue();
        if (damage < lastRepairDamage) {
            lastRepairDamage = damage;
            lastRepairProgressMs = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lastRepairProgressMs > REPAIR_STALL_TIMEOUT_MS) {
            // Resuming below the threshold would immediately restart the same stalled repair.
            if (needsRepair(currentElytra)) {
                error("Repair made no progress for " + REPAIR_STALL_TIMEOUT_MS / 1000 + "s (out of XP bottles?).");
                currentState = RepairState.EMERGENCY_DISCONNECT;
            } else {
                warning("Repair stalled, but durability is above the threshold. Resuming flight.");
                currentState = RepairState.RESUMING_FLIGHT;
            }
        }
    }

    private void handleResumingFlight() {
        if (!climbingToCruise) {
            resumeNormalOperation();
            if (keepFlightPaused) {
                info("Repair sequence ended. Flight stays stopped as requested.");
                resetRepairState();
                currentState = RepairState.MONITORING;
                return;
            }
            KeyHold.hold(this, mc.options.keyJump, 10, null);
            ElytraController.resume();
            if (ElytraController.isBaritoneFlight()) info("Resuming flight. Baritone climbs back to cruise altitude (Y=" + Config.flightAltitude + ").");
            else info("Resuming flight. Climbing back to cruise altitude (Y=" + Config.flightAltitude + "), timeout 10s.");
            climbingToCruise = true;
            climbStartTimeMs = System.currentTimeMillis();
        }

        // Baritone climbs on its way to the next waypoint; pitching the view would fight its steering.
        boolean baritoneClimbs = ElytraController.isBaritoneFlight();
        boolean reachedAltitude = mc.player.getY() >= Config.flightAltitude;
        boolean timedOut = System.currentTimeMillis() - climbStartTimeMs > 10_000L;
        if (!reachedAltitude && !timedOut && !baritoneClimbs) {
            mc.player.setXRot(-30);
            return;
        }

        notifyDiscord("Elytra Repair Finished", "Repair sequence complete. Resuming flight.", 0x00FF00);
        if (reachedAltitude || baritoneClimbs) info("Repair sequence complete.");
        else warning("Climb to Y=" + Config.flightAltitude + " timeout reached (10s). Continuing.");

        if (!baritoneClimbs) mc.player.setXRot(0);
        // Forward movement is the resumed flight controller's job (it releases the key it holds).
        resetRepairState();
        justFinishedRepairing = true; // after the reset, which clears it
        currentState = RepairState.MONITORING;
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
        // spot; the trip is saved and handleResumingFlight picks it up again.
        ElytraController.pause();
        repairDimension = mc.level.dimension();
        currentState = RepairState.STOPPING_AUTOPILOT;
        info("Elytra durability low. Stopping autopilot for repair sequence.");
        notifyDiscord("Elytra Repair Started", "Elytra durability low at " + mc.player.blockPosition().toShortString() + ". Starting repair sequence.", 0xFFAA00);
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
        repairStartTime = 0;
        timer = 0;
        justFinishedRepairing = false;
        scaffoldSetupDone = false;
        scaffoldAttempts = 0;
        originalAutoPilotState = true;
        originalFlightMode = ElytraFlightModes.Vanilla;
        elytraFlySettingsSaved = false;
        elytraFlyEnabledByRepair = false;
        autoExpEnabledByRepair = false;
        keepFlightPaused = false;
        originalScaffoldAirPlace = true;
        originalScaffoldAutoSwitch = true;
        scaffoldSettingsSaved = false;
        scaffoldEnabledByRepair = false;
        decelWaiting = false;
        decelTimer = 0;
        stopWaitTimer = 0;
        lastPlayerPosition = null;
        stationaryTicks = 0;
        climbingToCruise = false;
        climbStartTimeMs = 0;
        lastRepairDamage = 0;
        lastRepairProgressMs = 0;
    }

    private void resumeNormalOperation() {
        mc.options.keyShift.setDown(false);
        mc.options.keyUp.setDown(false);

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

        restoreScaffold();

        // Only switch AutoEXP off if the repair switched it on; the player may run it on purpose.
        AutoEXP autoExp = Modules.get().get(AutoEXP.class);
        if (autoExp != null && autoExpEnabledByRepair && autoExp.isActive()) {
            autoExp.toggle();
        }
        autoExpEnabledByRepair = false;
        debugLog("AutoElytraRepair cleanup finished.");
    }

    private void restoreScaffold() {
        Scaffold scaffold = Modules.get().get(Scaffold.class);
        if (scaffold != null) {
            if (scaffoldEnabledByRepair && scaffold.isActive()) scaffold.toggle();
            if (scaffoldSettingsSaved) {
                writeBool(scaffold, "air-place", originalScaffoldAirPlace);
                writeBool(scaffold, "auto-switch", originalScaffoldAutoSwitch);
            }
        }
        scaffoldEnabledByRepair = false;
        scaffoldSettingsSaved = false;
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

    // Used while still gliding without a block below: disconnecting beats the elytra breaking mid-air.
    private boolean abortIfElytraCritical() {
        if (!isElytraCritical()) return false;
        error("Elytra durability critical before a block could be placed. Aborting repair.");
        currentState = RepairState.EMERGENCY_DISCONNECT;
        return true;
    }

    private void notifyDiscord(String title, String description, int color) {
        if (notifyRepairs.get()) DiscordWebhook.sendMessage("", new DiscordEmbed(title, description, color));
    }

    private void debugLog(String message) {
        if (debugMode.get()) {
            info("[DEBUG] " + message);
        }
    }

    // Scaffold keeps these settings private; Meteor's settings lookup by name is the public way in.
    private boolean readBool(Module module, String name, boolean fallback) {
        Setting<Boolean> setting = boolSetting(module, name);
        return setting != null ? setting.get() : fallback;
    }

    private void writeBool(Module module, String name, boolean value) {
        Setting<Boolean> setting = boolSetting(module, name);
        if (setting != null) setting.set(value); // set() matches GUI behavior
    }

    @SuppressWarnings("unchecked")
    private Setting<Boolean> boolSetting(Module module, String name) {
        Setting<?> setting = module.settings.get(name);
        if (setting != null && setting.get() instanceof Boolean) return (Setting<Boolean>) setting;
        if (missingSettingWarned.add(module.name + "/" + name)) {
            StashHunter.LOG.warn("{} has no boolean setting '{}' - Meteor changed it, auto repair can't adjust it", module.name, name);
        }
        return null;
    }
}
