package dev.probe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.MinecraftServer;

/** Measurement only (-Dprobe.ioScout=true): how long a whole-world save takes (exit, autosave, /save-all), and GC in it. */
@Mixin(MinecraftServer.class)
public abstract class IoScoutSaveMixin {
    @Unique private long probe$t0, probe$gc0, probe$gcMs0;

    @Inject(method = "saveAllChunks", at = @At("HEAD"))
    private void probe$start(boolean suppressLog, boolean flush, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        probe$t0 = System.nanoTime();
        long[] g = probe$gc();
        probe$gc0 = g[0];
        probe$gcMs0 = g[1];
    }

    @Inject(method = "saveAllChunks", at = @At("RETURN"))
    private void probe$end(boolean suppressLog, boolean flush, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        long[] g = probe$gc();
        var h = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        System.out.println(String.format(java.util.Locale.ROOT, "[probe/io] saveAllChunks (flush %b, forced %b): %.1f ms, GC %d (%d ms), heap used %d MB",
                flush, forced, (System.nanoTime() - probe$t0) / 1e6, g[0] - probe$gc0, g[1] - probe$gcMs0, h.getUsed() >> 20));
    }

    @Unique
    private static long[] probe$gc() {
        long c = 0, ms = 0;
        for (var b : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            c += Math.max(0, b.getCollectionCount());
            ms += Math.max(0, b.getCollectionTime());
        }
        return new long[] {c, ms};
    }
}
