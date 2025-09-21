package com.stashhunter.stashhunter.events;

import meteordevelopment.orbit.ICancellable;
import net.minecraft.entity.player.PlayerEntity;

public class PlayerDeathEvent implements ICancellable {
    private static final PlayerDeathEvent INSTANCE = new PlayerDeathEvent();

    public PlayerEntity player;

    public static PlayerDeathEvent get(PlayerEntity player) {
    INSTANCE.setCancelled(false);
    INSTANCE.player = player;
    com.stashhunter.stashhunter.utils.Logger.log("Player death event triggered for: " + player.getName().getString());
    return INSTANCE;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        // Not cancellable
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
