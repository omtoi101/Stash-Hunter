package com.stashhunter.stashhunter.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Inventory.class)
public interface PlayerInventoryAccessor {
    @Accessor("selected")
    void setSelectedSlot(int slot);
}
