package dev.lull.optsupport;

import net.minecraft.world.level.Level;

/**
 * The single thread that owns a level's logic: the server thread for server levels, the client main (render) thread
 * for client levels. Per-entity and per-section memos are safe on that thread (one writer, one reader). Caches of data
 * shared by both sides (tags, fluid shapes, registries) must stay server-only in singleplayer.
 */
public final class OwnerThread {
    /** set by the client every frame (ClientMeter, RenderTickEvent START; 1.21.1: RenderFrameEvent.Pre); null on a dedicated server */
    public static volatile Thread client;
    /** -Dlab.opt.noClientLogic=true: memos stay server-only (A/B of the client side) */
    public static final boolean CLIENT_LOGIC = !Boolean.getBoolean("lab.opt.noClientLogic");

    private OwnerThread() {}

    public static boolean owns(Level level) {
        Thread t = Thread.currentThread();
        if (level.isClientSide()) return CLIENT_LOGIC && t == client;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && t == server.getRunningThread();
    }
}
