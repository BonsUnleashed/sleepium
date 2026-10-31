package dev.sleep.opt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/tick-memo: Level.tickBlockEntities asks shouldTickBlocksAt(pos) for every ticking block entity, every tick:
 * a hash lookup in the distance manager's ticking tracker (JFR: ~30 % of the server thread in the smelter scene).
 * The answer depends only on the chunk and cannot change while block entities tick (tickets are applied in
 * chunkSource.tick), so it is memoized per chunk: the ticker list is grouped by chunk, a one-entry memo hits almost
 * always. Exact by construction.
 */
@Mixin(Level.class)
public abstract class TickMemoMixin {
    @Unique private long sleep$memoGameTime = Long.MIN_VALUE, sleep$memoChunk;
    @Unique private boolean sleep$memoValue;

    @Shadow public abstract long getGameTime();

    @Shadow public abstract boolean shouldTickBlocksAt(long chunkPos);

    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean sleep$memoShouldTick(Level self, BlockPos pos) {
        long chunk = ChunkPos.asLong(pos);
        long now = getGameTime();
        if (sleep$memoGameTime == now && sleep$memoChunk == chunk) return sleep$memoValue;
        boolean v = shouldTickBlocksAt(chunk);
        sleep$memoGameTime = now;
        sleep$memoChunk = chunk;
        sleep$memoValue = v;
        return v;
    }
}
