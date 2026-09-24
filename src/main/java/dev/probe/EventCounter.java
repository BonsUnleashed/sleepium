package dev.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

/**
 * Counts posts of every concrete event type on the game bus, without a hand-written list.
 *
 * <p>Discovery: {@code EventBus.listenerLists} (a {@code LockHelper<Class, ListenerList>}) gets an entry the
 * first time a type is posted or listened to. The bus module is declared {@code open}, so plain reflection
 * works. Every second we look for new keys and attach a LOWEST-priority, receiveCanceled listener to each.
 * A type posted for the very first time is therefore missed for at most ~1 s.
 *
 * <p>Side effect to keep in mind: while counting, every event has at least one listener, so this mode
 * measures <b>how often</b> events fire, not how much a listener-free post costs (that is what
 * {@code /probe bench} is for). {@link #stop()} detaches all counting listeners again.
 */
final class EventCounter {
    static final EventCounter INSTANCE = new EventCounter();

    private static final int DISCOVERY_PERIOD_TICKS = 20;

    static final class Counter {
        final Class<?> type;
        final LongAdder serverThread = new LongAdder();
        final LongAdder otherThreads = new LongAdder();
        Consumer<? extends Event> listener;
        boolean rejected;
        /** Listeners on this exact type at stop time, excluding our own counting listener. -1 = unknown. */
        int listeners = -1;

        Counter(Class<?> type) {
            this.type = type;
        }

        long total() {
            return serverThread.sum() + otherThreads.sum();
        }
    }

    private final Map<Class<?>, Counter> counters = new ConcurrentHashMap<>();
    private volatile boolean enabled;
    private volatile Thread serverThread;
    private IEventBus bus;
    private Object listenerLists;
    private Method getReadMap;
    private int tickCounter;
    private long startNanos;
    private long stopNanos;

    private EventCounter() {}

    void attach(IEventBus bus) {
        this.bus = bus;
        try {
            Field f = bus.getClass().getDeclaredField("listenerLists");
            f.setAccessible(true);
            listenerLists = f.get(bus);
            getReadMap = listenerLists.getClass().getDeclaredMethod("getReadMap");
            getReadMap.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ProbeMod.LOGGER.error("Cannot reach EventBus internals ({}); event counting disabled", bus.getClass(), e);
            listenerLists = null;
        }
    }

    boolean available() {
        return listenerLists != null;
    }

    boolean enabled() {
        return enabled;
    }

    /** Server thread, from ServerTickEvent.Pre. */
    void onServerTick(Thread thread) {
        serverThread = thread;
        if (enabled && ++tickCounter % DISCOVERY_PERIOD_TICKS == 0) {
            discover();
        }
    }

    void start() {
        if (!available()) {
            return;
        }
        for (Counter c : counters.values()) {
            c.serverThread.reset();
            c.otherThreads.reset();
        }
        startNanos = System.nanoTime();
        enabled = true;
        discover();
    }

    void stop() {
        enabled = false;
        stopNanos = System.nanoTime();
        captureListenerCounts();
        for (Counter c : counters.values()) {
            if (c.listener != null) {
                bus.unregister(c.listener);
                c.listener = null;
            }
        }
    }

    double windowSeconds() {
        long end = enabled ? System.nanoTime() : stopNanos;
        return Math.max(1e-9, (end - startNanos) / 1e9);
    }

    int trackedTypes() {
        return (int) counters.values().stream().filter(c -> c.listener != null).count();
    }

    List<Counter> snapshotSorted() {
        List<Counter> list = new ArrayList<>(counters.values());
        list.removeIf(c -> c.total() == 0);
        list.sort(Comparator.comparingLong(Counter::total).reversed());
        return list;
    }

    private void discover() {
        Map<?, ?> map;
        try {
            map = (Map<?, ?>) getReadMap.invoke(listenerLists);
        } catch (ReflectiveOperationException e) {
            ProbeMod.LOGGER.error("EventBus discovery failed", e);
            return;
        }
        for (Object key : map.keySet().toArray()) {
            if (key instanceof Class<?> type && isCountable(type)) {
                Counter c = counters.computeIfAbsent(type, Counter::new);
                if (c.listener == null && !c.rejected) {
                    subscribe(c);
                }
            }
        }
    }

    /** ListenerList.getListeners() includes listeners of parent event types, i.e. what one post really invokes. */
    private void captureListenerCounts() {
        try {
            Map<?, ?> map = (Map<?, ?>) getReadMap.invoke(listenerLists);
            for (Counter c : counters.values()) {
                Object list = map.get(c.type);
                if (list != null) {
                    Object[] arr = (Object[]) list.getClass().getMethod("getListeners").invoke(list);
                    c.listeners = arr.length - (c.listener != null ? 1 : 0);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            ProbeMod.LOGGER.warn("Cannot read listener counts", e);
        }
    }

    private static boolean isCountable(Class<?> type) {
        return Event.class.isAssignableFrom(type)
                && !Modifier.isAbstract(type.getModifiers())
                && !type.getName().startsWith("dev.probe.");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void subscribe(Counter c) {
        Class type = c.type;
        Consumer<Event> listener = e -> {
            // Exact class match: a listener on a concrete parent also receives its subclasses.
            if (enabled && e.getClass() == type) {
                (Thread.currentThread() == serverThread ? c.serverThread : c.otherThreads).increment();
            }
        };
        try {
            bus.addListener(EventPriority.LOWEST, true, type, listener);
            c.listener = listener;
        } catch (IllegalArgumentException e) {
            // e.g. mod-bus-only event types rejected by the game bus class checker
            c.rejected = true;
        }
    }
}
