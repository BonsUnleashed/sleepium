package dev.probe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cross-thread task traffic, fed by the mixins in {@code dev.probe.mixin}.
 * <ul>
 *   <li>event loops ({@code BlockableEventLoop.tell}): every call does an unconditional
 *       {@code LockSupport.unpark} – a SetEvent syscall on Windows – so "tells" == unparks</li>
 *   <li>mailboxes ({@code ProcessorMailbox}): tasks told vs {@code run()} dispatches to the executor;
 *       vanilla runs exactly one task per dispatch, so the ratio shows how much batching is lost</li>
 * </ul>
 * Always on (a LongAdder increment is a few ns), reset by {@code /probe start}.
 */
public final class TaskStats {
    public static final class Loop {
        public final LongAdder tellsFromOwner = new LongAdder();
        public final LongAdder tellsFromOther = new LongAdder();
    }

    public static final class Mailbox {
        public final LongAdder tasks = new LongAdder();
        public final LongAdder runs = new LongAdder();
    }

    private static final Map<String, Loop> LOOPS = new ConcurrentHashMap<>();
    private static final Map<String, Mailbox> MAILBOXES = new ConcurrentHashMap<>();

    private TaskStats() {}

    public static Loop loop(String name) {
        return LOOPS.computeIfAbsent(name, k -> new Loop());
    }

    public static Mailbox mailbox(String name) {
        // "chat stream for <player>" -> one bucket
        String key = name.startsWith("chat stream for ") ? "chat stream for *" : name;
        return MAILBOXES.computeIfAbsent(key, k -> new Mailbox());
    }

    static void reset() {
        for (Loop l : LOOPS.values()) {
            l.tellsFromOwner.reset();
            l.tellsFromOther.reset();
        }
        for (Mailbox m : MAILBOXES.values()) {
            m.tasks.reset();
            m.runs.reset();
        }
        dev.probe.optsupport.WakeFlags.UNPARKED.reset();
        dev.probe.optsupport.WakeFlags.SKIPPED.reset();
    }

    static String report(double seconds, long ticks) {
        StringBuilder sb = new StringBuilder("## Cross-thread tasks\n");
        sb.append(String.format(java.util.Locale.ROOT, "%-28s %12s %12s %12s %12s%n",
                "event loop", "tells/s", "per tick", "from owner", "from other"));
        List<Map.Entry<String, Loop>> loops = new ArrayList<>(LOOPS.entrySet());
        loops.sort(Comparator.comparingLong(e -> -(e.getValue().tellsFromOwner.sum() + e.getValue().tellsFromOther.sum())));
        for (Map.Entry<String, Loop> e : loops) {
            long own = e.getValue().tellsFromOwner.sum(), other = e.getValue().tellsFromOther.sum();
            sb.append(String.format(java.util.Locale.ROOT, "%-28s %,12.0f %,12.1f %,12d %,12d%n",
                    e.getKey(), (own + other) / seconds, (double) (own + other) / ticks, own, other));
        }
        sb.append(String.format(java.util.Locale.ROOT, "%n%-28s %12s %12s %12s %12s%n",
                "mailbox", "tasks/s", "per tick", "dispatches/s", "tasks/disp"));
        List<Map.Entry<String, Mailbox>> boxes = new ArrayList<>(MAILBOXES.entrySet());
        boxes.sort(Comparator.comparingLong(e -> -e.getValue().tasks.sum()));
        for (Map.Entry<String, Mailbox> e : boxes) {
            long tasks = e.getValue().tasks.sum(), runs = e.getValue().runs.sum();
            sb.append(String.format(java.util.Locale.ROOT, "%-28s %,12.0f %,12.1f %,12.0f %12.2f%n",
                    e.getKey(), tasks / seconds, (double) tasks / ticks, runs / seconds, runs == 0 ? 0.0 : (double) tasks / runs));
        }
        long up = dev.probe.optsupport.WakeFlags.UNPARKED.sum(), sk = dev.probe.optsupport.WakeFlags.SKIPPED.sum();
        if (up + sk > 0) {
            sb.append(String.format(java.util.Locale.ROOT, "CondWake active: real unparks %,d, skipped %,d (%.1f %% saved)%n",
                    up, sk, 100.0 * sk / (up + sk)));
        }
        sb.append("'tells' on an event loop = LockSupport.unpark calls (SetEvent on Windows), unconditional unless CondWake is on.\n");
        return sb.toString();
    }

    static long totalLoopTells() {
        long s = 0;
        for (Loop l : LOOPS.values()) {
            s += l.tellsFromOwner.sum() + l.tellsFromOther.sum();
        }
        return s;
    }

    static long totalMailboxTasks() {
        long s = 0;
        for (Mailbox m : MAILBOXES.values()) {
            s += m.tasks.sum();
        }
        return s;
    }
}
