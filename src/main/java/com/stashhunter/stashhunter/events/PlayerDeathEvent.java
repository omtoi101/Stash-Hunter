package com.stashhunter.stashhunter.events;

import meteordevelopment.orbit.ICancellable;
import net.minecraft.world.entity.player.Player;

public class PlayerDeathEvent implements ICancellable {
    private static final PlayerDeathEvent INSTANCE = new PlayerDeathEvent();

    public Player player;

    public static PlayerDeathEvent get(Player player) {
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
