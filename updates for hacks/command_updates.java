// Add these imports to StashHunterCommand.java:
import com.stashhunter.stashhunter.modules.AutoElytraRepair;
import meteordevelopment.meteorclient.systems.modules.Modules;

// Add this new command to the build() method in StashHunterCommand.java:

// Repair status command
builder.then(literal("repair")
    .executes(context -> {
        AutoElytraRepair repairModule = Modules.get().get(AutoElytraRepair.class);
        if (repairModule == null) {
            error("Auto Elytra Repair module not found.");
            return SINGLE_SUCCESS;
        }
        
        if (!repairModule.isActive()) {
            info("Auto Elytra Repair: §cDisabled");
        } else if (repairModule.isRepairing()) {
            info("Auto Elytra Repair: §eActive - " + repairModule.getCurrentState().toString());
        } else {
            info("Auto Elytra Repair: §aEnabled - Monitoring");
        }
        
        // Show current elytra status
        if (MeteorClient.mc.player != null) {
            ItemStack chestSlot = MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestSlot.getItem() == Items.ELYTRA) {
                int durability = chestSlot.getMaxDamage() - chestSlot.getDamage();
                int maxDurability = chestSlot.getMaxDamage();
                info("Current Elytra: " + durability + "/" + maxDurability + " durability");
            } else {
                info("No Elytra currently equipped");
            }
        }
        
        return SINGLE_SUCCESS;
    })
    .then(literal("toggle")
        .executes(context -> {
            AutoElytraRepair repairModule = Modules.get().get(AutoElytraRepair.class);
            if (repairModule == null) {
                error("Auto Elytra Repair module not found.");
                return SINGLE_SUCCESS;
            }
            
            repairModule.toggle();
            info("Auto Elytra Repair: " + (repairModule.isActive() ? "§aEnabled" : "§cDisabled"));
            return SINGLE_SUCCESS;
        })
    )
);

// Update the help command to include repair information:
// In the help command execution, add these lines:

info("§7/stashhunter repair §f- Show elytra repair status");
info("§7/stashhunter repair toggle §f- Toggle auto elytra repair");

// Add to the detailed help section:
info("");
info("§eAuto Elytra Repair:");
info("§7Automatically repairs elytras when durability gets low");
info("§7Finds safe landing spots and cycles through elytras for mending");
info("§7Requires AutoExp module to be available in Meteor Client");