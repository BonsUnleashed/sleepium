package dev.sleep.opt;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A chunk being unloaded that is not ready for saving yet (a generation task still claims it) is checked again next
 * tick instead of at once. Vanilla re-queues the check onto the very queue processUnloads is draining; in a tick the
 * drain ends with the tick's time, but while the server stops it drains with no limit (hasMoreTime = () -> true), so
 * the check spins forever on the main thread: the claim is released only when the generation task finishes, and it
 * waits for main-thread work (a load's main-thread part, the FULL step) the spinning main thread never gets to — the
 * exit hangs at "Saving worlds" with the server thread at 100 % (a quit while chunks generate). Deferred to the next
 * tick, the stop loop's waitUntilNextTick runs that work between the checks. During play this is what vanilla does
 * whenever the tick's time runs out first.
 */
@Mixin(ChunkMap.class)
public abstract class UnloadRecheckMixin {
    @Unique private final Long2ObjectLinkedOpenHashMap<ChunkHolder> probe$recheck = new Long2ObjectLinkedOpenHashMap<>();
    @Unique private long probe$lastLog;
    @Unique private int probe$deferred;

    @Shadow @org.spongepowered.asm.mixin.Final net.minecraft.server.level.ServerLevel level;

    @Shadow
    private void scheduleUnload(long chunkPos, ChunkHolder chunkHolder) {
        throw new AssertionError();
    }

    @Redirect(method = "lambda$scheduleUnload$12", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V"))
    private void probe$later(ChunkMap self, long chunkPos, ChunkHolder holder) {
        probe$recheck.put(chunkPos, holder);   // main thread (the unload queue runs there)
        probe$deferred++;
    }

    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void probe$recheckNow(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        if (probe$recheck.isEmpty()) return;
        long now = System.nanoTime();
        if (!level.getServer().isRunning() && now - probe$lastLog > 2_000_000_000L) {
            probe$lastLog = now;
            System.out.println("[probe/opt] unload recheck while stopping: " + probe$recheck.size() + " chunks wait for their generation ("
                    + probe$deferred + " deferrals so far); " + dev.sleep.optsupport.FullRing.debug());
        }
        Long2ObjectLinkedOpenHashMap<ChunkHolder> due = probe$recheck.clone();
        probe$recheck.clear();
        for (var e : due.long2ObjectEntrySet()) scheduleUnload(e.getLongKey(), e.getValue());
    }
}
