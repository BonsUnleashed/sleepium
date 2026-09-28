package dev.probe.optsupport;

import dev.lab.runtime.LabRuntime;
import dev.lab.runtime.OsThreads;

/**
 * The server's pool for work taken off the main thread (the counterpart of the client's FrameReactor): chunk save
 * encoding first; light, generation and chunk-tick jobs are to follow on the same workers.
 */
public final class ServerReactor {
    private ServerReactor() {}

    private static final int THREADS = Integer.getInteger("lab.sreactor.threads", Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2)));
    private static LabRuntime pool;

    public static synchronized LabRuntime pool() {
        if (pool == null) {
            pool = new LabRuntime(LabRuntime.Config.of("Server-Worker", THREADS).withPriority(OsThreads.NORMAL).withDaemon(true));
            System.out.println("[probe/opt] server reactor: " + THREADS + " workers");
        }
        return pool;
    }
}
