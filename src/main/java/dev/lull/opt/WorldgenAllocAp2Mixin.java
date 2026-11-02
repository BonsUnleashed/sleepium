package dev.lull.opt;

import org.spongepowered.asm.mixin.Mixin;

/** WorldgenAlloc (Ap2): no members; applying it lets OptMixinPlugin.postApply run ScratchArrays.transform on Ap2. */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Ap2")
public abstract class WorldgenAllocAp2Mixin {
}
