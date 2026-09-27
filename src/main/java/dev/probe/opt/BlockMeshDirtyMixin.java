package dev.probe.opt;

import dev.probe.optsupport.BlockMeshMemo;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockMeshMemoMixin: the section marks vanilla makes for its chunk meshes (every block, light and chunk change goes
 * through setSectionDirty), a renderer reset or a new level (everything stale), and the frame counter for eviction.
 */
@Mixin(LevelRenderer.class)
public abstract class BlockMeshDirtyMixin {
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void probe$marked(int x, int y, int z, boolean mainThread, CallbackInfo ci) {
        BlockMeshMemo.marked(x, y, z);
    }

    @Inject(method = "allChanged", at = @At("HEAD"))
    private void probe$allChanged(CallbackInfo ci) {
        BlockMeshMemo.clearAll();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void probe$level(CallbackInfo ci) {
        BlockMeshMemo.clearAll();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void probe$frame(CallbackInfo ci) {
        BlockMeshMemo.frame++;
    }
}
