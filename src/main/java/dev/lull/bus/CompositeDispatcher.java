package dev.lull.bus;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V17;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * One generated dispatcher per listener array, calls UNROLLED and @SubscribeEvent wrappers FLATTENED (Forge eventbus 6).
 *
 * <p>Vanilla bus: {@code for (i...) wrapper.invoke(listeners[i], event)} is ONE call site for every event type in the
 * game, so it is megamorphic and nothing behind it inlines. Worse, every @SubscribeEvent method sits behind a second
 * shared site: {@code ASMEventHandler.invoke -> handler.invoke} (with an annotation-proxy call to receiveCanceled()
 * for a cancelled event).
 *
 * <p>Here each listener gets its own call site in a hidden class generated for this array:
 * <ul>
 *   <li>ASMEventHandler without a generic filter: its inner generated handler is called directly; receiveCanceled is
 *       read ONCE and compiled in as {@code if (!event.isCanceled())} for a cancellable event</li>
 *   <li>EventPriority (a phase marker, with phase tracking on): kept, it sets the event's phase as on the bus</li>
 *   <li>anything else (addListener's lambdas, filtered handlers): the listener object itself</li>
 * </ul>
 * Calls are split into chunks of {@value #CHUNK} private methods so each stays under C2's inlining size limits.
 * Order, priority, phases, cancellation and receiveCanceled semantics are unchanged.
 */
public final class CompositeDispatcher {
    public interface Dispatch {
        void dispatch(Event event);
    }

    static final int CHUNK = 16;

    private record Slot(IEventListener target, boolean skipIfCanceled) {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final String LISTENER = "net/minecraftforge/eventbus/api/IEventListener";
    private static final String EVENT = "net/minecraftforge/eventbus/api/Event";
    private static final String EVENT_DESC = "(L" + EVENT + ";)V";

    private static final Field H_HANDLER, H_INFO, H_FILTER;

    static {
        try {
            H_HANDLER = ASMEventHandler.class.getDeclaredField("handler");   // the eventbus module is open
            H_INFO = ASMEventHandler.class.getDeclaredField("subInfo");
            H_FILTER = ASMEventHandler.class.getDeclaredField("filter");
            H_HANDLER.setAccessible(true);
            H_INFO.setAccessible(true);
            H_FILTER.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private CompositeDispatcher() {}

    /** null: not called at all (a phase marker without phase tracking) */
    private static Slot flatten(IEventListener l, boolean cancelable, boolean trackPhases) throws IllegalAccessException {
        if (l.getClass() == EventPriority.class) return trackPhases ? new Slot(l, false) : null;
        if (l.getClass() == ASMEventHandler.class && H_FILTER.get(l) == null) {
            IEventListener handler = (IEventListener) H_HANDLER.get(l);
            if (handler == null) return null;   // ASMEventHandler.invoke does nothing then
            boolean receiveCanceled = ((SubscribeEvent) H_INFO.get(l)).receiveCanceled();
            return new Slot(handler, cancelable && !receiveCanceled);
        }
        return new Slot(l, false);
    }

    /** @param cancelable the event class' isCancelable() (one class per listener array) */
    public static Dispatch create(IEventListener[] listeners, boolean cancelable, boolean trackPhases) {
        List<Slot> slots = new ArrayList<>(listeners.length);
        try {
            for (IEventListener l : listeners) {
                Slot s = flatten(l, cancelable, trackPhases);
                if (s != null) slots.add(s);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        String name = "dev/lull/bus/Composite$" + IDS.incrementAndGet();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";   // frames only merge `this` and the Event argument
            }
        };
        cw.visit(V17, ACC_PUBLIC | ACC_FINAL, name, null, "java/lang/Object",
                new String[] {"dev/lull/bus/CompositeDispatcher$Dispatch"});
        for (int i = 0; i < slots.size(); i++) {
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "f" + i, "L" + LISTENER + ";", null, null).visitEnd();
        }

        // constructor(Object[] targets)
        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "([Ljava/lang/Object;)V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        for (int i = 0; i < slots.size(); i++) {
            init.visitVarInsn(ALOAD, 0);
            init.visitVarInsn(ALOAD, 1);
            init.visitLdcInsn(i);
            init.visitInsn(AALOAD);
            init.visitTypeInsn(CHECKCAST, LISTENER);
            init.visitFieldInsn(PUTFIELD, name, "f" + i, "L" + LISTENER + ";");
        }
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        // chunk methods
        int chunks = (slots.size() + CHUNK - 1) / CHUNK;
        for (int c = 0; c < chunks; c++) {
            MethodVisitor mv = cw.visitMethod(ACC_PRIVATE | ACC_FINAL, "c" + c, EVENT_DESC, null, null);
            mv.visitCode();
            for (int i = c * CHUNK; i < Math.min(slots.size(), (c + 1) * CHUNK); i++) {
                Slot s = slots.get(i);
                Label skip = new Label();
                if (s.skipIfCanceled()) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, EVENT, "isCanceled", "()Z", false);
                    mv.visitJumpInsn(IFNE, skip);
                }
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, name, "f" + i, "L" + LISTENER + ";");
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, LISTENER, "invoke", EVENT_DESC, true);
                if (s.skipIfCanceled()) {
                    mv.visitLabel(skip);
                }
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // dispatch(Event) -> c0(e); c1(e); ...
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "dispatch", EVENT_DESC, null, null);
        mv.visitCode();
        for (int c = 0; c < chunks; c++) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, name, "c" + c, EVENT_DESC, false);
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();

        try {
            MethodHandles.Lookup hidden = LOOKUP.defineHiddenClass(cw.toByteArray(), true);
            Object[] targets = slots.stream().map(Slot::target).toArray();
            return (Dispatch) hidden.lookupClass().getConstructor(Object[].class).newInstance((Object) targets);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot define composite dispatcher", e);
        }
    }
}
