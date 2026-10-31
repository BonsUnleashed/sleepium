package dev.sleep.api;

import dev.lab.runtime.LabRuntime;
import dev.lab.runtime.OsThreads;
import java.util.concurrent.ExecutorService;
import net.minecraft.Util;

/**
 * Worker pools. Prefer the shared background pool: with {@link Feature#LAB_POOLS} on it is a {@link LabRuntime}
 * (persistent threads, work stealing, parked when idle), otherwise vanilla's ForkJoinPool — either way the right
 * place for CPU work that must not run on the server or render thread. A mod that needs its own threads (other
 * priority, isolation from generation) makes one pool once with {@link #create}.
 */
public final class Pools {
    private Pools() {}

    /** the shared CPU pool (vanilla's "Worker-Main") */
    public static ExecutorService background() {
        return Util.backgroundExecutor();
    }

    /** the shared IO pool (vanilla's "IO-Worker"): blocking file or network work only */
    public static ExecutorService io() {
        return Util.ioPool();
    }

    /** true when {@link #background()} is a LabRuntime */
    public static boolean backgroundIsLabRuntime() {
        return Util.backgroundExecutor() instanceof LabRuntime;
    }

    /**
     * A new persistent pool: {@code workers} threads (1..16) named {@code "<name>-N"}, below-normal OS priority,
     * daemon; a task's exception is logged and the worker keeps running. Make it once and keep it.
     */
    public static LabRuntime create(String name, int workers) {
        return create(name, workers, OsThreads.BELOW_NORMAL);
    }

    /** as {@link #create(String, int)} with an OS priority: {@link OsThreads#BELOW_NORMAL}, {@code NORMAL}, {@code ABOVE_NORMAL} */
    public static LabRuntime create(String name, int workers, int osPriority) {
        return new LabRuntime(LabRuntime.Config.of(name, workers).withPriority(osPriority).withDaemon(true)
                .withHandler((t, e) -> org.slf4j.LoggerFactory.getLogger("probe").error("task failed on {}", t.getName(), e)));
    }
}
