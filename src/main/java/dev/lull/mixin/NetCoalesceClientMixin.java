package dev.lull.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import javax.crypto.Cipher;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Network engine stage C, the client's half: every non-memory client connection gets the container decoder before its
 * frame splitter. It passes plain frames through until the server switches (only a server with the engine on, and only
 * after seeing this client's capability channel), so it is always installed and costs a VarInt read per frame.
 * <p>
 * 1.20.1: configureSerialization has no memoryOnly flag (in-memory connections never call it). The decoder must read
 * decrypted bytes, but an online-mode login adds "decrypt" before "splitter" - in front of the decoder: there a fresh
 * decoder goes back between them (before the server can switch: it switches in PLAY, encryption starts in LOGIN).
 */
@Mixin(Connection.class)
public abstract class NetCoalesceClientMixin {
    @Shadow private Channel channel;

    @Inject(method = "configureSerialization", at = @At("RETURN"))
    private static void sleep$decoder(ChannelPipeline pipeline, PacketFlow flow, CallbackInfo ci) {
        if (flow == PacketFlow.CLIENTBOUND && pipeline.get("splitter") != null && pipeline.get("probe_uncoalesce") == null)
            pipeline.addBefore("splitter", "probe_uncoalesce", new dev.lull.net.CoalesceDecoder());
    }

    @Inject(method = "setEncryptionKey", at = @At("RETURN"))
    private void sleep$afterDecrypt(Cipher decrypting, Cipher encrypting, CallbackInfo ci) {
        ChannelPipeline p = this.channel.pipeline();
        if (p.get("probe_uncoalesce") == null || p.get("splitter") == null) return;   // the server's side, or no decoder
        p.remove("probe_uncoalesce");   // nothing switched yet: it holds no state worth keeping
        p.addBefore("splitter", "probe_uncoalesce", new dev.lull.net.CoalesceDecoder());
    }
}
