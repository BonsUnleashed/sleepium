package dev.lull.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import net.minecraft.network.BandwidthDebugMonitor;
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
 * The decoder must read decrypted bytes, but an online-mode login adds "decrypt" before "splitter" - in front of the
 * decoder: there a fresh decoder goes back between them (before the server can switch: it switches in PLAY, encryption
 * starts in LOGIN). Up to 1.1.4 this was only on 1.20.1: on 1.21.1 the decoder read the encrypted stream as frames,
 * waited for a length that never came, and logins to online-mode servers hung on "Encrypting..." (GitHub issue #2).
 */
@Mixin(Connection.class)
public abstract class NetCoalesceClientMixin {
    @Shadow private Channel channel;

    @Inject(method = "configureSerialization", at = @At("RETURN"))
    private static void sleep$decoder(ChannelPipeline pipeline, PacketFlow flow, boolean memoryOnly, @Nullable BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (!memoryOnly && flow == PacketFlow.CLIENTBOUND && pipeline.get("splitter") != null && pipeline.get("probe_uncoalesce") == null)
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
