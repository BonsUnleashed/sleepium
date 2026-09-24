package dev.probe.optsupport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Pool factories for PersistentPoolsMixin (must live outside the mixin package). */
public final class PersistentPools {
    /** IO: this many threads are pre-started and never time out; bursts may still add temporary ones. */
    private static final int IO_CORE = Integer.getInteger("lab.opt.ioThreads", 8);
    private static final long KEEP_ALIVE_DAYS = 365;

    private PersistentPools() {}

    private static void uncaught(Thread t, Throwable e) {
        System.err.println("[probe/opt] uncaught in " + t.getName() + ": " + e);
        e.printStackTrace();
    }

    public static ExecutorService io(String name, boolean daemon) {
        AtomicInteger n = new AtomicInteger(1);
        // Same hand-off semantics as newCachedThreadPool (SynchronousQueue, unbounded max),
        // but IO_CORE core threads that are pre-started and kept forever.
        ThreadPoolExecutor pool = new ThreadPoolExecutor(IO_CORE, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, name + n.getAndIncrement());
            t.setDaemon(daemon);
            t.setUncaughtExceptionHandler(PersistentPools::uncaught);
            return t;
        });
        pool.allowCoreThreadTimeOut(false);
        pool.prestartAllCoreThreads();
        System.out.println("[probe/opt] persistent IO pool '" + name + "': " + IO_CORE + " core threads");
        return pool;
    }

    /** Same parallelism and naming as vanilla Util.makeExecutor, but idle workers are never retired. */
    public static ExecutorService background(String serviceName) {
        int maxBg = Integer.getInteger("max.bg.threads", 255);
        int parallelism = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() - 1, maxBg));
        AtomicInteger n = new AtomicInteger(1);
        ForkJoinPool pool = new ForkJoinPool(parallelism, p -> {
            ForkJoinWorkerThread t = new ForkJoinWorkerThread(p) {};
            t.setName("Worker-" + serviceName + "-" + n.getAndIncrement());
            return t;
        }, PersistentPools::uncaught, true,
                parallelism,            // corePoolSize: keep this many
                parallelism + 256,      // maximumPoolSize (vanilla default spares)
                1, null,
                KEEP_ALIVE_DAYS, TimeUnit.DAYS);
        System.out.println("[probe/opt] persistent background pool 'Worker-" + serviceName + "': " + parallelism + " workers, keepAlive " + KEEP_ALIVE_DAYS + " d");
        return pool;
    }
}
