package com.stashhunter.stashhunter.signals;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Natural-structure checks the clusterer applies to containers only. */
public interface ContainerFilter {
    boolean filterNaturalStructures();

    boolean hasStashShape(List<BlockPos> containers);

    boolean nearStructureSpawner(BlockPos center);
}
