package dev.sleep.optsupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

/**
 * Measurement only (-Dprobe.brainTime=true, BrainTime*Mixin): direct timings inside Brain.tick - each behaviour's
 * tryStart / tickOrStop and each sensor's tick, as self time (a gate's children are subtracted from the gate), per
 * label: a Behavior subclass by its name, a BehaviorBuilder one-shot by the class its lambdas come from. Printed with
 * the brain total every 600 game ticks, then reset. Server thread only.
 */
public final class BrainTime {
    public static final boolean ON = Boolean.getBoolean("probe.brainTime");
    private static final Pattern LAMBDA = Pattern.compile("([A-Za-z0-9_]+)(?:\\$[A-Za-z0-9_]+)*\\$\\$Lambda");
    private static final IdentityHashMap<Object, String> LABELS = new IdentityHashMap<>();
    private static final Map<String, long[]> TIMES = new HashMap<>();   // label -> {self ns, calls}
    private static final long[] T0 = new long[64], CHILD = new long[64];
    private static final String[] LABEL = new String[64];
    private static int depth;
    private static Thread thread;
    private static long brainNs, brainCalls, lastPrint = -1;

    private BrainTime() {}

    public static void enter(Object what, String kind) {
        if (thread == null) thread = Thread.currentThread();
        if (Thread.currentThread() != thread || depth >= 64) { depth++; return; }
        LABEL[depth] = kind + label(what);
        CHILD[depth] = 0;
        T0[depth++] = System.nanoTime();
    }

    public static void exit() {
        if (Thread.currentThread() != thread) return;
        if (--depth >= 64 || depth < 0) { if (depth < 0) depth = 0; return; }
        long dt = System.nanoTime() - T0[depth];
        long[] t = TIMES.computeIfAbsent(LABEL[depth], k -> new long[2]);
        t[0] += dt - CHILD[depth];
        t[1]++;
        if (depth > 0) CHILD[depth - 1] += dt;
    }

    public static void brain(long ns, long gameTime) {
        brainNs += ns;
        brainCalls++;
        if (gameTime % 600 == 0 && gameTime != lastPrint) {
            lastPrint = gameTime;
            print();
        }
    }

    private static String label(Object o) {
        String l = LABELS.get(o);
        if (l != null) return l;
        if (o instanceof Behavior<?> || !(o instanceof BehaviorControl<?> b)) l = o.getClass().getSimpleName();
        else {
            String s = b.debugString();
            Matcher m = LAMBDA.matcher(s);
            l = m.find() ? m.group(1) : s.length() > 50 ? s.substring(0, 50) : s;
        }
        LABELS.put(o, l);
        return l;
    }

    private static void print() {
        List<Map.Entry<String, long[]>> l = new ArrayList<>(TIMES.entrySet());
        l.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        long sum = 0;
        for (var e : l) sum += e.getValue()[0];
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "[probe/braintime] brain ticks %d, %.1f us each; timed parts %.1f us each (self time, top 25):",
                brainCalls, brainCalls == 0 ? 0 : brainNs / 1e3 / brainCalls, brainCalls == 0 ? 0 : sum / 1e3 / brainCalls));
        for (int i = 0; i < Math.min(25, l.size()); i++) {
            long[] t = l.get(i).getValue();
            sb.append(String.format(Locale.ROOT, "%n   %6.2f us/brain tick  %5.1f %%  %7d calls  %s", t[0] / 1e3 / Math.max(1, brainCalls),
                    100.0 * t[0] / Math.max(1, brainNs), t[1], l.get(i).getKey()));
        }
        System.out.println(sb);
        TIMES.clear();
        brainNs = brainCalls = 0;
    }
}
