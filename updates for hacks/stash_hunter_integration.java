// Updates needed for StashHunterModule.java

// Add this import at the top:
import com.stashhunter.stashhunter.modules.AutoElytraRepair;

// Add this field with other module references:
private final AutoElytraRepair autoElytraRepair = Modules.get().get(AutoElytraRepair.class);

// Replace the existing elytra check logic in onTick() method with this enhanced version:

// Enhanced Elytra check with repair integration
if (ElytraController.isActive()) {
    ItemStack chestSlot = mc.player.getEquippedStack(EquipmentSlot.CHEST);
    boolean elytraMissing = chestSlot.isEmpty() || 
        (chestSlot.getItem() == Items.ELYTRA && chestSlot.isDamage() >= chestSlot.getMaxDamage() - 1);

    // Check if auto repair is handling the situation
    if (autoElytraRepair != null && autoElytraRepair.isActive() && autoElytraRepair.isRepairing()) {
        // Auto repair is active, let it handle elytra management
        return; // Skip normal elytra checks
    }

    if (elytraMissing) {
        wasElytraBroken = true;
        elytraBrokenTicks++;
        
        // Give auto repair system time to activate before panicking
        int timeoutTicks = autoElytraRepair != null && autoElytraRepair.isActive() ? 200 : 40;
        
        if (elytraBrokenTicks > timeoutTicks) {
            // Send Discord notification
            DiscordEmbed embed = new DiscordEmbed(
                "Out of Elytras!",
                "The bot has run out of elytras or all elytras are broken beyond repair, and will now disconnect.",
                0xFF0000
            );
            DiscordWebhook.sendMessage("@everyone", embed);

            // Disconnect from server
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.of("Ran out of elytras or all elytras broken."));
            }

            // Stop elytra controller
            ElytraController.stop();

            // Deactivate the module
            toggle();
            return;
        }
    } else {
        elytraBrokenTicks = 0;
        if (wasElytraBroken) {
            wasElytraBroken = false;
            reEngagingElytraTicks = 100; // 5 seconds
        }
    }
}