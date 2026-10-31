package dev.sleep.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.Deflater;

/**
 * Server side of {@link Coalesce}, between the frame prepender and encryption: frames written before a flush are
 * appended to one buffer and leave, at the flush, as one container deflated by the connection's own stream. Event loop
 * only; the pending buffer and the scratch array are reused for the connection's life.
 */
final class CoalesceEncoder extends ChannelOutboundHandlerAdapter {
    volatile boolean on;
    private final Deflater deflater = new Deflater(Coalesce.LEVEL);
    private ByteBuf pending;
    private final ArrayList<ChannelPromise> promises = new ArrayList<>();
    private final byte[] scratch = new byte[64 * 1024];

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!on || !(msg instanceof ByteBuf b)) {
            ctx.write(msg, promise);
            return;
        }
        int n = b.readableBytes();
        Coalesce.FRAME_BYTES.add(n);
        if (n >= Coalesce.RAW_LIMIT) {   // already packed by vanilla compression (or large): stored as it is, in order
            flushPending(ctx);
            ByteBuf head = ctx.alloc().buffer(6);
            Coalesce.writeVarInt(head, n + 1);
            head.writeByte(0);
            Coalesce.WIRE_BYTES.add(head.readableBytes() + n);
            Coalesce.CONTAINERS.increment();
            Coalesce.STORED.increment();
            ctx.write(head);
            ctx.write(b, promise);
            return;
        }
        if (pending == null) pending = ctx.alloc().directBuffer(16 * 1024);
        pending.writeBytes(b);
        b.release();
        promises.add(promise);
        if (pending.readableBytes() >= Coalesce.MAX_PENDING) flushPending(ctx);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        flushPending(ctx);
        ctx.flush();
    }

    private void flushPending(ChannelHandlerContext ctx) {
        if (pending == null || !pending.isReadable()) return;
        ByteBuf body = ctx.alloc().buffer(pending.readableBytes() / 2 + 64);
        ByteBuffer in = pending.nioBuffer();
        deflater.setInput(in);
        int k;
        do {   // SYNC_FLUSH: everything so far is decodable; call again while the scratch came back full
            k = deflater.deflate(scratch, 0, scratch.length, Deflater.SYNC_FLUSH);
            body.writeBytes(scratch, 0, k);
        } while (k == scratch.length || in.hasRemaining());
        pending.clear();
        ByteBuf head = ctx.alloc().buffer(6);
        Coalesce.writeVarInt(head, body.readableBytes() + 1);
        head.writeByte(1);
        Coalesce.WIRE_BYTES.add(head.readableBytes() + body.readableBytes());
        Coalesce.CONTAINERS.increment();
        ctx.write(head);
        if (promises.isEmpty()) {
            ctx.write(body);
            return;
        }
        ChannelPromise[] ps = promises.toArray(new ChannelPromise[0]);
        promises.clear();
        ctx.write(body).addListener(f -> {
            for (ChannelPromise p : ps) {
                if (f.isSuccess()) p.trySuccess();
                else p.tryFailure(f.cause());
            }
        });
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (pending != null) {
            pending.release();
            pending = null;
        }
        deflater.end();
    }
}
