package dev.lull.catmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import dev.lull.optsupport.KineticMemo;
import dev.lull.optsupport.RenderCache;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * KineticMemo: a chain conveyor's chains while its draw is recorded - drawn as usual this frame, kept as chain parts
 * (KineticMemo.chain: our own chain mesh in VRAM, scrolled on the GPU) for a resident draw.
 */
@Mixin(value = ChainConveyorRenderer.class, remap = false)
public abstract class ChainConveyorMemoMixin {
    @WrapOperation(method = "renderChains", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorRenderer;renderChain(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;FFIIZ)V"))
    private void sleep$chain(PoseStack ms, MultiBufferSource buffer, float animation, float length, int light1, int light2, boolean far, Operation<Void> original,
            @Local(argsOnly = true) ChainConveyorBlockEntity be, @Local(ordinal = 0) BlockPos offset) {
        if (!KineticMemo.INSTANCE.recording() || !(buffer instanceof MultiBufferSource.BufferSource src)) {
            original.call(ms, buffer, animation, length, light1, light2, far);
            return;
        }
        long before = RenderCache.vertices(src);
        original.call(ms, buffer, animation, length, light1, light2, far);
        KineticMemo.INSTANCE.chain(ms.last(), animation, length, light1, light2, be, offset, RenderCache.vertices(src) - before);
    }
}
