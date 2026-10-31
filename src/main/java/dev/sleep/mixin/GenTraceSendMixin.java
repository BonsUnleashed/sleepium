package dev.sleep.mixin;

import dev.sleep.optsupport.GenTrace;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dlab.pgen.trace=true): chunks sent to players, and once a second the sender's state. */
@Mixin(PlayerChunkSender.class)
public abstract class GenTraceSendMixin {
    @Shadow @Final private LongSet pendingChunks;
    @Shadow private float desiredChunksPerTick;
    @Shadow private int unacknowledgedBatches;
    @Shadow private int maxUnacknowledgedBatches;

    @Inject(method = "sendChunk", at = @At("HEAD"))
    private static void sleep$sent(ServerGamePacketListenerImpl listener, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        GenTrace.sent.increment();
    }

    @Inject(method = "sendNextChunks", at = @At("TAIL"))
    private void sleep$state(ServerPlayer player, CallbackInfo ci) {
        long now = System.nanoTime();
        if (now - GenTrace.lastSenderSample < 1_000_000_000L) return;
        GenTrace.lastSenderSample = now;
        var map = player.serverLevel().getChunkSource().chunkMap;
        int ready = 0;
        for (long p : pendingChunks) if (map.getChunkToSend(p) != null) ready++;
        GenTrace.sender = String.format(java.util.Locale.ROOT, "pending %d (ready %d), unacked %d/%d, desired %.1f/tick",
                pendingChunks.size(), ready, unacknowledgedBatches, maxUnacknowledgedBatches, desiredChunksPerTick);
    }
}
