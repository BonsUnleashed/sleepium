package dev.sleep.opt;

import dev.sleep.optsupport.ChunkCacheHolder;
import dev.sleep.optsupport.OptCounters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/chunk-cache: one-chunk lookup cache in Level for getBlockState / getChunkAt (getFluidState, getBlockEntity,
 * setBlock...). JFR on the redstone scene: ~17 % of the server thread in Level.getChunk + ServerChunkCache.getChunk
 * (thread check, profiler counter, 4-entry cache loop). Vanilla's own cache entries stay valid until
 * ServerChunkCache.clearCache(); this cache is dropped at exactly the same moments (ChunkCacheClearMixin), so it
 * always returns the object vanilla would return. Server levels, owner thread only.
 * {@code -Dlab.opt.chunkCacheCheck=true} compares every hit with vanilla's answer.
 */
@Mixin(Level.class)
public abstract class ChunkCacheMixin implements ChunkCacheHolder {
    @Shadow @Final private Thread thread;

    @Shadow public abstract LevelChunk getChunk(int x, int z);

    @Unique private long probe$ccKey = Long.MIN_VALUE;
    @Unique private LevelChunk probe$ccChunk;
    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.chunkCacheCheck");

    @Override
    public void probe$ccReset() {
        probe$ccKey = Long.MIN_VALUE;
        probe$ccChunk = null;
    }

    /** Hot path kept tiny so getBlockState stays inlinable (escape analysis of callers' BlockPos depends on it). */
    @Unique
    private LevelChunk probe$cached(int x, int z) {
        long key = ChunkPos.asLong(x, z);
        if (key == probe$ccKey && Thread.currentThread() == thread) {
            if (CHECK) probe$check(x, z);
            return probe$ccChunk;
        }
        return probe$miss(x, z, key);
    }

    @Unique
    private LevelChunk probe$miss(int x, int z, long key) {
        LevelChunk c = getChunk(x, z);
        if (Thread.currentThread() == thread && (Object) this instanceof ServerLevel) {
            probe$ccKey = key;
            probe$ccChunk = c;
        }
        return c;
    }

    @Unique
    private void probe$check(int x, int z) {
        OptCounters.CHUNK_CHECKED.incrementAndGet();
        if (getChunk(x, z) != probe$ccChunk) OptCounters.CHUNK_MISMATCHES.incrementAndGet();
    }

    @Redirect(method = "getBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk probe$blockState(Level self, int x, int z) {
        return probe$cached(x, z);
    }

    @Redirect(method = "getChunkAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk probe$chunkAt(Level self, int x, int z) {
        return probe$cached(x, z);
    }
}
