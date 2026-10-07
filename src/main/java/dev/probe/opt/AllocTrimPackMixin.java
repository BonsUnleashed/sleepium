package dev.probe.opt;

import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;

/** AllocTrim: a marker - OptMixinPlugin.postApply rewrites PalettedContainer.pack's int[] (AllocTrim.transformPack). */
@Mixin(PalettedContainer.class)
public abstract class AllocTrimPackMixin {
}
