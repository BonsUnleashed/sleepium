package dev.sleep.net;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The bulk transfer's wire messages (see {@link Bulk}). */
public final class BulkPayloads {
    private BulkPayloads() {}

    /** one entry of a content set: its name, the SHA-256 of its bytes, the sizes, how many chunks its packed form has */
    public record Entry(String name, byte[] hash, int rawSize, int packedSize, int chunks) {}

    /** server → client: the set's current version */
    public record Manifest(ResourceLocation set, int version, int chunkSize, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Manifest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("sleep", "bulk_manifest"));
        public static final StreamCodec<ByteBuf, Manifest> CODEC = StreamCodec.of((b, m) -> {
            ResourceLocation.STREAM_CODEC.encode(b, m.set);
            ByteBufCodecs.VAR_INT.encode(b, m.version);
            ByteBufCodecs.VAR_INT.encode(b, m.chunkSize);
            ByteBufCodecs.VAR_INT.encode(b, m.entries.size());
            for (Entry e : m.entries) {
                ByteBufCodecs.STRING_UTF8.encode(b, e.name());
                b.writeBytes(e.hash());
                ByteBufCodecs.VAR_INT.encode(b, e.rawSize());
                ByteBufCodecs.VAR_INT.encode(b, e.packedSize());
                ByteBufCodecs.VAR_INT.encode(b, e.chunks());
            }
        }, b -> {
            ResourceLocation set = ResourceLocation.STREAM_CODEC.decode(b);
            int version = ByteBufCodecs.VAR_INT.decode(b), chunkSize = ByteBufCodecs.VAR_INT.decode(b), n = ByteBufCodecs.VAR_INT.decode(b);
            List<Entry> es = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String name = ByteBufCodecs.STRING_UTF8.decode(b);
                byte[] h = new byte[32];
                b.readBytes(h);
                es.add(new Entry(name, h, ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b)));
            }
            return new Manifest(set, version, chunkSize, es);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** client → server: the entries of that version it lacks, each from the chunk it has to (a resume) */
    public record Want(ResourceLocation set, int version, int cached, List<byte[]> hashes, List<Integer> from) implements CustomPacketPayload {
        public static final Type<Want> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("sleep", "bulk_want"));
        public static final StreamCodec<ByteBuf, Want> CODEC = StreamCodec.of((b, w) -> {
            ResourceLocation.STREAM_CODEC.encode(b, w.set);
            ByteBufCodecs.VAR_INT.encode(b, w.version);
            ByteBufCodecs.VAR_INT.encode(b, w.cached);
            ByteBufCodecs.VAR_INT.encode(b, w.hashes.size());
            for (int i = 0; i < w.hashes.size(); i++) {
                b.writeBytes(w.hashes.get(i));
                ByteBufCodecs.VAR_INT.encode(b, w.from.get(i));
            }
        }, b -> {
            ResourceLocation set = ResourceLocation.STREAM_CODEC.decode(b);
            int version = ByteBufCodecs.VAR_INT.decode(b), cached = ByteBufCodecs.VAR_INT.decode(b), n = ByteBufCodecs.VAR_INT.decode(b);
            if (n > 65536) throw new IllegalArgumentException("bulk want: " + n + " entries");
            List<byte[]> hs = new ArrayList<>(n);
            List<Integer> fr = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                byte[] h = new byte[32];
                b.readBytes(h);
                hs.add(h);
                fr.add(ByteBufCodecs.VAR_INT.decode(b));
            }
            return new Want(set, version, cached, hs, fr);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** server → client: one chunk of an entry's packed bytes */
    public record Chunk(byte[] hash, int index, int count, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("sleep", "bulk_chunk"));
        public static final StreamCodec<ByteBuf, Chunk> CODEC = StreamCodec.of((b, c) -> {
            b.writeBytes(c.hash);
            ByteBufCodecs.VAR_INT.encode(b, c.index);
            ByteBufCodecs.VAR_INT.encode(b, c.count);
            ByteBufCodecs.VAR_INT.encode(b, c.data.length);
            b.writeBytes(c.data);
        }, b -> {
            byte[] h = new byte[32];
            b.readBytes(h);
            int index = ByteBufCodecs.VAR_INT.decode(b), count = ByteBufCodecs.VAR_INT.decode(b), len = ByteBufCodecs.VAR_INT.decode(b);
            if (len > (1 << 20)) throw new IllegalArgumentException("bulk chunk: " + len + " bytes");
            byte[] d = new byte[len];
            b.readBytes(d);
            return new Chunk(h, index, count, d);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("probe_net").versioned("1").optional();
        // the client's side runs on the network thread and hands everything to the bulk's own serial executor
        PayloadRegistrar onNet = r.executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK);
        onNet.playToClient(Manifest.TYPE, Manifest.CODEC, (m, ctx) -> Bulk.clientManifest(m, ctx::reply));   // the answer on the same connection
        onNet.playToClient(Chunk.TYPE, Chunk.CODEC, (c, ctx) -> Bulk.clientChunk(c));
        onNet.playToClient(Coalesce.Hello.TYPE, Coalesce.Hello.CODEC, (h, ctx) -> {});   // a capability marker, never sent
        r.playToServer(Want.TYPE, Want.CODEC, (w, ctx) -> Bulk.onWant((ServerPlayer) ctx.player(), w));   // server thread
    }
}
