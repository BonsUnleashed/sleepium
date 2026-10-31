package dev.sleep.mixin;

import dev.sleep.NetStats;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Varint21LengthFieldPrepender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (probe.net.stats): every frame written to a socket, after compression. */
@Mixin(Varint21LengthFieldPrepender.class)
public abstract class NetStatsBytesMixin {
    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V", at = @At("HEAD"))
    private void probe$count(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out, CallbackInfo ci) {
        NetStats.FRAMES.increment();
        NetStats.BYTES.add(in.readableBytes() + 3);
    }
}
