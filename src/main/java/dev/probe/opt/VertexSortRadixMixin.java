package dev.probe.opt;

import com.mojang.blaze3d.vertex.VertexSorting;
import org.spongepowered.asm.mixin.Mixin;

/**
 * exp/quad-sort (client): carrier only. OptMixinPlugin.postApply replaces the body of VertexSorting's byDistance lambda
 * (float per quad + comparator merge sort) with QuadSort.byDistance (same floats, stable radix sort, same permutation).
 */
@Mixin(VertexSorting.class)
public interface VertexSortRadixMixin {
}
