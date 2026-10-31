package dev.sleep.opt;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sleep.optsupport.LightScout;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (lab.lightscout): fluid blocks (their own light code). */
@Mixin(LiquidBlockRenderer.class)
public abstract class LightScoutFluidMixin {
    @Inject(method = "tesselate", at = @At("HEAD"))
    private void sleep$fluid(BlockAndTintGetter level, BlockPos pos, VertexConsumer buffer, BlockState state, FluidState fluid, CallbackInfo ci) {
        LightScout.fluidBlock();
    }
}
