package dev.sleep.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Client side of {@link Coalesce}, before the frame splitter. Until the server's marker frame it passes every frame
 * through as it came (whole frames: it reads their lengths, nothing else); after the marker the stream is containers,
 * and it hands the splitter the plain frames inside them. One inflater lives as long as the connection.
 */
public final class CoalesceDecoder extends ByteToMessageDecoder {
    private boolean on;
    private final Inflater inflater = new Inflater();
    private final byte[] scratch = new byte[64 * 1024];
    private final int[] len = new int[1];
    /** the most a container may inflate to (a broken or hostile stream cannot make the client allocate more) */
    private static final int MAX_OUT = 8 << 20;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (in.isReadable()) {
            int n = Coalesce.peekVarInt(in, len);
            if (n < 0 || in.readableBytes() < len[0] + n) return;   // wait for the rest
            int start = in.readerIndex(), body = start + len[0];
            if (!on) {
                if (n == Coalesce.MAGIC.length && isMagic(in, body)) {
                    in.skipBytes(len[0] + n);
                    on = true;
                    continue;
                }
                out.add(in.readRetainedSlice(len[0] + n));   // a plain frame, its length prefix included
                continue;
            }
            if (n < 1) throw new CorruptedFrameException("sleep coalesce: empty container");
            in.skipBytes(len[0]);
            byte flag = in.readByte();
            if (flag == 0) out.add(in.readRetainedSlice(n - 1));
            else if (flag == 1) out.add(inflate(ctx, in, n - 1));
            else throw new CorruptedFrameException("sleep coalesce: container flag " + flag);
        }
    }

    private boolean isMagic(ByteBuf in, int at) {
        for (int i = 0; i < Coalesce.MAGIC.length; i++) if (in.getByte(at + i) != Coalesce.MAGIC[i]) return false;
        return true;
    }

    private ByteBuf inflate(ChannelHandlerContext ctx, ByteBuf in, int n) throws DataFormatException {
        inflater.setInput(in.nioBuffer(in.readerIndex(), n));
        in.skipBytes(n);
        ByteBuf outBuf = ctx.alloc().buffer(Math.max(256, n * 4));
        while (true) {
            int k = inflater.inflate(scratch);
            if (k > 0) {
                outBuf.writeBytes(scratch, 0, k);
                if (outBuf.readableBytes() > MAX_OUT) {
                    outBuf.release();
                    throw new CorruptedFrameException("sleep coalesce: container over " + MAX_OUT + " bytes");
                }
            }
            if (k < scratch.length && inflater.needsInput()) return outBuf;
            if (k == 0 && (inflater.needsDictionary() || inflater.finished())) {
                outBuf.release();
                throw new CorruptedFrameException("sleep coalesce: bad deflate stream");
            }
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        inflater.end();
    }
}
