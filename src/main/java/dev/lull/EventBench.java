package dev.lull;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Microbenchmark of {@code IEventBus.post} run on the live game bus, on the server thread.
 *
 * <p>Why in-game and not JMH: inside the running game the dispatch site
 * {@code listeners[i].invoke(event)} in EventBus has already seen hundreds of listener classes
 * (megamorphic), so the numbers here include the real virtual-call and cache behaviour. A standalone
 * JMH run would see one listener class and let the JIT inline it.
 *
 * <p>Each case reports ns/op and bytes allocated per op (server thread TLAB accounting).
 *
 * <p>1.20.1: on MinecraftForge.EVENT_BUS (CompositeEventBus when ForgeBusMixin is on, else Forge's EventBus); a
 * cancellable event is {@code @Cancelable} (1.21.1: ICancellableEvent).
 */
final class EventBench {
    /** No listeners at all: the cost of a Forge hook nobody subscribed to. */
    public static final class EmptyEvent extends Event {}

    /** One no-op listener. */
    public static final class OneListenerEvent extends Event {}

    /** One no-op listener, cancellable: the bus wraps listeners in an isCanceled() check. */
    @Cancelable
    public static final class CancellableEvent extends Event {}

    private static volatile long sink;
    private static volatile Consumer<Object> directTarget = o -> sink++;

    private static IEventBus bus;

    private EventBench() {}

    static void registerListeners(IEventBus gameBus) {
        bus = gameBus;
        gameBus.addListener(EventPriority.NORMAL, false, OneListenerEvent.class, e -> sink++);
        gameBus.addListener(EventPriority.NORMAL, false, CancellableEvent.class, e -> sink++);
        gameBus.register(ScaleListeners.class);   // 211 distinct static listener methods
        ScaleListeners.registerConsumers(gameBus); // 100 distinct lambda listeners
        // (1.21.1 also registers BusEquivalence's listeners: on 1.20.1 BusCheck registers its own, /sleep buscheck)
    }

    record Result(String name, double nsPerOp, double bytesPerOp) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%-46s %9.1f ns/op %8.1f B/op", name, nsPerOp, bytesPerOp);
        }
    }

    interface Case {
        void run(int n);
    }

    static List<Result> run(int n) {
        EmptyEvent reusedEmpty = new EmptyEvent();

        List<Object[]> cases = List.of(
                new Object[] {"baseline: Consumer.accept (no bus)", (Case) k -> {
                    Consumer<Object> t = directTarget;
                    for (int i = 0; i < k; i++) t.accept(reusedEmpty);
                }},
                new Object[] {"post(new EmptyEvent())      0 listeners", (Case) k -> {
                    for (int i = 0; i < k; i++) bus.post(new EmptyEvent());
                }},
                new Object[] {"post(reused EmptyEvent)     0 listeners", (Case) k -> {
                    for (int i = 0; i < k; i++) bus.post(reusedEmpty);
                }},
                new Object[] {"post(new OneListenerEvent())  1 listener", (Case) k -> {
                    for (int i = 0; i < k; i++) bus.post(new OneListenerEvent());
                }},
                // (1.21.1 also posts a reused OneListenerEvent: Forge's Event.setPhase refuses a second pass through the
                // same phase - "already NORMAL" - so an event with listeners cannot be posted twice on 1.20.1)
                new Object[] {"post(new CancellableEvent())  1 listener", (Case) k -> {
                    for (int i = 0; i < k; i++) bus.post(new CancellableEvent());
                }},
                // N distinct listener classes (like N mods), each a cheap "is this mine?" filter
                new Object[] {"post  1 distinct listeners", (Case) k -> {
                    for (int i = 0; i < k; i++) { var e = new ScaleListeners.Scale1(); e.id = i; bus.post(e); }
                }},
                new Object[] {"post 10 distinct listeners", (Case) k -> {
                    for (int i = 0; i < k; i++) { var e = new ScaleListeners.Scale10(); e.id = i; bus.post(e); }
                }},
                new Object[] {"post 50 distinct listeners", (Case) k -> {
                    for (int i = 0; i < k; i++) { var e = new ScaleListeners.Scale50(); e.id = i; bus.post(e); }
                }},
                new Object[] {"post 100 distinct listeners", (Case) k -> {
                    for (int i = 0; i < k; i++) { var e = new ScaleListeners.Scale100(); e.id = i; bus.post(e); }
                }},
                new Object[] {"post 50 distinct listeners, cancellable", (Case) k -> {
                    for (int i = 0; i < k; i++) { var e = new ScaleListeners.Scale50Cancellable(); e.id = i; bus.post(e); }
                }},
                new Object[] {"baseline: 100 direct static calls", (Case) k -> {
                    for (int i = 0; i < k; i++) { for (int j = 0; j < 100; j++) if (i == 1000 + j) sink++; }
                }});

        // Warm-up: let C2 compile every loop before measuring.
        for (int round = 0; round < 3; round++) {
            for (Object[] c : cases) {
                ((Case) c[1]).run(Math.max(1, n / 5));
            }
        }

        List<Result> results = new ArrayList<>();
        for (Object[] c : cases) {
            Case body = (Case) c[1];
            // Best of 5 runs: the minimum is the least disturbed by GC and other threads.
            double bestNs = Double.MAX_VALUE;
            double bytesAtBest = 0;
            for (int rep = 0; rep < 5; rep++) {
                long a0 = ThreadAlloc.current();
                long t0 = System.nanoTime();
                body.run(n);
                long t1 = System.nanoTime();
                long a1 = ThreadAlloc.current();
                double ns = (double) (t1 - t0) / n;
                if (ns < bestNs) {
                    bestNs = ns;
                    bytesAtBest = ThreadAlloc.supported() ? (double) (a1 - a0) / n : Double.NaN;
                }
            }
            results.add(new Result((String) c[0], bestNs, bytesAtBest));
        }
        return results;
    }
}
