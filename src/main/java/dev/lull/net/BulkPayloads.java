package dev.lull.net;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.event.EventNetworkChannel;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The bulk transfer's wire messages (see {@link Bulk}) and the coalescing capability marker, on Forge's channels.
 * 1.21.1 registers payload types on NeoForge's optional registrar "probe_net" v1; Forge 1.20.1 has channels instead:
 * {@code sleepium:bulk} (a SimpleChannel: one discriminator byte, then the message) and {@code sleepium:coalesce_z1}
 * (no messages: its presence in the connection's channel list is the capability). Both accept a missing remote side
 * (NetworkRegistry.acceptMissingOr), so vanilla clients, servers without Sleepium and Sleepium without the flags all
 * still connect; a remote side without a channel is simply not served by it.
 */
public final class BulkPayloads {
    private BulkPayloads() {}

    static final String VERSION = "1";
    /**
     * -Dsleep.net.testAbsent=true (a test switch, tools/mp_test.py): the channels under other names, so this side looks
     * to the other like one without Sleepium's channels (an older or channel-less build) and the other looks so to it
     */
    private static final String SUFFIX = Boolean.getBoolean("sleep.net.testAbsent") ? "_absent" : "";
    static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath(dev.lull.SleepMod.MOD_ID, "bulk" + SUFFIX);

    /** the bulk transfer's messages (created at mod construction: Forge refuses channels after its network lock) */
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(NAME)
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .simpleChannel();

    /** the coalescing capability marker: registered by every Sleepium client (its decoder is always installed); never sent on */
    public static final EventNetworkChannel HELLO = NetworkRegistry.ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(dev.lull.SleepMod.MOD_ID, "coalesce_z1" + SUFFIX))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(VERSION))
            .eventNetworkChannel();

    private static final int MANIFEST = 0, WANT = 1, CHUNK = 2;

    /** one entry of a content set: its name, the SHA-256 of its bytes, the sizes, how many chunks its packed form has */
    public record Entry(String name, byte[] hash, int rawSize, int packedSize, int chunks) {}

    /** server → client: the set's current version */
    public record Manifest(ResourceLocation set, int version, int chunkSize, List<Entry> entries) {
        void encode(FriendlyByteBuf b) {
            b.writeResourceLocation(set);
            b.writeVarInt(version);
            b.writeVarInt(chunkSize);
            b.writeVarInt(entries.size());
            for (Entry e : entries) {
                b.writeUtf(e.name());
                b.writeBytes(e.hash());
                b.writeVarInt(e.rawSize());
                b.writeVarInt(e.packedSize());
                b.writeVarInt(e.chunks());
            }
        }

        static Manifest decode(FriendlyByteBuf b) {
            ResourceLocation set = b.readResourceLocation();
            int version = b.readVarInt(), chunkSize = b.readVarInt(), n = b.readVarInt();
            List<Entry> es = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String name = b.readUtf();
                byte[] h = new byte[32];
                b.readBytes(h);
                es.add(new Entry(name, h, b.readVarInt(), b.readVarInt(), b.readVarInt()));
            }
            return new Manifest(set, version, chunkSize, es);
        }
    }

    /** client → server: the entries of that version it lacks, each from the chunk it has to (a resume) */
    public record Want(ResourceLocation set, int version, int cached, List<byte[]> hashes, List<Integer> from) {
        void encode(FriendlyByteBuf b) {
            b.writeResourceLocation(set);
            b.writeVarInt(version);
            b.writeVarInt(cached);
            b.writeVarInt(hashes.size());
            for (int i = 0; i < hashes.size(); i++) {
                b.writeBytes(hashes.get(i));
                b.writeVarInt(from.get(i));
            }
        }

        static Want decode(FriendlyByteBuf b) {
            ResourceLocation set = b.readResourceLocation();
            int version = b.readVarInt(), cached = b.readVarInt(), n = b.readVarInt();
            if (n > 65536) throw new IllegalArgumentException("bulk want: " + n + " entries");
            List<byte[]> hs = new ArrayList<>(n);
            List<Integer> fr = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                byte[] h = new byte[32];
                b.readBytes(h);
                hs.add(h);
                fr.add(b.readVarInt());
            }
            return new Want(set, version, cached, hs, fr);
        }
    }

    /** server → client: one chunk of an entry's packed bytes */
    public record Chunk(byte[] hash, int index, int count, byte[] data) {
        void encode(FriendlyByteBuf b) {
            b.writeBytes(hash);
            b.writeVarInt(index);
            b.writeVarInt(count);
            b.writeVarInt(data.length);
            b.writeBytes(data);
        }

        static Chunk decode(FriendlyByteBuf b) {
            byte[] h = new byte[32];
            b.readBytes(h);
            int index = b.readVarInt(), count = b.readVarInt(), len = b.readVarInt();
            if (len > (1 << 20)) throw new IllegalArgumentException("bulk chunk: " + len + " bytes");
            byte[] d = new byte[len];
            b.readBytes(d);
            return new Chunk(h, index, count, d);
        }
    }

    /** mod construction, both sides: the messages (the channels themselves are made by this class's initialisation) */
    public static void register() {
        // the client's side runs on the network thread and hands everything to the bulk's own serial executor
        CHANNEL.messageBuilder(Manifest.class, MANIFEST, NetworkDirection.PLAY_TO_CLIENT).encoder(Manifest::encode).decoder(Manifest::decode)
                .consumerNetworkThread((m, ctx) -> {
                    var connection = ctx.get().getNetworkManager();   // the answer on the same connection (the player may not exist yet)
                    Bulk.clientManifest(m, w -> CHANNEL.sendTo(w, connection, NetworkDirection.PLAY_TO_SERVER));
                    ctx.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(Chunk.class, CHUNK, NetworkDirection.PLAY_TO_CLIENT).encoder(Chunk::encode).decoder(Chunk::decode)
                .consumerNetworkThread((c, ctx) -> {
                    Bulk.clientChunk(c);
                    ctx.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(Want.class, WANT, NetworkDirection.PLAY_TO_SERVER).encoder(Want::encode).decoder(Want::decode)
                .consumerMainThread((w, ctx) -> {   // server thread
                    ServerPlayer p = ctx.get().getSender();
                    if (p != null) Bulk.onWant(p, w);
                }).add();
    }

    /** server thread: a message to one player */
    static void send(ServerPlayer p, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), message);
    }

    /**
     * server thread: a chunk to one player. SimpleChannel encodes on the calling thread into a buffer that starts at
     * 256 bytes and doubles (a 256 KB chunk would be copied ~10 times); this buffer is sized once for the chunk.
     */
    static void sendChunk(ServerPlayer p, Chunk c) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer(c.data().length + 64));
        int index = CHANNEL.encodeMessage(c, b);
        p.connection.send(NetworkDirection.PLAY_TO_CLIENT.buildPacket(org.apache.commons.lang3.tuple.Pair.of(b, index), NAME).getThis());
    }
}
