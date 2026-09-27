package dev.probe.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.probe.optsupport.BlockMeshMemo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/block-mesh-memo (client): tesselateBlock's AO / flat tesselation against the client level served from
 * BlockMeshMemo while the section around the block is unchanged (pistons' moving blocks, falling blocks, …). The
 * model offset translate before these calls stays vanilla's.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class BlockMeshMemoMixin {
    private static final String TESSELATE = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";
    private static final String ARGS = "(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V";

    @Redirect(method = TESSELATE, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateWithAO" + ARGS))
    private void probe$ao(ModelBlockRenderer self, BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack pose, VertexConsumer vc,
                          boolean checkSides, RandomSource random, long seed, int overlay, ModelData data, RenderType rt) {
        int r = BlockMeshMemo.begin(level, model, state, pos, pose, vc, checkSides, seed, overlay, data, rt, true);
        if (r == BlockMeshMemo.SERVED) return;
        if (r == BlockMeshMemo.VANILLA) {
            self.tesselateWithAO(level, model, state, pos, pose, vc, checkSides, random, seed, overlay, data, rt);
            return;
        }
        try {
            self.tesselateWithAO(level, model, state, pos, BlockMeshMemo.recordPose(), BlockMeshMemo.recorder(), checkSides, random, seed, overlay, data, rt);
        } catch (Throwable t) {
            BlockMeshMemo.abort();
            throw t;
        }
        BlockMeshMemo.end(pose, vc);
    }

    @Redirect(method = TESSELATE, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateWithoutAO" + ARGS))
    private void probe$flat(ModelBlockRenderer self, BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack pose, VertexConsumer vc,
                            boolean checkSides, RandomSource random, long seed, int overlay, ModelData data, RenderType rt) {
        int r = BlockMeshMemo.begin(level, model, state, pos, pose, vc, checkSides, seed, overlay, data, rt, false);
        if (r == BlockMeshMemo.SERVED) return;
        if (r == BlockMeshMemo.VANILLA) {
            self.tesselateWithoutAO(level, model, state, pos, pose, vc, checkSides, random, seed, overlay, data, rt);
            return;
        }
        try {
            self.tesselateWithoutAO(level, model, state, pos, BlockMeshMemo.recordPose(), BlockMeshMemo.recorder(), checkSides, random, seed, overlay, data, rt);
        } catch (Throwable t) {
            BlockMeshMemo.abort();
            throw t;
        }
        BlockMeshMemo.end(pose, vc);
    }
}
