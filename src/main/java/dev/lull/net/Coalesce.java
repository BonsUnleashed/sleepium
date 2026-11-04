package dev.lull.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Network engine stage C: every frame of a flush — vanilla, NeoForge and every mod's packets alike — goes to the client
 * as one container compressed by a deflate stream that lives as long as the connection (SYNC_FLUSH per container, so
 * each container decodes at once and the dictionary carries over between ticks: a movement packet like the one sent a
 * tick ago costs a few bits). Large frames that vanilla compression already packed (≥ {@link #RAW_LIMIT}) go in a
 * stored container, untouched.
 * <p>
 * Wire: from the switch on, the server's byte stream (between the frame prepender and encryption) is
 * {@code [varint length][flag][body]} with flag 1 = deflated frames, 0 = stored frames; the client's decoder (before the
 * frame splitter, installed on every non-memory client connection) turns it back into the plain frames the splitter
 * expects. The switch point is a marker frame no real packet can be (its content starts with a 6-byte VarInt, which
 * vanilla rejects), written on the event loop right after the server's handler is added: both ends change at one byte
 * of the stream. The server switches only for a client that registered {@link BulkPayloads#HELLO} (a probe client, whose
 * decoder is always there); in-memory connections (singleplayer) carry objects, not bytes, and are left alone.
 * <p>
 * 1.20.1: the capability is Forge's optional channel {@code sleepium:coalesce_z1} (1.21.1: a NeoForge payload type that
 * is never sent). The pipeline names are vanilla's on both versions ("prepender", "splitter", "encrypt" / "decrypt").
 */
public final class Coalesce {
    public static final boolean ENABLED = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.NetCoalesce");
    public static final int LEVEL = Integer.getInteger("lab.net.coalesceLevel", 6);
    public static final int RAW_LIMIT = Integer.getInteger("lab.net.coalesceRawLimit", 8192);
    /** a container is closed early past this many bytes (bounds memory and the decoder's output) */
    public static final int MAX_PENDING = 1 << 20;
    static final byte[] MAGIC = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 'p', 'r', 'o', 'b', 'e', '-', 'z', '1'};
    static final String SERVER_HANDLER = "probe_coalesce", CLIENT_HANDLER = "probe_uncoalesce";

    /** frames in, bytes out (after the container), containers, stored containers, switched connections */
    public static final LongAdder FRAME_BYTES = new LongAdder(), WIRE_BYTES = new LongAdder(), CONTAINERS = new LongAdder(), STORED = new LongAdder(),
            SWITCHED = new LongAdder();

    private Coalesce() {}

    public static boolean capable(ServerPlayer p) {
        return ENABLED && p.connection != null && !p.connection.connection.isMemoryConnection() && BulkPayloads.HELLO.isRemotePresent(p.connection.connection);
    }

    /** server thread, a player in PLAY: switch the connection's outbound stream to containers */
    public static void enable(ServerPlayer p) {
        if (!capable(p)) return;
        Channel ch = p.connection.connection.channel();
        ch.eventLoop().execute(() -> {
            var pipe = ch.pipeline();
            if (pipe.get(SERVER_HANDLER) != null || pipe.get("prepender") == null) return;
            CoalesceEncoder enc = new CoalesceEncoder();
            pipe.addBefore("prepender", SERVER_HANDLER, enc);
            ByteBuf m = ch.alloc().buffer(MAGIC.length + 1);
            writeVarInt(m, MAGIC.length);
            m.writeBytes(MAGIC);
            pipe.context(enc).write(m);   // toward the socket, past our own handler: every frame before it stays plain
            enc.on = true;
            SWITCHED.increment();
        });
    }

    static void writeVarInt(ByteBuf b, int v) {
        while ((v & ~0x7F) != 0) {
            b.writeByte(v & 0x7F | 0x80);
            v >>>= 7;
        }
        b.writeByte(v);
    }

    /** a VarInt at the reader index without moving it: value, or -1 if not complete yet; byte count in len[0] */
    static int peekVarInt(ByteBuf b, int[] len) {
        int v = 0, i = b.readerIndex(), end = b.writerIndex();
        for (int n = 0; n < 5; n++) {
            if (i + n >= end) return -1;
            byte x = b.getByte(i + n);
            v |= (x & 0x7F) << 7 * n;
            if ((x & 0x80) == 0) {
                len[0] = n + 1;
                return v;
            }
        }
        throw new io.netty.handler.codec.CorruptedFrameException("sleep coalesce: VarInt too long");
    }

    public static String report() {
        long f = FRAME_BYTES.sumThenReset(), w = WIRE_BYTES.sumThenReset();
        return String.format(java.util.Locale.ROOT, "coalesce: %d connections switched, %d containers (%d stored), frames %d KB -> wire %d KB (%.0f %%)",
                SWITCHED.sum(), CONTAINERS.sumThenReset(), STORED.sumThenReset(), f / 1024, w / 1024, f == 0 ? 100.0 : 100.0 * w / f);
    }
}
