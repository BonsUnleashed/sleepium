package dev.sleep.mixin;

import io.netty.channel.ChannelPipeline;
import javax.annotation.Nullable;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Network engine stage C, the client's half: every non-memory client connection gets the container decoder before its
 * frame splitter. It passes plain frames through until the server switches (only a server with the engine on, and only
 * after seeing this client's capability channel), so it is always installed and costs a VarInt read per frame.
 */
@Mixin(Connection.class)
public abstract class NetCoalesceClientMixin {
    @Inject(method = "configureSerialization", at = @At("RETURN"))
    private static void sleep$decoder(ChannelPipeline pipeline, PacketFlow flow, boolean memoryOnly, @Nullable BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (!memoryOnly && flow == PacketFlow.CLIENTBOUND && pipeline.get("splitter") != null && pipeline.get("probe_uncoalesce") == null)
            pipeline.addBefore("splitter", "probe_uncoalesce", new dev.sleep.net.CoalesceDecoder());
    }
}
