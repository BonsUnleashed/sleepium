package dev.lull.catmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lull.optsupport.KineticResident;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KineticResident: the frame's camera before the level is drawn, the section renderLevel's block entity loop is in, and
 * the loop's visibility test of each block entity (a resident one is drawn from VRAM: not through the dispatcher);
 * LightBoxes: their frame.
 * <p>
 * 1.20.1: the section is a RenderChunk (getCompiledChunk in the loop); there is no ClientHooks.isBlockEntityRendererVisible
 * (1.21's BlockEntityCullNoAllocMixin hook): Forge culls with frustum.isVisible(be.getRenderBoundingBox()) in both block
 * entity loops - the box's block entity is remembered, and a resident one reads as not visible there, as the 1.21 hook
 * answered before its own test.
 */
@Mixin(LevelRenderer.class)
public abstract class KineticLevelMixin {
    @Shadow @Final private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;
    @Shadow private net.minecraft.client.renderer.ViewArea viewArea;

    @Unique private BlockEntity sleep$boxOf;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sleep$frame(PoseStack pose, float partialTick, long finishNanos, boolean outline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
        if (dev.lull.optsupport.PartBatch.LIT_ON) dev.lull.optsupport.LightBoxes.frame();
        if (!KineticResident.ON) return;
        dev.lull.optsupport.PartBatch.persistView.set(pose.last().pose());   // 1.20.1: the camera's rotation is the level pose's

        KineticResident.Crumbling.progress = destructionProgress;
        KineticResident.beginFrame(camera, viewArea);
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk;getCompiledChunk()Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$CompiledChunk;"))
    private ChunkRenderDispatcher.CompiledChunk sleep$section(ChunkRenderDispatcher.RenderChunk s) {
        if (KineticResident.ON) KineticResident.section(s);
        return s.getCompiledChunk();
    }

    /** (Forge's method: no SRG name) */
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getRenderBoundingBox()Lnet/minecraft/world/phys/AABB;", remap = false))
    private AABB sleep$box(BlockEntity be, Operation<AABB> original) {
        sleep$boxOf = be;
        return original.call(be);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean sleep$visible(Frustum frustum, AABB box, Operation<Boolean> original) {
        BlockEntity be = sleep$boxOf;
        sleep$boxOf = null;
        var res = dev.lull.optsupport.NoAllocChecks.RESIDENT;
        if (be != null && res != null && res.drawn(be)) return false;   // drawn from VRAM already: not through the dispatcher
        return original.call(frustum, box);
    }
}
