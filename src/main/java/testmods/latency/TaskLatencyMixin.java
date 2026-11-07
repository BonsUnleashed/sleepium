package testmods.latency;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test only (-Dsleep.test.taskLatency=true): how long a task handed to the server from another thread (as netty hands
 * it every player packet) waits before the server thread runs it. A probe thread posts one every 20-80 ms through
 * MinecraftServer.execute and logs the waits every 10 s. A server thread asleep until the next tick shows up as waits
 * spread up to 50 ms; one woken by each task as waits well under a millisecond.
 */
@Mixin(MinecraftServer.class)
public abstract class TaskLatencyMixin {
    @Unique private static volatile boolean sleep$started;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void sleep$probe(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (sleep$started) return;
        sleep$started = true;
        MinecraftServer server = (MinecraftServer) (Object) this;
        Thread t = new Thread(() -> {
            long[] waits = new long[4096];
            int n = 0;
            long report = System.nanoTime() + 10_000_000_000L;
            while (server.isRunning()) {
                try {
                    Thread.sleep(20 + ThreadLocalRandom.current().nextInt(60));
                } catch (InterruptedException e) {
                    return;
                }
                long t0 = System.nanoTime();
                long[] done = {0};
                server.execute(() -> done[0] = System.nanoTime());
                long limit = t0 + 1_000_000_000L;
                while (done[0] == 0 && System.nanoTime() < limit) Thread.onSpinWait();
                if (done[0] != 0 && n < waits.length) waits[n++] = done[0] - t0;
                if (System.nanoTime() > report && n > 0) {
                    long[] w = Arrays.copyOf(waits, n);
                    Arrays.sort(w);
                    double mean = Arrays.stream(w).average().orElse(0) / 1e6;
                    System.out.printf(java.util.Locale.ROOT, "[sleep/test] task latency: n=%d mean %.3f ms, p50 %.3f, p95 %.3f, max %.3f%n",
                            n, mean, w[n / 2] / 1e6, w[n * 95 / 100] / 1e6, w[n - 1] / 1e6);
                    n = 0;
                    report = System.nanoTime() + 10_000_000_000L;
                }
            }
        }, "sleep-task-latency-probe");
        t.setDaemon(true);
        t.start();
    }
}
