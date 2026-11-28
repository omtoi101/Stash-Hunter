package com.stashhunter.stashhunter.signals;

import com.stashhunter.stashhunter.utils.MobGear;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Turns the entities around the player into stash signals. Called from the scan (every scan interval), never per
 * frame. Idee aus Trouser-Streak (etianl, BaseFinder), kein Code übernommen.
 */
public final class SignalEntities {
    private SignalEntities() {}

    /** One entry per entity that shows a signal, with its UUID so moving entities are not counted twice. */
    public record Collected(List<EntitySignalIndex.Seen> seen) {}

    /**
     * Entities within {@code radius} blocks of {@code center}, measured horizontally like the block scan (the
     * player flies far above the ground). Does nothing unless {@link TraceGroup#ENTITIES} is enabled.
     */
    public static Collected collect(ClientLevel level, BlockPos center, int radius, Set<TraceGroup> enabled) {
        List<EntitySignalIndex.Seen> seen = new ArrayList<>();
        if (!enabled.contains(TraceGroup.ENTITIES)) return new Collected(seen);

        long radiusSq = (long) radius * radius;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null || entity.isRemoved() || entity instanceof Player) continue;
            BlockPos pos = entity.blockPosition();
            long dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
            if (dx * dx + dz * dz > radiusSq) continue;
            SignalKind kind = kindOf(entity);
            if (kind == null) continue;
            seen.add(new EntitySignalIndex.Seen(entity.getUUID(), pos.immutable(), kind));
        }
        return new Collected(seen);
    }

    /** The strongest kind the entity shows; null if it shows none. */
    private static SignalKind kindOf(Entity entity) {
        // End ships hang an elytra in an item frame naturally.
        if (entity instanceof ItemFrame frame && !frame.getItem().isEmpty() && !frame.getItem().is(Items.ELYTRA)) {
            return SignalKind.ITEM_FRAME;
        }
        if (entity instanceof Villager villager && villager.getVillagerData().level() > 1) {
            return SignalKind.TRADED_VILLAGER;
        }
        if (entity instanceof AbstractChestedHorse animal && animal.hasChest()) return SignalKind.CHESTED_ANIMAL;
        if (entity.hasCustomName()) return SignalKind.NAMED_ENTITY;
        if (entity instanceof AbstractBoat) return SignalKind.BOAT;
        if (entity instanceof MinecartChest) return SignalKind.CHEST_MINECART;
        if (entity instanceof Mob mob && hasPlayerGear(mob)) return SignalKind.MOB_GEAR;
        return null;
    }

    private static boolean hasPlayerGear(Mob mob) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (MobGear.isPlayerGear(id, stack.isEnchanted())) return true;
        }
        return false;
    }
}
