package dev.lull.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;

/**
 * Test latency on a real connection (both sides of it, the integrated server's in-memory one too): every packet read in
 * the PLAY protocol reaches the connection {@code lab.net.lagMs} later (in order), so the round trip is twice that - the
 * players' inputs, their positions and the world's updates all late together, as on a network. Before PLAY (handshake,
 * login; 1.21.1 also configuration) nothing is held. {@code -Dlab.opt.NetLagMixin=true -Dlab.net.lagMs=150}.
 * 1.20.1's packet listeners do not name their protocol: the channel's protocol attribute (Connection.setProtocol) does.
 */
public final class NetLag extends ChannelInboundHandlerAdapter {
    public static final int MS = Integer.getInteger("lab.net.lagMs", 0);

    /** reads held (the event loop's only): while any is, later ones queue behind it whatever the protocol */
    private int held;

    private NetLag() {}

    /** Connection.channelActive: before the connection's own handler (1.21.1 read the protocol from the connection) */
    public static void install(Channel channel, Connection connection) {
        var p = channel.pipeline();
        if (MS <= 0 || p.get("packet_handler") == null || p.get("probe_lag") != null) return;
        p.addBefore("packet_handler", "probe_lag", new NetLag());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (held == 0 && ctx.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get() != ConnectionProtocol.PLAY) {
            ctx.fireChannelRead(msg);
            return;
        }
        held++;
        ctx.executor().schedule(() -> {
            held--;
            ctx.fireChannelRead(msg);
        }, MS, TimeUnit.MILLISECONDS);
    }
}
