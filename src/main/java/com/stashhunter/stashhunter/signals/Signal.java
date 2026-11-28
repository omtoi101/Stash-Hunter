package com.stashhunter.stashhunter.signals;

import net.minecraft.core.BlockPos;

public record Signal(BlockPos pos, SignalKind kind) {
}
