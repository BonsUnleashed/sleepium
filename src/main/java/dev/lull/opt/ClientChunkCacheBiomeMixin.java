package dev.lull.opt;

import dev.lull.optsupport.BiomeMemo;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ClientBiomeMemoMixin: every change of the chunk data (or of which chunks count as present) clears the biome memo. */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheBiomeMixin {
    @Shadow @Final ClientLevel level;

    @Inject(method = {"drop", "replaceBiomes", "updateViewCenter", "updateViewRadius"}, at = @At("TAIL"))
    private void sleep$changed(CallbackInfo ci) {
        ((BiomeMemo) this.level).sleep$biomesChanged();
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void sleep$loaded(CallbackInfoReturnable<LevelChunk> cir) {
        ((BiomeMemo) this.level).sleep$biomesChanged();
    }
}
