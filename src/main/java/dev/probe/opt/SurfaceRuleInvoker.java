package dev.probe.opt;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** WorldgenAlloc: SurfaceRules.SurfaceRule is protected; this makes its tryApply callable from our overwrite. */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$SurfaceRule")
public interface SurfaceRuleInvoker {
    @Invoker("tryApply")
    @Nullable
    BlockState probe$tryApply(int x, int y, int z);
}
