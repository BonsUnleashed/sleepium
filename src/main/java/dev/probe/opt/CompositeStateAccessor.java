package dev.probe.opt;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EntityBatchMixin: the state shards that decide whether a render type's draws commute with each other. */
@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor("transparencyState")
    RenderStateShard.TransparencyStateShard probe$transparency();

    @Accessor("depthTestState")
    RenderStateShard.DepthTestStateShard probe$depthTest();

    @Accessor("writeMaskState")
    RenderStateShard.WriteMaskStateShard probe$writeMask();

    @Accessor("outputState")
    RenderStateShard.OutputStateShard probe$output();

    @Accessor("colorLogicState")
    RenderStateShard.ColorLogicStateShard probe$colorLogic();
}
