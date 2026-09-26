package dev.probe.opt;

import dev.probe.optsupport.ClientCounters;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/ambient-skip (client, with AmbientParticleSkipMixin): the loaded chunks whose biome palettes contain a biome with
 * ambient particles (vanilla: nether biomes; any modded one too). Kept at chunk load, biome update and drop; chunks that
 * leave the view without a drop stay listed until checked (the safe direction).
 */
@Mixin(ClientChunkCache.class)
public abstract class AmbientChunksMixin implements ClientCounters.AmbientChunks {
    @Unique private final LongOpenHashSet probe$ambient = new LongOpenHashSet();

    @Unique
    private static boolean probe$has(LevelChunk chunk) {
        // maybeHas is only a hint (a global palette says yes to anything): confirm entry by entry
        boolean[] found = new boolean[1];
        for (LevelChunkSection s : chunk.getSections()) {
            if (!s.getBiomes().maybeHas(h -> h.value().getAmbientParticle().isPresent())) continue;
            s.getBiomes().getAll(h -> {
                if (h.value().getAmbientParticle().isPresent()) found[0] = true;
            });
            if (found[0]) return true;
        }
        return false;
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void probe$loaded(int x, int z, FriendlyByteBuf buffer, CompoundTag tag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer,
            CallbackInfoReturnable<LevelChunk> cir) {
        ClientCounters.ambientVersion++;
        LevelChunk c = cir.getReturnValue();
        long k = ChunkPos.asLong(x, z);
        if (c != null && probe$has(c)) probe$ambient.add(k);
        else probe$ambient.remove(k);
    }

    @Inject(method = "replaceBiomes", at = @At("TAIL"))
    private void probe$biomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) {
        ClientCounters.ambientVersion++;
        LevelChunk c = ((ClientChunkCache) (Object) this).getChunk(x, z, ChunkStatus.FULL, false);
        long k = ChunkPos.asLong(x, z);
        if (c == null || probe$has(c)) probe$ambient.add(k);   // unknown: keep the vanilla path
        else probe$ambient.remove(k);
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void probe$drop(ChunkPos pos, CallbackInfo ci) {
        ClientCounters.ambientVersion++;
        probe$ambient.remove(pos.toLong());
    }

    @Override
    public boolean probe$anyAmbient() {
        if (probe$ambient.isEmpty()) return false;
        ClientChunkCache self = (ClientChunkCache) (Object) this;
        for (LongIterator it = probe$ambient.iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            if (self.getChunk(ChunkPos.getX(k), ChunkPos.getZ(k), ChunkStatus.FULL, false) != null) return true;
            it.remove();   // left the view without a drop
        }
        return false;
    }
}
