package dev.sleep.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sleep.optsupport.LightScout;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (lab.lightscout): flat-lit quads. */
@Mixin(ModelBlockRenderer.class)
public abstract class LightScoutFlatMixin {
    @Inject(method = "renderModelFaceFlat", at = @At("HEAD"))
    private void probe$flat(BlockAndTintGetter level, BlockState state, BlockPos pos, int light, int overlay, boolean repack, PoseStack pose,
                            VertexConsumer consumer, List<BakedQuad> quads, BitSet flags, CallbackInfo ci) {
        for (int i = 0; i < quads.size(); i++) LightScout.flatFace();
    }
}
