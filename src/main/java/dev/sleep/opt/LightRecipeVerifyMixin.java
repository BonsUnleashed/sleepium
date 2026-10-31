package dev.sleep.opt;

import dev.sleep.optsupport.LightRecipe;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** exp/shader-light step 1 (measurement, -Dlab.lightrecipe.verify=true): recipes checked against vanilla's AO light. */
@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public abstract class LightRecipeVerifyMixin {
    @Shadow @Final int[] lightmap;

    @Inject(method = "calculate", at = @At("RETURN"))
    private void probe$verify(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction dir, float[] shape, BitSet flags, boolean shade, CallbackInfo ci) {
        LightRecipe.verify(level, pos, dir, flags, lightmap);
    }
}
