package com.stashhunter.stashhunter.signals;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

/** Whether a sign carries text a player wrote. */
public final class SignText {
    private SignText() {}

    /** True when the front or the back has at least one line that is not blank. */
    public static boolean hasText(SignBlockEntity sign) {
        return hasText(sign.getText(SignTextSlot.FRONT)) || hasText(sign.getText(SignTextSlot.BACK));
    }

    private static boolean hasText(net.minecraft.world.level.block.entity.SignText text) {
        // Unfiltered lines: the client gets what was written; filtering is a per-player chat setting.
        for (Component line : text.getMessages(false)) {
            if (!line.getString().isBlank()) return true;
        }
        return false;
    }
}
