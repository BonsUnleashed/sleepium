package dev.probe.api;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Run code with owner-thread side effects on another thread. The work runs on the calling (pool) thread with a journal
 * open; every call inside it that touches shared state asks {@link #defer} first, which records the call instead of
 * running it. The owner thread then {@link Recorded#publish() publishes}: the recorded calls run in the order they
 * were made, then the result is returned — or the work's exception is rethrown there, after the calls recorded before
 * it, exactly where the owner would have seen it had it run the work itself.
 *
 * <pre>{@code
 * // in a mixin on the method that runs on the pool:
 * @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "...Registry;register(...)V"))
 * private static void defer(Registry r, Object key, Operation<Void> original) {
 *     if (!SideEffectJournal.defer(() -> original.call(r, key))) original.call(r, key);
 * }
 *
 * // pool thread, then owner thread:
 * SideEffectJournal.Recorded<Data> rec = SideEffectJournal.run(() -> Loader.load(tag));
 * server.execute(() -> use(rec.publish()));
 * }</pre>
 *
 * The work itself must be safe off the owner thread apart from the deferred calls: it reads its own inputs (a copy, a
 * snapshot) and builds its own objects. probe's saved-chunk reads (ChunkSerializer.read on the pool) use this.
 */
public final class SideEffectJournal {
    private static final ThreadLocal<ArrayList<Runnable>> CURRENT = new ThreadLocal<>();

    private SideEffectJournal() {}

    /** The outcome of {@link #run}: the recorded calls and the result or exception. */
    public static final class Recorded<T> {
        private final ArrayList<Runnable> calls;
        private final T result;
        private final RuntimeException error;

        Recorded(ArrayList<Runnable> calls, T result, RuntimeException error) {
            this.calls = calls;
            this.result = result;
            this.error = error;
        }

        /** recorded calls, for statistics */
        public int calls() {
            return calls.size();
        }

        /** owner thread, once: run the recorded calls in order, then return the result or rethrow the exception */
        public T publish() {
            for (int i = 0; i < calls.size(); i++) calls.get(i).run();
            if (error != null) throw error;
            return result;
        }
    }

    /** any thread: run {@code work} with a journal open on this thread */
    public static <T> Recorded<T> run(Supplier<T> work) {
        ArrayList<Runnable> calls = new ArrayList<>(32);
        ArrayList<Runnable> outer = CURRENT.get();
        CURRENT.set(calls);
        T result = null;
        RuntimeException error = null;
        try {
            result = work.get();
        } catch (RuntimeException e) {
            error = e;
        } finally {
            if (outer != null) CURRENT.set(outer);
            else CURRENT.remove();
        }
        return new Recorded<>(calls, result, error);
    }

    /** inside the work: record {@code call} if a journal is open on this thread; false = none open, run it now */
    public static boolean defer(Runnable call) {
        ArrayList<Runnable> calls = CURRENT.get();
        if (calls == null) return false;
        calls.add(call);
        return true;
    }

    /** whether a journal is open on this thread */
    public static boolean recording() {
        return CURRENT.get() != null;
    }
}
