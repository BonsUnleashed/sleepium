package dev.lull.optsupport;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Allocation-free events nobody listens to (EventSiteMixin rewrites {@code MinecraftForge.EVENT_BUS.post(new E(...))}
 * in Forge's hook classes into an invokedynamic bound here, one call site per place).
 *
 * <p>Each site is a MutableCallSite. While type E has no listener, its target is the bus' answer for an empty listener
 * list ({@code isCancelable() && isCanceled()}, false for a fresh event): the JIT sees a constant target, inlines it,
 * and escape analysis removes the {@code new E(...)} at the caller altogether. Otherwise the target is the normal
 * {@code bus.post(event)}. Any listener registration anywhere switches every fast site back to the normal target first
 * (CompositeEventBus calls {@link #invalidate}); a site becomes fast again by itself after a post that finds no
 * listener. Only when MinecraftForge.EVENT_BUS is our CompositeEventBus.
 */
public final class EventSites {
    private static final List<Site> SITES = new ArrayList<>();
    private static final MethodHandle SLOW, FAST;
    public static volatile long sites, fastSwitches, invalidations;

    static {
        try {
            SLOW = MethodHandles.lookup().findStatic(EventSites.class, "slow",
                    MethodType.methodType(boolean.class, Site.class, IEventBus.class, Event.class));
            FAST = MethodHandles.lookup().findStatic(EventSites.class, "fast",
                    MethodType.methodType(boolean.class, IEventBus.class, Event.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private EventSites() {}

    static final class Site {
        final Class<?> type;
        final MutableCallSite cs;
        final MethodHandle fast, slow;
        volatile boolean isFast;

        Site(Class<?> type, MethodType mt) {
            this.type = type;
            this.cs = new MutableCallSite(mt);
            this.fast = FAST.asType(mt);
            this.slow = MethodHandles.insertArguments(SLOW, 0, this).asType(mt);
            cs.setTarget(slow);
        }
    }

    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> eventType) {
        Site s = new Site(eventType, type);
        synchronized (SITES) {
            SITES.add(s);
            sites = SITES.size();
        }
        return s.cs;
    }

    /** what EventBus.post returns for an event no listener sees */
    private static boolean fast(IEventBus bus, Event event) {
        return event.isCanceled() && event.isCancelable();
    }

    /** The normal post; afterwards the site turns fast if this type has (now) no listener at all. */
    private static boolean slow(Site s, IEventBus bus, Event event) {
        boolean r = bus.post(event);
        if (!s.isFast && bus == MinecraftForge.EVENT_BUS && bus instanceof CompositeEventBus cb && !cb.hasListeners(s.type)) {
            synchronized (SITES) {
                if (!cb.hasListeners(s.type)) {   // re-check under the lock that invalidate() takes
                    s.cs.setTarget(s.fast);
                    s.isFast = true;
                    MutableCallSite.syncAll(new MutableCallSite[] {s.cs});
                    fastSwitches++;
                }
            }
        }
        return r;
    }

    /** A listener was added or removed somewhere: every fast site back to the normal post (before it can be missed). */
    public static void invalidate() {
        synchronized (SITES) {
            List<MutableCallSite> changed = new ArrayList<>();
            for (Site s : SITES) {
                if (s.isFast) {
                    s.cs.setTarget(s.slow);
                    s.isFast = false;
                    changed.add(s.cs);
                }
            }
            if (!changed.isEmpty()) {
                MutableCallSite.syncAll(changed.toArray(new MutableCallSite[0]));
                invalidations++;
            }
        }
    }

    /**
     * The events posted from inside a (non-player: players stay on the render thread) entity's rendering; in a holder,
     * loaded on the first ask (client classes: a dedicated server never loads them).
     */
    private static final class RenderEvents {
        static final Class<?>[] ENTITY_RENDER_EVENTS = {net.minecraftforge.client.event.RenderLivingEvent.Pre.class,
            net.minecraftforge.client.event.RenderLivingEvent.Post.class, net.minecraftforge.client.event.RenderNameTagEvent.class,
            net.minecraftforge.client.event.RenderItemInFrameEvent.class};
    }
    /**
     * One of them has a listener now, asked of the bus (a mod's listener must not run on a worker); cheap, callers ask
     * once per frame (listeners can be added at any time).
     */
    /** RenderNameTagEvent has a listener now (NameTagSkipMixin, client only; asked every call: a mod may register one later) */
    public static boolean nameTagListened() {
        return !(MinecraftForge.EVENT_BUS instanceof CompositeEventBus cb) || cb.mayHaveListeners(net.minecraftforge.client.event.RenderNameTagEvent.class);
    }

    public static boolean renderListened() {
        if (!(MinecraftForge.EVENT_BUS instanceof CompositeEventBus cb)) return true;
        for (Class<?> c : RenderEvents.ENTITY_RENDER_EVENTS) if (cb.mayHaveListeners(c)) return true;
        return false;
    }

    public static String report() {
        int fast = 0;
        java.util.Set<String> slow = new java.util.TreeSet<>();
        synchronized (SITES) {
            for (Site s : SITES) {
                if (s.isFast) fast++;
                else slow.add(s.type.getName().substring(s.type.getName().lastIndexOf('.') + 1));
            }
        }
        return "event sites " + sites + " (fast now " + fast + "), fast switches " + fastSwitches + ", invalidations " + invalidations
                + ", listened " + slow;
    }
}
