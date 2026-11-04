package dev.lull.shadermixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lull.shaders.ShaderIds;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A shaderpack's block ids into the section's vertex bytes, block by block (ShaderIds). 1.20.1: the compile is
 * RenderChunk.RebuildTask.compile (1.21.1: SectionCompiler.compile); the last block is patched where the block loop
 * ends, at the translucent layer's check (1.21.1: before NeoForge's additional geometry) - before any layer is ended.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public class ShaderTerrainIdsMixin {
    @ModifyArg(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderLiquid(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"), index = 2)
    private VertexConsumer sleep$fluidIds(BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer, BlockState state, FluidState fluid) {
        if (ShaderIds.maps != null && consumer instanceof BufferBuilder b) ShaderIds.beginBlock(b, state, true);
        return consumer;
    }

    @ModifyArg(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"), index = 4)
    private VertexConsumer sleep$blockIds(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose, VertexConsumer consumer, boolean checkSides,
            RandomSource random, net.minecraftforge.client.model.data.ModelData modelData, net.minecraft.client.renderer.RenderType type) {
        if (ShaderIds.maps != null && consumer instanceof BufferBuilder b) ShaderIds.beginBlock(b, state, false);
        return consumer;
    }

    @Inject(method = "compile", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private void sleep$lastBlock(CallbackInfoReturnable<?> cir) {
        if (ShaderIds.maps != null) ShaderIds.endSection();
    }
}
