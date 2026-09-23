package com.stashhunter.stashhunter.mixin;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.events.PlayerDeathEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof LocalPlayer) {
            StashHunter.LOG.info("Player death mixin called!");
            MeteorClient.EVENT_BUS.post(PlayerDeathEvent.get((LocalPlayer) entity));
        }
    }
}
