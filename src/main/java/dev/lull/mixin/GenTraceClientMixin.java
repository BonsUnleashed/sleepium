package dev.lull.mixin;

import dev.lull.optsupport.GenTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dlab.pgen.trace=true): chunk and light packets the client applies (on its own thread). */
@Mixin(ClientPacketListener.class)
public abstract class GenTraceClientMixin {
    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void sleep$chunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) GenTrace.clientChunks.increment();
    }

    @Inject(method = "handleLightUpdatePacket", at = @At("TAIL"))
    private void sleep$light(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) GenTrace.clientLights.increment();
    }
}
