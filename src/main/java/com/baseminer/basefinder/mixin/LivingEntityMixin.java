package com.baseminer.basefinder.mixin;

import com.baseminer.basefinder.BaseFinder;
import com.baseminer.basefinder.events.PlayerDeathEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof ClientPlayerEntity) {
            BaseFinder.LOG.info("Player death mixin called!");
            MeteorClient.EVENT_BUS.post(PlayerDeathEvent.get((ClientPlayerEntity) entity));
        }
    }
}
