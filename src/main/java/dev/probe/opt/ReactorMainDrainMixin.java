package dev.probe.opt;

import dev.probe.optsupport.FullRing;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WorldgenReactor: ServerChunkCache.pollTask is the server's idle-loop entry (MinecraftServer.pollTaskInternal, only
 * while it has time): FullRing's window and deadline apply there.
 */
@Mixin(ServerChunkCache.class)
public abstract class ReactorMainDrainMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final public net.minecraft.server.level.ChunkMap chunkMap;

    @Inject(method = "pollTask", at = @At("HEAD"))
    private void probe$idleOn(CallbackInfoReturnable<Boolean> cir) {
        FullRing.idle = true;
        if ((Object) this.chunkMap instanceof dev.probe.optsupport.ChunkLoadMarks m) m.probe$drainMarks();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void probe$marks(java.util.function.BooleanSupplier hasTimeLeft, boolean tickChunks, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if ((Object) this.chunkMap instanceof dev.probe.optsupport.ChunkLoadMarks m) m.probe$drainMarks();
    }

    @Inject(method = "pollTask", at = @At("RETURN"))
    private void probe$idleOff(CallbackInfoReturnable<Boolean> cir) {
        FullRing.idle = false;
    }
}
