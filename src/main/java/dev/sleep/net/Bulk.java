package dev.sleep.net;

import dev.sleep.api.Pools;
import dev.sleep.net.BulkPayloads.Chunk;
import dev.sleep.net.BulkPayloads.Entry;
import dev.sleep.net.BulkPayloads.Manifest;
import dev.sleep.net.BulkPayloads.Want;
import io.netty.channel.Channel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Content-addressed bulk transfer (network engine stage D). A content set is a map name → bytes that the server
 * publishes (on load, after a reload); each entry is known by the SHA-256 of its bytes.
 * <ul>
 *   <li>The server sends the set's manifest (names, hashes, sizes). The client looks each hash up in its disk cache and
 *       asks only for what it lacks, each entry from the chunk it already has (a transfer cut by a disconnect resumes).
 *       A reload with a few changed entries sends those few: the rest have the same hashes.</li>
 *   <li>Entries are deflated and cut into chunks of {@link #CHUNK} bytes once per publish, on the pool, and the same
 *       chunk arrays go to every player.</li>
 *   <li>Chunks leave at the server tick's end, and only while the connection's outbound buffer (the transport's own
 *       feedback: what the socket has not taken yet) holds less than {@link #WINDOW} bytes, so a slow link gets chunks
 *       as fast as it drains them and combat packets are never queued behind megabytes.</li>
 *   <li>The client checks every entry's hash after assembling it, and the whole set is handed to the content's consumer
 *       on its main thread at once.</li>
 * </ul>
 * A player without the channel (no probe on the client), or on an in-memory connection (singleplayer: no bytes to
 * save), is not served: {@link #sendTo} returns false and the content sends its own way.
 */
public final class Bulk {
    public static final boolean ENABLED = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.NetBulk");
    public static final int CHUNK = Integer.getInteger("lab.net.bulkChunk", 256 * 1024);
    public static final int WINDOW = Integer.getInteger("lab.net.bulkWindow", 512 * 1024);
    public static final int MAX_PER_TICK = Integer.getInteger("lab.net.bulkChunksPerTick", 4);
    public static final LongAdder CHUNKS = new LongAdder(), BYTES = new LongAdder(), WANTED = new LongAdder(), CACHED = new LongAdder(), HELD = new LongAdder();

    /** one published entry: its chunks are shared by every transfer */
    record Blob(String name, byte[] hash, String hex, int rawSize, int packedSize, byte[][] chunks) {}

    private static final class SetState {
        final ResourceLocation id;
        int version;
        Map<String, Blob> current = Map.of(), previous = Map.of();
        Manifest manifest;

        SetState(ResourceLocation id) {
            this.id = id;
        }

        synchronized Blob find(String hex) {
            Blob b = current.get(hex);
            return b != null ? b : previous.get(hex);
        }
    }

    /**
     * Per-connection pacing: what the socket took per tick (EWMA of pending-before + sent − pending-now), and a window
     * of about one tick of that — so combat packets never wait behind more than ~50 ms of bulk data on any link.
     */
    private static final class Pace {
        long lastPending, sentLastTick;
        double drainPerTick = WINDOW;

        long window(long pendingNow) {
            long drained = Math.max(0, lastPending + sentLastTick - pendingNow);
            if (sentLastTick > 0 || lastPending > 0) drainPerTick += (drained - drainPerTick) * 0.25;
            return Math.max(MIN_WINDOW, Math.min(WINDOW, (long) drainPerTick));
        }
    }

    public static final int MIN_WINDOW = Integer.getInteger("lab.net.bulkMinWindow", 16 * 1024);
    private static final Map<UUID, Pace> PACES = new HashMap<>();

    private static final class Send {
        final Blob blob;
        int next;

        Send(Blob blob, int from) {
            this.blob = blob;
            this.next = Math.max(0, Math.min(from, blob.chunks().length));
        }
    }

    private static final Map<ResourceLocation, SetState> SETS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Consumer<Map<String, byte[]>>> CONSUMERS = new ConcurrentHashMap<>();
    /** server thread only */
    private static final Map<UUID, ArrayDeque<Send>> QUEUES = new HashMap<>();
    private static final Map<UUID, List<ResourceLocation>> WAITING = new HashMap<>();

    private Bulk() {}

    /** both sides: what the client does with a complete set (runs on the client's main thread) */
    public static void register(ResourceLocation set, Consumer<Map<String, byte[]>> clientApply) {
        CONSUMERS.put(set, clientApply);
    }

    /** the client's consumer of a set (BulkClient) */
    public static Consumer<Map<String, byte[]>> consumerFor(ResourceLocation set) {
        return CONSUMERS.get(set);
    }

    /** whether this player is served by the bulk path */
    public static boolean capable(ServerPlayer p) {
        return ENABLED && p.connection != null && !p.connection.getConnection().isMemoryConnection() && p.connection.hasChannel(Manifest.TYPE);
    }

    /**
     * Any thread: the set's new content. Blobs are built on the pool; then every capable player gets the new manifest
     * (and asks only for the entries it does not have).
     */
    public static void publish(ResourceLocation set, Map<String, byte[]> entries) {
        if (!ENABLED) return;
        Map<String, byte[]> copy = new TreeMap<>(entries);
        Pools.background().execute(() -> {
            long t0 = System.nanoTime();
            Map<String, Blob> blobs = new HashMap<>();
            List<Entry> list = new ArrayList<>(copy.size());
            long raw = 0, packed = 0;
            for (Map.Entry<String, byte[]> e : copy.entrySet()) {
                Blob b = blob(e.getKey(), e.getValue());
                blobs.put(b.hex(), b);
                list.add(new Entry(b.name(), b.hash(), b.rawSize(), b.packedSize(), b.chunks().length));
                raw += b.rawSize();
                packed += b.packedSize();
            }
            SetState s = SETS.computeIfAbsent(set, SetState::new);
            Manifest m;
            synchronized (s) {
                s.version++;
                s.previous = s.current;
                s.current = blobs;
                s.manifest = m = new Manifest(set, s.version, CHUNK, list);
            }
            org.slf4j.LoggerFactory.getLogger("probe").info("[probe/net] bulk {} v{}: {} entries, {} KB raw, {} KB packed, built in {} ms",
                    set, m.version(), list.size(), raw / 1024, packed / 1024, (System.nanoTime() - t0) / 1_000_000);
            MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) server.execute(() -> {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    List<ResourceLocation> w = WAITING.get(p.getUUID());
                    if (capable(p)) PacketDistributor.sendToPlayer(p, m);
                    if (w != null) w.remove(set);
                }
            });
        });
    }

    /**
     * Server thread: the set to a joining player. False: not served by the bulk path (no probe client, singleplayer,
     * or the engine is off) — the caller sends its content its own way.
     */
    public static boolean sendTo(ServerPlayer p, ResourceLocation set) {
        if (!capable(p)) return false;
        SetState s = SETS.get(set);
        Manifest m;
        if (s == null) m = null;
        else synchronized (s) {
            m = s.manifest;
        }
        if (m == null) WAITING.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(set);   // sent when published
        else PacketDistributor.sendToPlayer(p, m);
        return true;
    }

    /** server thread: what a client lacks */
    static void onWant(ServerPlayer p, Want w) {
        SetState s = SETS.get(w.set());
        if (s == null) return;
        CACHED.add(w.cached());
        ArrayDeque<Send> q = QUEUES.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        HexFormat hf = HexFormat.of();
        for (int i = 0; i < w.hashes().size(); i++) {
            Blob b = s.find(hf.formatHex(w.hashes().get(i)));
            if (b == null) continue;   // an entry of a version older than the last two: the client gets the new manifest anyway
            q.add(new Send(b, w.from().get(i)));
            WANTED.increment();
        }
    }

    /** server thread, end of every tick: chunks while the transport takes them */
    public static void pump(MinecraftServer server) {
        if (QUEUES.isEmpty()) return;
        for (var it = QUEUES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || e.getValue().isEmpty()) {
                it.remove();
                PACES.remove(e.getKey());
                continue;
            }
            Channel ch = p.connection.getConnection().channel();
            long pendingNow = pending(ch), inFlight = pendingNow;
            Pace pace = PACES.computeIfAbsent(e.getKey(), k -> new Pace());
            long window = pace.window(pendingNow);
            long sent = 0;
            ArrayDeque<Send> q = e.getValue();
            for (int n = 0; n < MAX_PER_TICK && !q.isEmpty(); n++) {
                if (inFlight >= window) {
                    HELD.increment();
                    break;
                }
                Send s = q.peek();
                byte[] data = s.blob.chunks()[s.next];
                PacketDistributor.sendToPlayer(p, new Chunk(s.blob.hash(), s.next, s.blob.chunks().length, data));
                inFlight += data.length;   // queued in this tick's batch, not yet in the outbound buffer
                sent += data.length;
                CHUNKS.increment();
                BYTES.add(data.length);
                if (++s.next >= s.blob.chunks().length) q.poll();
            }
            pace.lastPending = pendingNow;
            pace.sentLastTick = sent;
        }
    }

    /** bytes the socket has not taken yet (the outbound buffer; a racy read is enough for pacing) */
    private static long pending(Channel ch) {
        var buf = ch.unsafe().outboundBuffer();
        return buf == null ? 0 : buf.totalPendingWriteBytes();
    }

    public static void onLogout(ServerPlayer p) {
        QUEUES.remove(p.getUUID());
        WAITING.remove(p.getUUID());
        PACES.remove(p.getUUID());
    }

    private static Blob blob(String name, byte[] raw) {
        byte[] hash;
        try {
            hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var out = new java.io.ByteArrayOutputStream(Math.max(64, raw.length / 4));
        java.util.zip.Deflater d = new java.util.zip.Deflater(6);
        try (var z = new java.util.zip.DeflaterOutputStream(out, d)) {
            z.write(raw);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } finally {
            d.end();
        }
        byte[] packed = out.toByteArray();
        int n = Math.max(1, (packed.length + CHUNK - 1) / CHUNK);
        byte[][] chunks = new byte[n][];
        for (int i = 0; i < n; i++) chunks[i] = java.util.Arrays.copyOfRange(packed, i * CHUNK, Math.min(packed.length, (i + 1) * CHUNK));
        return new Blob(name, hash, HexFormat.of().formatHex(hash), raw.length, packed.length, chunks);
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "bulk: %d chunks, %d KB sent, %d entries wanted, %d cached on clients, %d held by the window",
                CHUNKS.sumThenReset(), BYTES.sumThenReset() / 1024, WANTED.sumThenReset(), CACHED.sumThenReset(), HELD.sumThenReset());
    }

    // ------------------------------------------------------------------ client side (network thread → BulkClient)

    static void clientManifest(Manifest m, java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload> reply) {
        dev.sleep.net.client.BulkClient.onManifest(m, reply);
    }

    static void clientChunk(Chunk c) {
        dev.sleep.net.client.BulkClient.onChunk(c);
    }
}
