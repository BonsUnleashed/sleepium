package dev.sleep;

import dev.sleep.bus.CompositeDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Semantic equivalence check: the composite dispatcher must produce exactly the same listener call sequence as the
 * bus, including priority order, cancellation mid-dispatch and receiveCanceled, for both listener kinds.
 */
public final class BusEquivalence {
    public static final class OrderEvent extends Event implements ICancellableEvent {
        public int id;
        public final StringBuilder trace = new StringBuilder();
    }

    /** Plain (non-cancellable) event: checks priority order and consumer + @SubscribeEvent mixing. */
    public static final class PlainOrderEvent extends Event {
        public final StringBuilder trace = new StringBuilder();
    }

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
        e.trace.append('C');                        // always runs
        if (e.id == 2) e.setCanceled(true);         // cancels late: D must be skipped, E must run
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void p2(PlainOrderEvent e) {
        e.trace.append('2');
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void p4(PlainOrderEvent e) {
        e.trace.append('4');
    }

    private BusEquivalence() {}

    static void register(IEventBus bus) {
        bus.register(BusEquivalence.class);
        bus.addListener(EventPriority.LOWEST, false, OrderEvent.class, e -> e.trace.append('D'));
        bus.addListener(EventPriority.LOWEST, true, OrderEvent.class, e -> e.trace.append('E'));
        bus.addListener(EventPriority.HIGHEST, PlainOrderEvent.class, e -> e.trace.append('1'));
        bus.addListener(EventPriority.NORMAL, PlainOrderEvent.class, e -> e.trace.append('3'));
    }

    /** Returns failure descriptions; empty list = equivalent. */
    static List<String> check(IEventBus bus) throws ReflectiveOperationException {
        List<String> failures = new ArrayList<>();
        bus.post(new OrderEvent());
        bus.post(new PlainOrderEvent());
        CompositeDispatcher.Dispatch order = CompositeDispatcher.create(BusAnatomy.listenerList(bus, OrderEvent.class).getListeners());
        CompositeDispatcher.Dispatch plain = CompositeDispatcher.create(BusAnatomy.listenerList(bus, PlainOrderEvent.class).getListeners());
        for (int id = 0; id <= 2; id++) {
            OrderEvent viaBus = new OrderEvent(), viaComposite = new OrderEvent();
            viaBus.id = viaComposite.id = id;
            bus.post(viaBus);
            order.dispatch(viaComposite);
            String a = viaBus.trace + "/" + viaBus.isCanceled(), b = viaComposite.trace + "/" + viaComposite.isCanceled();
            if (!a.equals(b)) failures.add("OrderEvent id=" + id + ": bus " + a + " vs composite " + b);
        }
        PlainOrderEvent pb = new PlainOrderEvent(), pc = new PlainOrderEvent();
        bus.post(pb);
        plain.dispatch(pc);
        if (!pb.trace.toString().equals(pc.trace.toString())) failures.add("PlainOrderEvent: bus " + pb.trace + " vs composite " + pc.trace);
        return failures;
    }

    /** Expected sequences, for the report. */
    static String expected() {
        return "expected: id=0 ABCDE/false, id=1 ACE/true, id=2 ABCE/true, plain 1234";
    }
}
