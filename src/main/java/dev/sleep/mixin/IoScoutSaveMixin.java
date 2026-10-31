package dev.sleep.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.MinecraftServer;

/** Measurement only (-Dsleep.ioScout=true): how long a whole-world save takes (exit, autosave, /save-all), and GC in it. */
@Mixin(MinecraftServer.class)
public abstract class IoScoutSaveMixin {
    @Unique private long sleep$t0, sleep$gc0, sleep$gcMs0;

    @Inject(method = "saveAllChunks", at = @At("HEAD"))
    private void sleep$start(boolean suppressLog, boolean flush, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        sleep$t0 = System.nanoTime();
        long[] g = sleep$gc();
        sleep$gc0 = g[0];
        sleep$gcMs0 = g[1];
    }

    @Inject(method = "saveAllChunks", at = @At("RETURN"))
    private void sleep$end(boolean suppressLog, boolean flush, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        long[] g = sleep$gc();
        var h = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        System.out.println(String.format(java.util.Locale.ROOT, "[sleep/io] saveAllChunks (flush %b, forced %b): %.1f ms, GC %d (%d ms), heap used %d MB",
                flush, forced, (System.nanoTime() - sleep$t0) / 1e6, g[0] - sleep$gc0, g[1] - sleep$gcMs0, h.getUsed() >> 20));
    }

    @Unique
    private static long[] sleep$gc() {
        long c = 0, ms = 0;
        for (var b : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            c += Math.max(0, b.getCollectionCount());
            ms += Math.max(0, b.getCollectionTime());
        }
        return new long[] {c, ms};
    }
}
