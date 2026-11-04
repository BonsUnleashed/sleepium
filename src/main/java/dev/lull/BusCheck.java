package dev.lull;

import dev.lull.optsupport.CompositeEventBus;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.GenericEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The composite bus against Forge's own (dev runs: /sleep buscheck): the same events posted through CompositeEventBus
 * (generated dispatcher, short-list loop, no-listener path) and through its delegate must call the same listeners in
 * the same order and return the same result: priorities, cancellation mid-dispatch, receiveCanceled, @SubscribeEvent
 * and addListener mixed, a generic filter, a parent type's listener, the phase the event is left in.
 */
public final class BusCheck {
    @Cancelable
    public static class OrderEvent extends Event {
        public int id;
        public final StringBuilder trace = new StringBuilder();
    }

    /** a subclass: its list holds OrderEvent's listeners too */
    public static final class SubOrderEvent extends OrderEvent {}

    /** Plain (non-cancellable) event: priority order, consumer + @SubscribeEvent mixing. */
    public static final class PlainEvent extends Event {
        public final StringBuilder trace = new StringBuilder();
    }

    public static final class TypedEvent<T> extends GenericEvent<T> {
        public final StringBuilder trace = new StringBuilder();

        public TypedEvent(Class<T> type) {
            super(type);
        }
    }

    /** one listener (and its priority's phase marker): the short-list loop */
    public static final class SingleEvent extends Event {
        public final StringBuilder trace = new StringBuilder();
    }

    /** nobody listens */
    public static final class LonelyEvent extends Event {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void a(OrderEvent e) {
        e.trace.append('A');
        if (e.id == 1) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void b(OrderEvent e) {
        e.trace.append('B');                        // skipped once cancelled
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void c(OrderEvent e) {
        e.trace.append('C').append(e.getPhase().ordinal());   // always runs; the phase it sees
        if (e.id == 2) e.setCanceled(true);         // cancels late: D must be skipped, E must run
    }

    @SubscribeEvent
    public static void s(SubOrderEvent e) {
        e.trace.append('S');
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void p2(PlainEvent e) {
        e.trace.append('2');
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void p4(PlainEvent e) {
        e.trace.append('4');
    }

    private static boolean registered;

    private BusCheck() {}

    static synchronized void register(IEventBus bus) {
        if (registered) return;
        registered = true;
        bus.register(BusCheck.class);
        bus.addListener(EventPriority.LOWEST, false, OrderEvent.class, e -> e.trace.append('D'));
        bus.addListener(EventPriority.LOWEST, true, OrderEvent.class, e -> e.trace.append('E'));
        bus.addListener(EventPriority.HIGHEST, false, PlainEvent.class, e -> e.trace.append('1'));
        bus.addListener(EventPriority.NORMAL, false, PlainEvent.class, e -> e.trace.append('3'));
        addTyped(bus, String.class, EventPriority.HIGH, 's');
        addTyped(bus, Integer.class, EventPriority.NORMAL, 'i');
        addTyped(bus, String.class, EventPriority.LOW, 'z');
        bus.addListener(EventPriority.NORMAL, false, SingleEvent.class, e -> e.trace.append('x'));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTyped(IEventBus bus, Class<?> filter, EventPriority priority, char mark) {
        java.util.function.Consumer<TypedEvent<?>> c = e -> e.trace.append(mark);
        bus.addGenericListener((Class) filter, priority, false, (Class) TypedEvent.class, (java.util.function.Consumer) c);
    }

    /** failures; empty = equivalent */
    static List<String> check() {
        List<String> fails = new ArrayList<>();
        IEventBus bus = MinecraftForge.EVENT_BUS;
        if (!(bus instanceof CompositeEventBus cb)) {
            fails.add("MinecraftForge.EVENT_BUS is not the composite bus (lab.opt.ForgeBusMixin off?)");
            return fails;
        }
        register(bus);
        IEventBus vanilla = cb.delegate();
        for (int round = 0; round < 3; round++) {   // the first post builds the dispatchers, the later ones use them
            for (int id = 0; id <= 2; id++) {
                for (int sub = 0; sub < 2; sub++) {
                    OrderEvent x = sub == 0 ? new OrderEvent() : new SubOrderEvent(), y = sub == 0 ? new OrderEvent() : new SubOrderEvent();
                    x.id = y.id = id;
                    compare(fails, (sub == 0 ? "OrderEvent" : "SubOrderEvent") + " id=" + id, bus.post(x), vanilla.post(y),
                            x.trace + "/" + x.isCanceled() + "/" + x.getPhase(), y.trace + "/" + y.isCanceled() + "/" + y.getPhase());
                }
            }
            PlainEvent p = new PlainEvent(), q = new PlainEvent();
            compare(fails, "PlainEvent", bus.post(p), vanilla.post(q), p.trace + "/" + p.getPhase(), q.trace + "/" + q.getPhase());
            for (Class<?> t : new Class<?>[] {String.class, Integer.class, Long.class}) {
                TypedEvent<?> m = new TypedEvent<>(t), n = new TypedEvent<>(t);
                compare(fails, "TypedEvent<" + t.getSimpleName() + ">", bus.post(m), vanilla.post(n), m.trace.toString(), n.trace.toString());
            }
            SingleEvent r = new SingleEvent(), w = new SingleEvent();
            compare(fails, "SingleEvent", bus.post(r), vanilla.post(w), r.trace + "/" + r.getPhase(), w.trace + "/" + w.getPhase());
            LonelyEvent l = new LonelyEvent(), k = new LonelyEvent();
            compare(fails, "LonelyEvent", bus.post(l), vanilla.post(k), String.valueOf(l.getPhase()), String.valueOf(k.getPhase()));
        }
        if (cb.hasListeners(LonelyEvent.class)) fails.add("hasListeners(LonelyEvent) = true");
        if (!cb.hasListeners(SubOrderEvent.class) || !cb.hasListeners(SingleEvent.class)) fails.add("hasListeners misses listeners");
        return fails;
    }

    private static void compare(List<String> fails, String what, boolean r1, boolean r2, String t1, String t2) {
        if (r1 != r2 || !t1.equals(t2)) fails.add(what + ": composite " + t1 + " -> " + r1 + " vs bus " + t2 + " -> " + r2);
    }

    static String expected() {
        return "id=0 ABC3DE, id=1 AC3E (cancelled), id=2 ABC3E (cancelled); SubOrderEvent adds S; plain 1234; typed<String> sz, typed<Integer> i, typed<Long> nothing; single x";
    }
}
