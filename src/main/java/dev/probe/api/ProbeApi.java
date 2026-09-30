package dev.probe.api;

/** Entry point of the public API: version, active features, waking the server thread. */
public final class ProbeApi {
    /** grows by one whenever something is added to {@code dev.probe.api} */
    public static final int VERSION = 2;

    private ProbeApi() {}

    /**
     * Whether a feature is on in this JVM. Flags are JVM properties read at start-up, so the answer does not change
     * while the game runs.
     */
    public static boolean isEnabled(Feature f) {
        return Boolean.getBoolean("lab.opt." + f.flag()) || f.inAll() && Boolean.getBoolean("lab.opt.all");
    }

    /**
     * Any thread: wake the server thread if it sleeps between ticks waiting for work (MainReactor). Work posted with
     * {@code server.execute(..)} already wakes it; call this after putting work somewhere the server thread polls by
     * itself. A no-op when the reactor is off or the thread is busy.
     */
    public static void wakeServerThread() {
        dev.probe.optsupport.MainReactor.signal();
    }
}
