package dev.sleep.opt;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EntityBatchMixin: the state shards that decide whether a render type's draws commute with each other. */
@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor("transparencyState")
    RenderStateShard.TransparencyStateShard sleep$transparency();

    @Accessor("depthTestState")
    RenderStateShard.DepthTestStateShard sleep$depthTest();

    @Accessor("writeMaskState")
    RenderStateShard.WriteMaskStateShard sleep$writeMask();

    @Accessor("outputState")
    RenderStateShard.OutputStateShard sleep$output();

    @Accessor("shaderState")
    RenderStateShard.ShaderStateShard sleep$shader();

    @Accessor("colorLogicState")
    RenderStateShard.ColorLogicStateShard sleep$colorLogic();
}
