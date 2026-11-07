package dev.lull.optsupport;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-THREAD "I am about to park" flags for CondWakeMixin.
 *
 * <p>Keyed by thread, not by event loop: the server thread also drains the chunk-source main executor (another
 * BlockableEventLoop instance) while it waits, so a tell() to either loop must see the same flag.
 * A thread that never waits through BlockableEventLoop.waitForTasks (e.g. SoundEngineExecutor, which overrides it)
 * has no entry, and tell() falls back to the vanilla unconditional unpark.
 *
 * <p>Lives outside {@code dev.lull.opt}: Mixin forbids non-mixin classes inside a mixin package.
 */
public final class WakeFlags {
    private static final ConcurrentHashMap<Thread, AtomicBoolean> FLAGS = new ConcurrentHashMap<>();

    /** Real unparks issued vs. unparks skipped because the target thread was not parking. */
    public static final LongAdder UNPARKED = new LongAdder();
    public static final LongAdder SKIPPED = new LongAdder();

    private WakeFlags() {}

    public static AtomicBoolean current() {
        return FLAGS.computeIfAbsent(Thread.currentThread(), t -> new AtomicBoolean());
    }

    /** CondWake's hooks, also in the overrides of waitForTasks (OverrideHooks): about to wait for tasks / done */
    public static void arm() {
        current().set(true);
    }

    public static void disarm() {
        current().set(false);
    }

    public static AtomicBoolean of(Thread t) {
        return FLAGS.get(t);
    }
}
