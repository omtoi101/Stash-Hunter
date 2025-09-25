package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.KeyHold;
import com.stashhunter.stashhunter.utils.ElytraController;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoEXP;
import meteordevelopment.meteorclient.systems.modules.movement.Scaffold;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class AutoElytraRepair extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> repairThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("repair-threshold")
        .description("Durability threshold to trigger repair (remaining durability).")
        .defaultValue(50)
        .min(1)
        .sliderMax(100)
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

    // Repair state
    private RepairState currentState = RepairState.MONITORING;
    private long repairStartTime = 0;
    private int currentRepairSlot = 0;
    private List<Integer> elytraSlots = new ArrayList<>();
    private boolean wasStashHunterActive = false;
    private int timer = 0;
    private boolean justFinishedRepairing = false;

    // Scaffold-repair specific state
    private double descentStartY = 0;
    private boolean scaffoldSetupDone = false;
    private boolean originalAutoPilotState = false;
    private boolean originalAutoHoverState = false;
    private boolean originalScaffoldAirPlace = false;
    private boolean originalScaffoldAutoSwitch = false;

    // NEW: deceleration wait state (added to allow ElytraFly to slow down before scaffold placement)
    private boolean decelWaiting = false;
    private boolean decelInitiated = false; // to ensure we only initiate deceleration wait once
    private int decelTimer = 0; // ticks to wait for deceleration

    // Movement detection for repair sequence
    private int stopWaitTimer = 0;
    private Vec3d lastPlayerPosition = null;
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
        resetRepairState();
        debugLog("AutoElytraRepair activated");
    }

    @Override
    public void onDeactivate() {
        if (currentState != RepairState.MONITORING) {
            resumeNormalOperation();
        }
        debugLog("AutoElytraRepair deactivated");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
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
        if (!ElytraController.isActive()) return;
        ItemStack chestSlot = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chestSlot.getItem() != Items.ELYTRA) return;

        if (needsRepair(chestSlot)) {
            initiateRepairSequence();
        }
    }

    private void handleStoppingAutopilot() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly == null) {
            error("ElytraFly not found! Aborting repair.");
            currentState = RepairState.EMERGENCY_DISCONNECT;
            return;
        }

        // Stop autopilot to allow player to slow down
        originalAutoPilotState = getModuleSetting(elytraFly, "autoPilot");
        Boolean takeoffState = getModuleSetting(elytraFly, "autoTakeOff");
        originalAutoTakeoffState = takeoffState != null ? takeoffState : false;
        setModuleSetting(elytraFly, "autoPilot", false);
        // Immediately release forward to stop autopilot movement without GUI
        mc.options.forwardKey.setPressed(false);
        // Clear ElytraFly internal forward flag if present
        try {
            Field modeField = elytraFly.getClass().getDeclaredField("currentMode");
            modeField.setAccessible(true);
            Object mode = modeField.get(elytraFly);
            if (mode != null) {
                try {
                    Field lastF = mode.getClass().getDeclaredField("lastForwardPressed");
                    lastF.setAccessible(true);
                    lastF.setBoolean(mode, false);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Throwable ignored) {}
        setModuleSetting(elytraFly, "autoTakeOff", true);

        info("Stopped autopilot. Waiting for player to stop moving...");
        currentState = RepairState.WAITING_FOR_STOP;
        lastPlayerPosition = mc.player.getPos();
        stationaryTicks = 0;
    }

    private void handleWaitingForStop() {
        Vec3d currentPos = mc.player.getPos();

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
                // Save/modify ElytraFly settings to slow the player down
                originalAutoHoverState = getModuleSetting(elytraFly, "autoHover");
                Boolean takeoffState2 = getModuleSetting(elytraFly, "autoTakeOff");
                originalAutoTakeoffState = takeoffState2 != null ? takeoffState2 : false;
                setModuleSetting(elytraFly, "autoHover", true);
                setModuleSetting(elytraFly, "autoPilot", false);
                // Ensure forward key is released now that autopilot is off
                mc.options.forwardKey.setPressed(false);
                try {
                    Field modeField = elytraFly.getClass().getDeclaredField("currentMode");
                    modeField.setAccessible(true);
                    Object mode = modeField.get(elytraFly);
                    if (mode != null) {
                        try {
                            Field lastF = mode.getClass().getDeclaredField("lastForwardPressed");
                            lastF.setAccessible(true);
                            lastF.setBoolean(mode, false);
                        } catch (NoSuchFieldException ignored) {}
                    }
                } catch (Throwable ignored) {}
                setModuleSetting(elytraFly, "autoTakeOff", true);

                // Give ElytraFly some time to slow the player down before enabling scaffold placement
                decelInitiated = true;
                decelWaiting = true;
                decelTimer = 20; // wait 20 ticks (~1 second). Adjust as needed.
                info("Set autoHover=true and autoPilot=false. Waiting " + decelTimer + " ticks for deceleration...");
                return;
            }

            // If we're waiting for deceleration, count down or check velocity
            if (decelWaiting) {
                // If player is essentially stopped, end wait early
                try {
                    double vsq = mc.player.getVelocity().lengthSquared();
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
            originalScaffoldAirPlace = getModuleSetting(scaffold, "airPlace");
            originalScaffoldAutoSwitch = getModuleSetting(scaffold, "autoSwitch");
            setModuleSetting(scaffold, "airPlace", true);
            setModuleSetting(scaffold, "autoSwitch", true);

            if (!scaffold.isActive()) scaffold.toggle();

            // Enable AutoEXP to allow XP bottles to smash on placed blocks
            AutoEXP autoExp = Modules.get().get(AutoEXP.class);
            if (autoExp != null && !autoExp.isActive()) {
                autoExp.toggle();
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
            error("Scaffold failed to place a block in time. Aborting repair.");
            currentState = RepairState.RESUMING_FLIGHT;
            return;
        }

        if (!mc.world.getBlockState(mc.player.getBlockPos().down()).isAir()) {
            info("Block placed successfully.");
            // Calculate safe hover altitude above the placed block (block top + 0.6)
            double blockTopY = mc.player.getBlockPos().down().getY() + 1;
            targetRepairAltitude = blockTopY + 0.6;
            positioningStartMs = System.currentTimeMillis();

            // Ensure elytra engaged before positioning
            if (!mc.player.isGliding()) {
                if (mc.player.isOnGround()) KeyHold.hold(mc.options.jumpKey, 4, null);
                try { mc.player.startGliding(); } catch (Exception ignored) {}
            }

            currentState = RepairState.POSITIONING_FOR_REPAIR;
        }
    }

    private void handlePositioningForRepair() {
        // Aim upward slightly to gain altitude until targetRepairAltitude or timeout (~2s)
        if (mc.player == null) return;
        double y = mc.player.getY();
        if (y >= targetRepairAltitude) {
            info("Reached repair hover altitude (" + String.format("%.2f", y) + ")");
            currentState = RepairState.REPAIRING;
            repairStartTime = System.currentTimeMillis();
            currentRepairSlot = 0;
            return;
        }

        // Timeout safety
        if (System.currentTimeMillis() - positioningStartMs > 2000L) {
            warning("Positioning timeout; proceeding with repair at current altitude.");
            currentState = RepairState.REPAIRING;
            repairStartTime = System.currentTimeMillis();
            currentRepairSlot = 0;
            return;
        }

        // Keep gliding and pitch up to climb gently
        if (!mc.player.isGliding()) {
            try { mc.player.startGliding(); } catch (Exception ignored) {}
        }
        mc.player.setPitch(-20);
    }

    private void handleRepairing() {
        Scaffold scaffold = Modules.get().get(Scaffold.class);
        if (scaffold != null && scaffold.isActive()) {
            scaffold.toggle();
            setModuleSetting(scaffold, "airPlace", originalScaffoldAirPlace);
            setModuleSetting(scaffold, "autoSwitch", originalScaffoldAutoSwitch);
            info("Scaffold disabled and settings restored.");
        }

        if (System.currentTimeMillis() - repairStartTime > repairTimeout.get() * 1000L) {
            warning("Repair timeout reached. Resuming flight.");
            currentState = RepairState.RESUMING_FLIGHT;
            return;
        }

        ItemStack chestElytra = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chestElytra.getItem() != Items.ELYTRA) {
            warning("No elytra equipped during repair.");
            currentState = RepairState.RESUMING_FLIGHT;
            return;
        }

        if (chestElytra.getDamage() == 0) {
            info("Equipped elytra repaired. Resuming flight.");
            currentState = RepairState.RESUMING_FLIGHT;
            return;
        }

        AutoEXP autoExp = Modules.get().get(AutoEXP.class);
        if (autoExp != null && !autoExp.isActive()) autoExp.toggle();
        currentState = RepairState.REPAIRING_IN_PROGRESS;
    }

    private void handleRepairingInProgress() {
        ItemStack currentElytra = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (currentElytra.getItem() != Items.ELYTRA) {
            warning("Elytra was unequipped during repair!");
            currentState = RepairState.REPAIRING;
            return;
        }

        if (currentElytra.getDamage() == 0) {
            info("Equipped elytra repaired.");
            currentState = RepairState.REPAIRING;
            return;
        }

        if (System.currentTimeMillis() - repairStartTime > repairTimeout.get() * 1000L) {
            warning("Repair timeout reached during repair-in-progress.");
            currentState = RepairState.RESUMING_FLIGHT;
        }
    }

    private void handleResumingFlight() {
        resumeNormalOperation();
        KeyHold.hold(mc.options.jumpKey, 10, null);
        mc.options.jumpKey.setPressed(false);
        KeyHold.hold(mc.options.jumpKey, 10, null);
        ElytraController.resume();
        info("Resuming flight.");
        justFinishedRepairing = true;
        final double CRUISE_ALTITUDE = 200.0;
        if (mc.player.getY() < CRUISE_ALTITUDE) {
            if (!climbingToCruise) {
                info("Climbing back to cruise altitude (Y=200), timeout 10s.");
                climbingToCruise = true;
                climbStartTimeMs = System.currentTimeMillis();
            }
            mc.player.setPitch(-30);
            if (originalAutoPilotState) {
                decelInitiated = true;
            }
            // Check timeout
            if (System.currentTimeMillis() - climbStartTimeMs > 10_000L) {
                warning("Climb to Y=200 timeout reached (10s). Continuing.");
                mc.player.setPitch(0);
                // Ensure autopilot resumes forward movement
                ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
                if (elytraFly != null && originalAutoPilotState) {
                    setModuleSetting(elytraFly, "autoPilot", true);
                }
                mc.options.forwardKey.setPressed(true);
                ElytraController.resume();
                resetRepairState();
                currentState = RepairState.MONITORING;
            }
        } else {
            info("Cruise altitude reached. Repair sequence complete.");
            mc.player.setPitch(0);
            // Ensure autopilot resumes forward movement
            ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
            if (elytraFly != null && originalAutoPilotState) {
                setModuleSetting(elytraFly, "autoPilot", true);
            }
            mc.options.forwardKey.setPressed(true);
            ElytraController.resume();
            resetRepairState();
            currentState = RepairState.MONITORING;
        }
    }

    private void handleEmergencyDisconnect() {
        resumeNormalOperation();
        error("Emergency disconnect initiated...");
        if (ElytraController.isActive()) ElytraController.pause();
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(net.minecraft.text.Text.of("Emergency disconnect: Elytra repair failed"));
        }
        this.toggle();
    }

    private void initiateRepairSequence() {
        wasStashHunterActive = ElytraController.isActive();
        descentStartY = mc.player.getY(); // retained for potential future use
        currentState = RepairState.STOPPING_AUTOPILOT;
        info("Elytra durability low. Stopping autopilot for repair sequence.");
    }

    private void initiateEmergencyDisconnect(String reason) {
        error("Initiating emergency disconnect: " + reason);
        currentState = RepairState.EMERGENCY_DISCONNECT;
    }

    private void resetRepairState() {
        repairStartTime = 0;
        currentRepairSlot = 0;
        elytraSlots.clear();
        wasStashHunterActive = false;
        timer = 0;
        justFinishedRepairing = false;
        descentStartY = 0;
        scaffoldSetupDone = false;
        originalAutoPilotState = true;
        originalAutoHoverState = true;
        originalScaffoldAirPlace = true;
        originalScaffoldAutoSwitch = true;
        decelWaiting = false;
        decelTimer = 0;
        stopWaitTimer = 0;
        lastPlayerPosition = null;
        stationaryTicks = 0;
        climbingToCruise = false;
        climbStartTimeMs = 0;
    }

    private void resumeNormalOperation() {
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null) {
            setModuleSetting(elytraFly, "autoPilot", originalAutoPilotState);
            setModuleSetting(elytraFly, "autoHover", originalAutoHoverState);
            setModuleSetting(elytraFly, "autoTakeOff", originalAutoTakeoffState);
        }

        Scaffold scaffold = Modules.get().get(Scaffold.class);
        if (scaffold != null && scaffold.isActive()) {
            scaffold.toggle();
            setModuleSetting(scaffold, "airPlace", originalScaffoldAirPlace);
            setModuleSetting(scaffold, "autoSwitch", originalScaffoldAutoSwitch);
        }

        AutoEXP autoExp = Modules.get().get(AutoEXP.class);
        if (autoExp != null && autoExp.isActive()) {
            autoExp.toggle();
        }
        debugLog("AutoElytraRepair cleanup finished.");
    }

    // Public Helpers for other modules
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
        return (elytra.getMaxDamage() - elytra.getDamage()) <= repairThreshold.get();
    }

    private void findElytraSlots() {
        elytraSlots.clear();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ELYTRA) elytraSlots.add(i);
        }
        info("Found " + elytraSlots.size() + " elytras in inventory");
    }

    private boolean hasRepairableElytras() {
        for (int slot : elytraSlots) {
            ItemStack elytra = mc.player.getInventory().getStack(slot);
            if (elytra.getItem() == Items.ELYTRA && elytra.getDamage() > 0) return true;
        }
        return false;
    }

    private void selectBestElytraAndEquip() {
        // Implementation from user's code
    }

    private boolean equipElytraToChestSlot(int slot) {
        // Implementation from user's code
        return true;
    }

    private void debugLog(String message) {
        if (debugMode.get()) {
            info("[DEBUG] " + message);
        }
    }

    // Reflection Helpers
    @SuppressWarnings("unchecked")
    private <T> T getModuleSetting(Module module, String settingName) {
        try {
            Field field = module.getClass().getDeclaredField(settingName);
            field.setAccessible(true);
            Object settingObj = field.get(module);
            if (settingObj instanceof Setting) {
                return ((Setting<T>) settingObj).get();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            debugLog("Reflection failed while getting setting '" + settingName + "': " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> void setModuleSetting(Module module, String settingName, T value) {
        try {
            Field field = module.getClass().getDeclaredField(settingName);
            field.setAccessible(true);
            Object settingObj = field.get(module);
            if (settingObj instanceof Setting) {
                ((Setting<T>) settingObj).set(value); // set() matches GUI behavior
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            debugLog("Reflection failed while setting '" + settingName + "': " + e.getMessage());
        }
    }
}
