package dev.probe.mixin;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Measurement only (-Dlab.pgen.trace=true, -Dlab.slowtick.ms=N, default 50): a server tick - its work and the wait for
 * the next one, where queued main-thread tasks run - longer than N ms is logged as its profiler tree (vanilla's
 * push/pop sections), every section with 5 % of the tick or more, in ms.
 */
@Mixin(MinecraftServer.class)
public abstract class GenTraceSlowTickMixin {
    @Shadow private ProfilerFiller profiler;
    @Shadow private int tickCount;
    @Unique private ActiveProfiler probe$active;
    @Unique private static final long probe$THRESHOLD = Long.getLong("lab.slowtick.ms", 50L) * 1_000_000L;

    @Inject(method = "startMetricsRecordingTick", at = @At("TAIL"))
    private void probe$start(CallbackInfo ci) {
        probe$active = new ActiveProfiler(net.minecraft.Util.timeSource, () -> this.tickCount, false);
        probe$active.startTick();
        profiler = ProfilerFiller.tee(probe$active, profiler);
    }

    @Inject(method = "endMetricsRecordingTick", at = @At("RETURN"))
    private void probe$end(CallbackInfo ci) {
        ActiveProfiler a = probe$active;
        probe$active = null;
        if (a == null) return;
        ProfileResults r = a.getResults();
        long ns = r.getNanoDuration();
        if (ns < probe$THRESHOLD) return;
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "[probe/slowtick] %.1f ms:", ns / 1e6));
        probe$walk(r, "root", ns, sb, 0);
        System.out.println(sb);
    }

    @Unique
    private static void probe$walk(ProfileResults r, String path, long rootNs, StringBuilder sb, int depth) {
        if (depth > 8) return;
        List<ResultField> fields = r.getTimes(path);
        for (int i = 1; i < fields.size(); i++) {
            ResultField f = fields.get(i);
            if (f.globalPercentage < 5) continue;
            sb.append("\n   ").append("  ".repeat(depth)).append(f.name).append(String.format(java.util.Locale.ROOT, " %.1f ms (x%d)", f.globalPercentage * rootNs / 1e8, f.count));
            if (!f.name.equals("unspecified")) probe$walk(r, path + '\u001e' + f.name, rootNs, sb, depth + 1);
        }
    }
}
