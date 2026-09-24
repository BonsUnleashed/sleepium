package dev.probe.optsupport;

import dev.probe.bus.CompositeDispatcher;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.IEventExceptionHandler;

/**
 * exp/bus-composite: wraps NeoForge.EVENT_BUS (installed by dev.probe.opt.NeoForgeBusMixin).
 *
 * <p>Registration, unregistration, start() and phase posts are delegated unchanged to the real EventBus.
 * {@link #post(Event)} dispatches through a {@link CompositeDispatcher} generated for the event's listener list.
 * A rebuilt list is detected by array identity: {@code ListenerList.getListeners()} returns a new array after any
 * (un)registration, so no invalidation hooks are needed.
 *
 * <p>Vanilla post semantics that are kept: the shutdown flag (read from the delegate), checkTypesOnDispatch (then the
 * wrapper simply falls back to the delegate), and the exception handler, called with index -1 because the unrolled
 * code does not know which listener threw.
 */
public final class CompositeEventBus implements IEventBus {
    private static final CompositeDispatcher.Dispatch EMPTY = e -> {};

    /** Lists this short use a plain loop (same cost as vanilla); longer ones get a generated composite. */
    static final int COMPOSITE_MIN = Integer.getInteger("lab.opt.compositeMin", 3);

    private record Built(ListenerList list, EventListener[] source, CompositeDispatcher.Dispatch dispatch) {}

    private static final class Holder {
        volatile Built built;   // one volatile read on the hot path
    }

    private final EventBus delegate;
    private final VarHandle shutdown;
    private final boolean fallbackAlways;
    private final IEventExceptionHandler exceptionHandler;
    private final Object listenerLists;
    private final Method getReadMap;
    private final ClassValue<Holder> holders = new ClassValue<>() {
        @Override
        protected Holder computeValue(Class<?> type) {
            return new Holder();
        }
    };

    public CompositeEventBus(IEventBus built) {
        this.delegate = (EventBus) built;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(EventBus.class, MethodHandles.lookup());
            this.shutdown = lookup.findVarHandle(EventBus.class, "shutdown", boolean.class);
            this.fallbackAlways = (boolean) lookup.findVarHandle(EventBus.class, "checkTypesOnDispatch", boolean.class).get(delegate);
            this.exceptionHandler = (IEventExceptionHandler) lookup.findVarHandle(EventBus.class, "exceptionHandler",
                    IEventExceptionHandler.class).get(delegate);
            var f = EventBus.class.getDeclaredField("listenerLists");
            f.setAccessible(true);
            this.listenerLists = f.get(delegate);
            this.getReadMap = listenerLists.getClass().getDeclaredMethod("getReadMap");
            this.getReadMap.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot wrap EventBus", e);
        }
        System.out.println("[probe/opt] NeoForge.EVENT_BUS wrapped by CompositeEventBus");
    }

    public EventBus delegate() {
        return delegate;
    }

    @Override
    public <T extends Event> T post(T event) {
        if (fallbackAlways || (boolean) shutdown.getVolatile(delegate)) {
            return delegate.post(event);   // identical vanilla behaviour (incl. returning early when shut down)
        }
        Holder h = holders.get(event.getClass());
        Built b = h.built;
        if (b == null) {
            ListenerList list = lookupList(event.getClass());
            if (list == null) {
                return delegate.post(event);   // first ever post of this type: let the bus create the list
            }
            b = build(h, list, list.getListeners());
        }
        EventListener[] current = b.list().getListeners();
        if (current != b.source()) {
            b = build(h, b.list(), current);   // listener set changed since the composite was generated
        }
        int n = current.length;
        if (n == 0) {
            return event;
        }
        if (n < COMPOSITE_MIN) {
            int i = 0;
            try {
                for (; i < n; i++) current[i].invoke(event);
            } catch (Throwable t) {
                exceptionHandler.handleException(this, event, current, i, t);
                throw t;
            }
            return event;
        }
        try {
            b.dispatch().dispatch(event);
        } catch (Throwable t) {
            exceptionHandler.handleException(this, event, current, -1, t);
            throw t;
        }
        return event;
    }

    private static Built build(Holder h, ListenerList list, EventListener[] current) {
        Built b = new Built(list, current, current.length < COMPOSITE_MIN ? EMPTY : CompositeDispatcher.create(current));
        h.built = b;
        return b;
    }

    private ListenerList lookupList(Class<?> type) {
        try {
            return (ListenerList) ((Map<?, ?>) getReadMap.invoke(listenerLists)).get(type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- everything else: straight delegation

    @Override public <T extends Event> T post(EventPriority phase, T event) { return delegate.post(phase, event); }
    @Override public void register(Object target) { delegate.register(target); }
    @Override public void unregister(Object object) { delegate.unregister(object); }
    @Override public void start() { delegate.start(); }
    @Override public <T extends Event> void addListener(Consumer<T> consumer) { delegate.addListener(consumer); }
    @Override public <T extends Event> void addListener(Class<T> eventType, Consumer<T> consumer) { delegate.addListener(eventType, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, Consumer<T> consumer) { delegate.addListener(priority, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, Class<T> eventType, Consumer<T> consumer) { delegate.addListener(priority, eventType, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Consumer<T> consumer) { delegate.addListener(priority, receiveCanceled, consumer); }
    @Override public <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Class<T> eventType, Consumer<T> consumer) { delegate.addListener(priority, receiveCanceled, eventType, consumer); }
    @Override public <T extends Event> void addListener(boolean receiveCanceled, Consumer<T> consumer) { delegate.addListener(receiveCanceled, consumer); }
    @Override public <T extends Event> void addListener(boolean receiveCanceled, Class<T> eventType, Consumer<T> consumer) { delegate.addListener(receiveCanceled, eventType, consumer); }
}
