package com.stashhunter.stashhunter.signals;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * One cluster after structure filtering.
 *
 * @param signals          all remaining signals; removed containers are not included
 * @param keptContainers   CONTAINER-category positions that survived the filter (never STRONG kinds)
 * @param containerShapeOk hasStashShape on keptContainers plus STRONG positions
 * @param filtered         removed containers per reason
 */
public record Candidate(BlockPos center, List<Signal> signals, List<BlockPos> keptContainers,
                        boolean containerShapeOk, Map<Rejection, Integer> filtered) {
}
