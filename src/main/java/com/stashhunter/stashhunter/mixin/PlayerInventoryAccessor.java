package com.stashhunter.stashhunter.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Inventory.class)
public interface PlayerInventoryAccessor {
    // Named stashhunter$setSelectedSlot, not setSelectedSlot: Mixin's @Accessor generates a
    // synthetic method into Inventory using this interface method's exact name+signature, and
    // vanilla Inventory already declares a public "void setSelectedSlot(int)" of its own in
    // 26.2. Reusing that name produced two methods with the identical name/descriptor in the
    // generated class file, which the JVM verifier rejects with ClassFormatError ("Duplicate
    // method name&signature") at mixin-apply time - crashing the game on boot. The field name
    // itself ("selected") is correct; only the accessor method name needed to be distinct.
    @Accessor("selected")
    void stashhunter$setSelectedSlot(int slot);
}
