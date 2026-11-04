package dev.lull.mixin;

import dev.lull.NetStats;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (sleep.net.stats): server-thread time inside Connection.send (1.20.1: send(packet, listener), no flush flag). */
@Mixin(Connection.class)
public abstract class NetStatsSendMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"))
    private void sleep$start(Packet<?> packet, @Nullable PacketSendListener l, CallbackInfo ci) {
        if (Thread.currentThread() != NetStats.serverThread) return;
        if (NetStats.depth++ == 0) NetStats.sendStart = System.nanoTime();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("RETURN"))
    private void sleep$end(Packet<?> packet, @Nullable PacketSendListener l, CallbackInfo ci) {
        if (Thread.currentThread() != NetStats.serverThread) return;
        if (--NetStats.depth == 0) {
            NetStats.SEND_NANOS.add(System.nanoTime() - NetStats.sendStart);
            NetStats.SENDS.increment();
        }
    }
}
