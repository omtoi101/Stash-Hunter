package com.baseminer.basefinder.events;

import meteordevelopment.orbit.ICancellable;

public class PlayerDisconnectEvent implements ICancellable {
    private static final PlayerDisconnectEvent INSTANCE = new PlayerDisconnectEvent();

    public static PlayerDisconnectEvent get() {
        INSTANCE.setCancelled(false);
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
