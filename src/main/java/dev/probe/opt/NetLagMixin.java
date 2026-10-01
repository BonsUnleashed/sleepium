package dev.probe.opt;

import dev.probe.net.NetLag;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NetLag: test latency, installed as a connection becomes active (-Dlab.net.lagMs) */
@Mixin(Connection.class)
public abstract class NetLagMixin {
    @Inject(method = "channelActive", at = @At("TAIL"))
    private void probe$lag(ChannelHandlerContext ctx, CallbackInfo ci) {
        NetLag.install(ctx.channel(), (Connection) (Object) this);
    }
}
