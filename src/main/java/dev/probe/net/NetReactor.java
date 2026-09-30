package dev.probe.net;

import java.util.ArrayList;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;

/**
 * Network reactor, stage A: outbound batching. Vanilla sends every packet from the server thread as its own netty task
 * with its own flush — a task (a lambda, a queue node, often a selector wake-up) and a socket write per packet. Here a
 * server-thread send in the PLAY protocol is appended to its connection's batch instead, and the batch goes out as ONE
 * netty task that writes every packet and flushes once, at the reactor's flush points:
 * <ul>
 *   <li>the end of the server tick and the connection's own tick,</li>
 *   <li>before the server thread sleeps (waitForTasks: idle phase or a blocking wait) — the answers to the packets it
 *       just handled leave before it waits for more,</li>
 *   <li>a batch of {@link #MAX_BATCH} packets (bounds memory and latency in bursts),</li>
 *   <li>before a protocol switch, a disconnect or an explicit flush.</li>
 * </ul>
 * Same packets, same order per connection, same bytes; only the moment of the socket write moves, to the end of the
 * server thread's current burst of work. A send from another thread goes into the batch too while one is pending (so
 * it cannot overtake it), and straight to netty as in vanilla otherwise.
 */
public final class NetReactor {
    public static final boolean ENABLED = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.NetReactor");
    public static final int MAX_BATCH = Integer.getInteger("lab.net.maxBatch", 256);
    public static final LongAdder BATCHED = new LongAdder(), OTHER_THREAD = new LongAdder(), DRAINS = new LongAdder(), EARLY = new LongAdder();

    /** a batched packet with a send listener (rare: most sends have none) */
    public record WithListener(Packet<?> packet, PacketSendListener listener) {}

    /** a connection that batches (the Connection mixin) */
    public interface Batched {
        /** schedule the pending batch, if any (any thread) */
        void probe$flush();
    }

    private static volatile Thread serverThread;
    /** connections with a pending batch not yet scheduled (server thread only) */
    private static final ArrayList<Batched> DIRTY = new ArrayList<>();

    private NetReactor() {}

    public static boolean onServerThread() {
        return Thread.currentThread() == serverThread;
    }

    /** server thread: its connection got a first packet since the last flush */
    public static void markDirty(Batched c) {
        DIRTY.add(c);
    }

    /** server thread, a flush point: every pending batch goes to netty */
    public static void flushAll() {
        for (int i = 0; i < DIRTY.size(); i++) DIRTY.get(i).probe$flush();
        DIRTY.clear();
    }

    /** the end of a server tick (MinecraftServer.tickServer) */
    public static void onServerTick() {
        serverThread = Thread.currentThread();
        flushAll();
    }

    /** before the server thread sleeps */
    public static void beforeSleep() {
        if (ENABLED && Thread.currentThread() == serverThread) flushAll();
    }

    public static String report() {
        long b = BATCHED.sumThenReset(), d = DRAINS.sumThenReset();
        return String.format(java.util.Locale.ROOT, "net reactor: %d packets batched into %d netty tasks (%.1f per task, %d early), %d from other threads",
                b, d, d == 0 ? 0 : (double) b / d, EARLY.sumThenReset(), OTHER_THREAD.sumThenReset());
    }
}
