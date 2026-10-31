package dev.sleep.opt;

import dev.sleep.optsupport.LightScout;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (lab.lightscout): an AO face, full (flag 1 clear) or partial. */
@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public abstract class LightScoutAoMixin {
    @Inject(method = "calculate", at = @At("HEAD"))
    private void probe$face(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction dir, float[] shape, BitSet flags, boolean shade, CallbackInfo ci) {
        LightScout.aoFace(flags.get(1));
    }
}
