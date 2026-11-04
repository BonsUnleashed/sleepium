package dev.lull.mixin;

import dev.lull.optsupport.GenTrace;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Measurement only (-Dlab.pgen.trace=true): chunks sent to players. 1.20.1 has no PlayerChunkSender (1.20.2+: its
 * batches, acknowledgements and rate, sampled on 1.21.1): ChunkMap.playerLoadedChunk sends each chunk at once.
 */
@Mixin(ChunkMap.class)
public abstract class GenTraceSendMixin {
    @Inject(method = "playerLoadedChunk", at = @At("HEAD"))
    private void sleep$sent(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet, LevelChunk chunk, CallbackInfo ci) {
        GenTrace.sent.increment();
    }
}
