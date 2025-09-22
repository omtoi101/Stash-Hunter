package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.utils.Config;
import com.stashhunter.stashhunter.utils.DiscordEmbed;
import com.stashhunter.stashhunter.utils.DiscordWebhook;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.SafeLandingSpotFinder;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
// import meteordevelopment.meteorclient.systems.modules.misc.AutoTool; // Not found in project
// import meteordevelopment.meteorclient.systems.modules.player.AutoExp; // Not found in project
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import com.stashhunter.stashhunter.mixin.PlayerInventoryAccessor;

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

    private final Setting<Integer> landingRadius = sgGeneral.add(new IntSetting.Builder()
        .name("landing-radius")
        .description("Radius to search for safe landing spots.")
        .defaultValue(64)
        .min(16)
        .sliderMax(128)
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

    // Repair state
    private RepairState currentState = RepairState.MONITORING;
    private BlockPos targetLandingSpot = null;
    private Vec3d resumePosition = null;
    private long repairStartTime = 0;
    private int currentRepairSlot = 0;
    private List<Integer> elytraSlots = new ArrayList<>();
    private boolean wasStashHunterActive = false;
    private int landingAttempts = 0;
    private static final int MAX_LANDING_ATTEMPTS = 3;
    private int timer = 0;
    private int resumingStep = 0;
    private RepairState nextState = null;

    private enum RepairState {
        MONITORING,          // Normal operation, checking elytra durability
        FINDING_LANDING,     // Looking for safe landing spot
        DESCENDING,          // Flying to landing spot
        LANDING,             // Final landing approach
        REPAIRING,           // On ground, cycling through elytras for repair
        RESUMING,            // Taking off and resuming flight
        EMERGENCY_DISCONNECT, // Critical failure, preparing to disconnect
        WAITING
    }

    public AutoElytraRepair() {
        super(StashHunter.CATEGORY, "auto-elytra-repair", "Automatically repairs elytras when they get low on durability.");
    }

    @Override
    public void onActivate() {
        currentState = RepairState.MONITORING;
        resetRepairState();
    }

    @Override
    public void onDeactivate() {
        if (currentState != RepairState.MONITORING) {
            // Emergency cleanup
            resumeNormalOperation();
        }
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
            case FINDING_LANDING:
                handleFindingLanding();
                break;
            case DESCENDING:
                handleDescending();
                break;
            case LANDING:
                handleLanding();
                break;
            case REPAIRING:
                handleRepairing();
                break;
            case RESUMING:
                handleResuming();
                break;
            case EMERGENCY_DISCONNECT:
                handleEmergencyDisconnect();
                break;
            case WAITING:
                handleWaiting();
                break;
        }
    }

    private void handleWaiting() {
        if (timer > 0) {
            timer--;
        } else {
            if (currentState == RepairState.WAITING && nextState == RepairState.REPAIRING) {
                mc.options.useKey.setPressed(false);
            }
            currentState = nextState;
            nextState = null;
        }
    }

    private void handleMonitoring() {
        if (!ElytraController.isActive()) {
            return; // Only monitor when stash hunter is active
        }

        ItemStack chestSlot = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chestSlot.getItem() != Items.ELYTRA) {
            return; // No elytra equipped
        }

        // Check current elytra durability
        if (needsRepair(chestSlot)) {
            // Find all elytras in inventory
            findElytraSlots();

            if (elytraSlots.isEmpty()) {
                // No elytras to repair with - emergency disconnect
                error("No elytras found in inventory for repair!");
                initiateEmergencyDisconnect("No backup elytras available");
                return;
            }

            // Check if any elytras can be repaired
            if (!hasRepairableElytras()) {
                error("All elytras are too damaged to repair!");
                initiateEmergencyDisconnect("All elytras beyond repair threshold");
                return;
            }

            info("Elytra needs repair (durability: " + (chestSlot.getMaxDamage() - chestSlot.getDamage()) +
                 "/" + chestSlot.getMaxDamage() + "). Initiating repair sequence.");

            if (notifyRepairs.get() && !Config.discordWebhookUrl.isEmpty()) {
                DiscordEmbed embed = new DiscordEmbed(
                    "Elytra Repair Initiated",
                    "Current elytra durability low. Starting repair sequence.\n" +
                    "Position: " + mc.player.getBlockPos().toShortString() + "\n" +
                    "Available elytras: " + elytraSlots.size(),
                    0xFFAA00
                );
                DiscordWebhook.sendMessage("", embed);
            }

            initiateRepairSequence();
        }
    }

    private void handleFindingLanding() {
        Vec3d playerPos = mc.player.getPos();

        // Calculate search radius based on attempts
        int currentRadius = landingRadius.get() * (landingAttempts + 1);

        // Find safe landing spot
        BlockPos landingSpot = SafeLandingSpotFinder.findLandingSpot(
            playerPos, currentRadius, mc.world, false
        );

        if (landingSpot != null) {
            targetLandingSpot = landingSpot;
            resumePosition = playerPos; // Remember where we were
            currentState = RepairState.DESCENDING;
            info("Found safe landing spot at " + landingSpot.toShortString());
            landingAttempts = 0; // Reset for next time
        } else {
            landingAttempts++;
            info("Could not find landing spot, expanding search radius to " + (landingRadius.get() * (landingAttempts + 1)) + " blocks...");
            // Continue searching with expanded radius next tick
        }
    }

    private void handleDescending() {
        if (targetLandingSpot == null) {
            currentState = RepairState.FINDING_LANDING;
            return;
        }

        Vec3d playerPos = mc.player.getPos();
        double horizontalDistance = Math.sqrt(
            Math.pow(targetLandingSpot.getX() - playerPos.x, 2) +
            Math.pow(targetLandingSpot.getZ() - playerPos.z, 2)
        );

        // Navigate to landing spot
        if (horizontalDistance > 5.0) {
            // Fly toward landing spot
            flyToPosition(new Vec3d(targetLandingSpot.getX(), playerPos.y, targetLandingSpot.getZ()));
        } else {
            // Close enough horizontally, start final descent
            currentState = RepairState.LANDING;
            info("Beginning final landing approach...");
        }
    }

    private void handleLanding() {
        if (targetLandingSpot == null) {
            currentState = RepairState.FINDING_LANDING;
            return;
        }

        Vec3d playerPos = mc.player.getPos();
        double groundDistance = playerPos.y - (targetLandingSpot.getY() + 1);

        if (mc.player.isOnGround()) {
            // Successfully landed
            info("Successfully landed at " + mc.player.getBlockPos().toShortString());
            currentState = RepairState.REPAIRING;
            repairStartTime = System.currentTimeMillis();
            currentRepairSlot = 0;

            // Enable AutoExp module
            // AutoExp autoExp = Modules.get().get(AutoExp.class);
            // if (autoExp != null && !autoExp.isActive()) {
            //     autoExp.toggle();
            //     info("Enabled AutoExp for repair operations");
            // }
        } else if (groundDistance > 10) {
            // Continue descending
            Vec3d landingPos = new Vec3d(targetLandingSpot.getX(), targetLandingSpot.getY() + 2, targetLandingSpot.getZ());
            flyToPosition(landingPos);

            // Stop gliding when close to ground for safer landing
            if (groundDistance < 5 && mc.player.isGliding()) {
                mc.player.stopGliding();
            }
        } else {
            // Very close to ground, let gravity handle it
            if (mc.player.isGliding()) {
                mc.player.stopGliding();
            }
        }
    }

    private void handleRepairing() {
        // Check for timeout
        if (System.currentTimeMillis() - repairStartTime > repairTimeout.get() * 1000L) {
            warning("Repair timeout reached. Resuming flight with current elytra.");
            currentState = RepairState.RESUMING;
            return;
        }

        if (currentRepairSlot >= elytraSlots.size()) {
            // Finished cycling through all elytras
            info("Completed repair cycle. Selecting best elytra and resuming flight.");
            selectBestElytra();
            currentState = RepairState.RESUMING;
            return;
        }

        // Get current elytra in repair slot
        int slotIndex = elytraSlots.get(currentRepairSlot);
        ItemStack elytra = mc.player.getInventory().getStack(slotIndex);

        if (elytra.getItem() != Items.ELYTRA) {
            // Elytra was moved or consumed, skip this slot
            currentRepairSlot++;
            return;
        }

        // Check if this elytra is fully repaired or good enough
        if (!needsRepair(elytra)) {
            info("Elytra in slot " + slotIndex + " is sufficiently repaired");
            currentRepairSlot++;

            // Wait a bit before moving to next elytra
            timer = 20; // Wait 1 second (20 ticks)
            currentState = RepairState.WAITING;
            nextState = RepairState.REPAIRING;
        } else {
            // Find and use experience bottles
            int bottleSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.EXPERIENCE_BOTTLE) {
                    bottleSlot = i;
                    break;
                }
            }

            if (bottleSlot != -1) {
                selectHotbarSlot(bottleSlot);
                mc.player.setPitch(90);
                mc.options.useKey.setPressed(true);
                timer = 5; // Wait 0.25 seconds
                currentState = RepairState.WAITING;
                nextState = RepairState.REPAIRING;
            } else {
                warning("No experience bottles found. Cannot repair elytra.");
                currentState = RepairState.RESUMING;
            }
        }
    }

    private void handleResuming() {
        // Disable AutoExp
        // AutoExp autoExp = Modules.get().get(AutoExp.class);
        // if (autoExp != null && autoExp.isActive()) {
        //     autoExp.toggle();
        //     info("Disabled AutoExp after repair completion");
        // }

        // Ensure best elytra is equipped
        selectBestElytra();

        // Take off and resume flight
        switch (resumingStep) {
            case 0:
                // Jump to start takeoff
                mc.options.jumpKey.setPressed(true);
                timer = 10; // Wait 0.5 seconds
                currentState = RepairState.WAITING;
                nextState = RepairState.RESUMING;
                resumingStep++;
                break;
            case 1:
                mc.options.jumpKey.setPressed(false);
                timer = 20; // Wait 1 second
                currentState = RepairState.WAITING;
                nextState = RepairState.RESUMING;
                resumingStep++;
                break;
            case 2:
                if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                    mc.player.startGliding();
                    info("Restarted elytra gliding");
                }
                timer = 40; // Wait 2 seconds
                currentState = RepairState.WAITING;
                nextState = RepairState.RESUMING;
                resumingStep++;
                break;
            case 3:
                // Re-enable ElytraFly module
                ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
                if (elytraFly != null && !elytraFly.isActive()) {
                    elytraFly.toggle();
                    info("Re-enabled ElytraFly module");
                }
                resumingStep = 0;
                currentState = RepairState.MONITORING;
                break;
        }

        // Resume stash hunter if it was active
        if (wasStashHunterActive && !ElytraController.isActive()) {
            // ElytraController will resume automatically when conditions are right
            info("Repair sequence complete. Stash hunter will resume automatically.");
        }

        if (notifyRepairs.get() && !Config.discordWebhookUrl.isEmpty()) {
            DiscordEmbed embed = new DiscordEmbed(
                "Elytra Repair Complete",
                "Successfully repaired elytras and resumed flight.\n" +
                "Position: " + mc.player.getBlockPos().toShortString() + "\n" +
                "Status: Resuming stash hunting operations",
                0x00FF00
            );
            DiscordWebhook.sendMessage("", embed);
        }

        resetRepairState();
        currentState = RepairState.MONITORING;
    }

    private void handleEmergencyDisconnect() {
        error("Emergency disconnect initiated. Saving state and disconnecting...");

        // Save current trip state
        if (ElytraController.isActive()) {
            ElytraController.pause();
        }

        if (notifyRepairs.get() && !Config.discordWebhookUrl.isEmpty()) {
            DiscordEmbed embed = new DiscordEmbed(
                "Emergency Disconnect",
                "Critical elytra repair failure. Bot is disconnecting for safety.\n" +
                "Position: " + (mc.player != null ? mc.player.getBlockPos().toShortString() : "Unknown") + "\n" +
                "Reason: Unable to repair elytras safely",
                0xFF0000
            );
            DiscordWebhook.sendMessage("@everyone", embed);
        }

        // Disconnect from server
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(
                net.minecraft.text.Text.of("Emergency disconnect: Elytra repair failed")
            );
        }

        this.toggle(); // Disable the module
    }

    private void initiateRepairSequence() {
        wasStashHunterActive = ElytraController.isActive();

        // Pause stash hunter
        if (ElytraController.isActive()) {
            ElytraController.pause();
            info("Paused stash hunter for elytra repair");
        }

        currentState = RepairState.FINDING_LANDING;
        landingAttempts = 0;
    }

    private void initiateEmergencyDisconnect(String reason) {
        error("Initiating emergency disconnect: " + reason);
        currentState = RepairState.EMERGENCY_DISCONNECT;
    }

    private boolean needsRepair(ItemStack elytra) {
        if (elytra.getItem() != Items.ELYTRA) return false;
        int remainingDurability = elytra.getMaxDamage() - elytra.getDamage();
        return remainingDurability <= repairThreshold.get();
    }

    private void findElytraSlots() {
        elytraSlots.clear();

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ELYTRA) {
                elytraSlots.add(i);
            }
        }

        info("Found " + elytraSlots.size() + " elytras in inventory");
    }

    private boolean hasRepairableElytras() {
        for (int slot : elytraSlots) {
            ItemStack elytra = mc.player.getInventory().getStack(slot);
            if (elytra.getItem() == Items.ELYTRA) {
                int remainingDurability = elytra.getMaxDamage() - elytra.getDamage();
                // Can be repaired if it has at least some durability and can benefit from mending
                if (remainingDurability > 10 && elytra.getDamage() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isElytraInOffhand(ItemStack targetElytra) {
        ItemStack offhand = mc.player.getInventory().getStack(40); // Offhand slot
        return offhand.getItem() == Items.ELYTRA && offhand.getDamage() == targetElytra.getDamage();
    }

    private void moveElytraToOffhand(int fromSlot) {
        // Click on the elytra in inventory
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            fromSlot < 9 ? fromSlot + 36 : fromSlot, // Convert to screen handler slot
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        // Click on offhand slot
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            45, // Offhand slot in screen handler
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }

    private void selectBestElytra() {
        ItemStack bestElytra = null;
        int bestSlot = -1;
        int bestDurability = 0;

        // Find elytra with highest durability
        for (int slot : elytraSlots) {
            ItemStack elytra = mc.player.getInventory().getStack(slot);
            if (elytra.getItem() == Items.ELYTRA) {
                int durability = elytra.getMaxDamage() - elytra.getDamage();
                if (durability > bestDurability) {
                    bestDurability = durability;
                    bestElytra = elytra;
                    bestSlot = slot;
                }
            }
        }

        if (bestElytra != null && bestSlot != -1) {
            // Equip the best elytra
            equipElytra(bestSlot);
            info("Equipped elytra with " + bestDurability + " durability");
        }
    }

    private void equipElytra(int slot) {
        // Move elytra to chest slot
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slot < 9 ? slot + 36 : slot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            6, // Chest slot in screen handler
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }

    private void flyToPosition(Vec3d target) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getPos();
        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;
        double dy = target.y - playerPos.y;

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDistance > 1.0) {
            double yaw = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;
            double pitch = Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI;

            // Limit pitch for safe flying
            pitch = Math.max(-30.0, Math.min(30.0, pitch));

            mc.player.setYaw((float) yaw);
            mc.player.setPitch((float) pitch);
        }
    }

    private void resetRepairState() {
        targetLandingSpot = null;
        resumePosition = null;
        repairStartTime = 0;
        currentRepairSlot = 0;
        elytraSlots.clear();
        wasStashHunterActive = false;
        landingAttempts = 0;
    }

    private void resumeNormalOperation() {
        resetRepairState();
        currentState = RepairState.MONITORING;

        // Re-enable ElytraFly if needed
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly != null && !elytraFly.isActive() && wasStashHunterActive) {
            elytraFly.toggle();
        }
    }

    public RepairState getCurrentState() {
        return currentState;
    }

    public boolean isRepairing() {
        return currentState != RepairState.MONITORING;
    }

    public String getCurrentStateName() {
        return currentState.name();
    }

    private void selectHotbarSlot(int slot) {
        if (slot < 0 || slot > 8 || mc.player == null) return;

        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
        ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
    }
}
