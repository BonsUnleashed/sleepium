package dev.lull.opt;

import dev.lull.optsupport.ChunkTickMemo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ChunkTickMemoMixin: every write of the chunk-visibility map goes through here. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class ChunkTickMemoEpochMixin {
    @Inject(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V", at = @At("HEAD"))
    private void sleep$visibilityChanged(ChunkPos pos, Visibility visibility, CallbackInfo ci) {
        ChunkTickMemo.epoch++;
    }
}
